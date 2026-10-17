package com.luvin.survey.service;

import com.luvin.common.exception.UserNotFoundException;
import com.luvin.survey.definition.SurveyDefinitionConfig;
import com.luvin.survey.definition.SurveyDefinitionConfigLoader;
import com.luvin.survey.definition.SurveyDefinitionProfile;
import com.luvin.survey.domain.SurveyDefinitionV2;
import com.luvin.survey.domain.SurveyResultV2;
import com.luvin.survey.domain.SurveySubmissionAnswerV2;
import com.luvin.survey.domain.SurveySubmissionV2;
import com.luvin.survey.dto.SurveyResultResponse;
import com.luvin.survey.dto.SurveyV2SubmitRequest;
import com.luvin.survey.repository.SurveyDefinitionV2Repository;
import com.luvin.survey.repository.SurveyResultV2Repository;
import com.luvin.survey.repository.SurveySubmissionAnswerV2Repository;
import com.luvin.survey.repository.SurveySubmissionV2Repository;
import com.luvin.survey.scoring.BreadTypeClassificationResult;
import com.luvin.survey.scoring.BreadTypeClassifier;
import com.luvin.survey.scoring.ReasonEvidence;
import com.luvin.survey.scoring.SurveyAnswerValidationException;
import com.luvin.survey.scoring.SurveyExplanationBuilder;
import com.luvin.survey.scoring.SurveyScorer;
import com.luvin.survey.scoring.SurveyScoringResult;
import com.luvin.survey.service.exception.SurveyIdempotencyConflictException;
import com.luvin.survey.service.exception.SurveyResultNotFoundException;
import com.luvin.survey.service.exception.SurveySubmissionNotFoundException;
import com.luvin.survey.service.exception.SurveyVersionRetiredException;
import com.luvin.user.domain.User;
import com.luvin.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * 설문 v2 제출/조회. member row lock으로 동시 제출을 직렬화하고, (memberId, clientSubmissionId) unique +
 * canonical payload hash로 재시도 멱등성을 보장한다 (BACKEND_CHANGES.md 7절 "멱등성과 경합").
 * 외부 AI 호출은 이 transaction에 절대 묶지 않는다.
 */
@Service
@RequiredArgsConstructor
public class SurveySubmissionServiceImpl implements SurveySubmissionService {

    private final UserRepository userRepository;
    private final SurveyDefinitionV2Repository surveyDefinitionV2Repository;
    private final SurveySubmissionV2Repository surveySubmissionV2Repository;
    private final SurveySubmissionAnswerV2Repository surveySubmissionAnswerV2Repository;
    private final SurveyResultV2Repository surveyResultV2Repository;

    @Override
    @Transactional
    public SurveySubmissionOutcome submit(Long memberId, SurveyV2SubmitRequest request) {
        User user = userRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new UserNotFoundException(memberId));

        SurveyDefinitionV2 definition = surveyDefinitionV2Repository.findById(request.definitionId())
                .orElseThrow(() -> new SurveyAnswerValidationException(
                        "SURVEY_REQUEST_INVALID", "알 수 없는 definitionId입니다."));
        if (!definition.getSurveyVersion().equals(request.surveyVersion())) {
            throw new SurveyAnswerValidationException(
                    "SURVEY_REQUEST_INVALID", "definitionId와 surveyVersion이 일치하지 않습니다.");
        }

        Map<String, String> answers = toAnswerMap(request);
        String requestHash = canonicalHash(request.definitionId(), answers);

        Optional<SurveySubmissionV2> existing = surveySubmissionV2Repository
                .findByMemberIdAndClientSubmissionId(memberId, request.clientSubmissionId());
        if (existing.isPresent()) {
            SurveySubmissionV2 previous = existing.get();
            if (!previous.getRequestHash().equals(requestHash)) {
                throw new SurveyIdempotencyConflictException();
            }
            SurveyResultV2 result = surveyResultV2Repository.findBySubmission_Id(previous.getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "submission은 있는데 result가 없습니다: " + previous.getId()));
            return new SurveySubmissionOutcome(toResultResponse(result, definition), false);
        }

        // 새 제출: retired definition에는 새로 들어올 수 없다(같은 key/hash replay는 위에서 이미 처리됨).
        if (definition.isRetired()) {
            throw new SurveyVersionRetiredException();
        }

        SurveyDefinitionConfig config = SurveyDefinitionConfigLoader.parse(definition.getConfigJson());
        SurveyScoringResult scoring = SurveyScorer.score(config, request.surveyVersion(), answers);
        BreadTypeClassificationResult classification = BreadTypeClassifier.classify(config, scoring);

        SurveySubmissionV2 submission = surveySubmissionV2Repository.save(new SurveySubmissionV2(
                UUID.randomUUID(), memberId, definition, request.clientSubmissionId(), requestHash));
        for (SurveyV2SubmitRequest.AnswerItem item : request.answers()) {
            surveySubmissionAnswerV2Repository.save(
                    new SurveySubmissionAnswerV2(submission, item.questionId(), item.answerId()));
        }

        SurveyResultV2 result = surveyResultV2Repository.save(new SurveyResultV2(
                UUID.randomUUID(),
                submission,
                SurveyResultJsonMapper.scoresToJson(scoring.scores(), config.dimensions(), true),
                SurveyResultJsonMapper.scoresToJson(scoring.scores(), config.dimensions(), false),
                SurveyResultJsonMapper.evidenceStatsToJson(scoring.observedByDimension()),
                SurveyResultJsonMapper.distancesToJson(classification.distances()),
                classification.primaryType(),
                classification.secondaryType(),
                classification.mixed(),
                classification.poorFit(),
                classification.tie(),
                classification.topTwoDistanceGap(),
                SurveyResultJsonMapper.reasonEvidenceToJson(classification.reasonEvidence())));

        // 재설문 경합 순서 보장: member row가 잠겨있는 이 transaction이 커밋될 때만 latest pointer가 바뀐다.
        user.applySurveyResult(result.getId(), classification.primaryType());

        return new SurveySubmissionOutcome(toResultResponse(result, definition), true);
    }

    @Override
    @Transactional(readOnly = true)
    public SurveyResultResponse getByClientSubmissionId(Long memberId, UUID clientSubmissionId) {
        SurveySubmissionV2 submission = surveySubmissionV2Repository
                .findByMemberIdAndClientSubmissionId(memberId, clientSubmissionId)
                .orElseThrow(SurveySubmissionNotFoundException::new);
        SurveyResultV2 result = surveyResultV2Repository.findBySubmission_Id(submission.getId())
                .orElseThrow(SurveySubmissionNotFoundException::new);
        return toResultResponse(result, submission.getDefinition());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SurveyResultResponse> getLatestResult(Long memberId) {
        User user = userRepository.findById(memberId)
                .orElseThrow(() -> new UserNotFoundException(memberId));
        if (!user.hasCompletedSurveyV2()) {
            return Optional.empty();
        }
        SurveyResultV2 result = surveyResultV2Repository
                .findByIdAndSubmission_MemberId(user.getLatestSurveyResultId(), memberId)
                .orElseThrow(SurveyResultNotFoundException::new);
        return Optional.of(toResultResponse(result, result.getSubmission().getDefinition()));
    }

    private Map<String, String> toAnswerMap(SurveyV2SubmitRequest request) {
        Map<String, String> answers = new LinkedHashMap<>();
        for (SurveyV2SubmitRequest.AnswerItem item : request.answers()) {
            if (answers.put(item.questionId(), item.answerId()) != null) {
                throw new SurveyAnswerValidationException(
                        "SURVEY_REQUEST_INVALID", "questionId가 중복되었습니다: " + item.questionId());
            }
        }
        return answers;
    }

    private String canonicalHash(UUID definitionId, Map<String, String> answers) {
        TreeMap<String, String> sorted = new TreeMap<>(answers);
        StringBuilder sb = new StringBuilder(definitionId.toString());
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            sb.append('|').append(entry.getKey()).append('=').append(entry.getValue());
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }

    private SurveyResultResponse toResultResponse(SurveyResultV2 result, SurveyDefinitionV2 definition) {
        SurveyDefinitionConfig config = SurveyDefinitionConfigLoader.parse(definition.getConfigJson());
        String primaryLabel = labelOf(config, result.getPrimaryType());
        List<ReasonEvidence> reasons = SurveyResultJsonMapper.parseReasonEvidence(result.getExplanationEvidenceJson());
        List<String> reasonTexts = SurveyExplanationBuilder.buildReasonTexts(reasons, config, primaryLabel);

        return new SurveyResultResponse(
                result.getId(),
                result.getSubmission().getClientSubmissionId(),
                definition.getId(),
                definition.getSurveyVersion(),
                definition.getScoringVersion(),
                definition.getClassificationVersion(),
                result.getPrimaryType(),
                primaryLabel,
                result.getSecondaryType(),
                result.isMixed(),
                result.isPoorFit(),
                result.isTie(),
                primaryLabel + " 반죽",
                "이 설문에서는 " + primaryLabel + "에 가장 가깝게 나왔어요.",
                reasonTexts,
                result.getCreatedAt());
    }

    private String labelOf(SurveyDefinitionConfig config, String profileId) {
        return config.profiles().stream()
                .filter(p -> p.id().equals(profileId))
                .map(SurveyDefinitionProfile::label)
                .findFirst()
                .orElse(profileId);
    }
}
