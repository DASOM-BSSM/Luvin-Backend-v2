package com.luvin.ai.service.story;

import com.luvin.ai.client.dto.TraitsDto;
import com.luvin.ai.client.story.SeasonDesignRequest;
import com.luvin.ai.client.story.SeasonDesignResponse;
import com.luvin.ai.client.story.StoryTypes;
import com.luvin.ai.domain.AiCastRole;
import com.luvin.ai.domain.AiSeasonSetting;
import com.luvin.ai.domain.AiStoryRoute;
import com.luvin.ai.dto.AiReportView;
import com.luvin.ai.dto.story.AiStageIntroView;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 새 시즌 설정(FastAPI season-design) 입력 조립·응답 검사·출연진 배정, 그리고 저장한 설정으로 만드는 회차 본문·시즌 리포트의
 * seasonContext. 2시즌부터 쓴다. 설정 결과는 초안이다 — 회차 블록 설계·선택 판정·호감은 지금처럼 Spring이 정한다.
 */
final class AiSeasonDesigns {

    /** 이전 시즌 기록은 최근 순으로 이만큼까지 보낸다. */
    static final int PREVIOUS_SEASONS_MAX = 3;
    static final int USER_FEEDBACKS_MAX = 5;
    static final int LIKED_LINES_MAX = 5;
    static final int CATALOG_MAX = 100;
    static final int REPORT_CHIPS_MAX = 20;
    static final int LINE_MAX = 1000;
    static final int FEEDBACK_MAX = 5000;
    static final int CAST_SIZE = 5;
    static final int CANDIDATES = 3;
    static final int EPISODES = 10;
    static final int ROLE_MAX = 12;
    static final int DESCRIPTION_MAX = 20;
    static final int SUBTITLE_MAX = 30;
    static final int SCORE_MAX = 100;

    private static final Pattern LINE_BREAKS = Pattern.compile("[\\r\\n\\u0085\\u2028\\u2029]+");
    private static final Pattern NAME = Pattern.compile("^[가-힣]{2,3}$");
    /** 선택 지점(①~④)이 있는 회차. */
    private static final Map<Integer, Integer> EPISODE_OF_POINT = Map.of(1, 3, 2, 5, 3, 6, 4, 8);

    private AiSeasonDesigns() {
    }

    // ---------------------------------------------------------------- 입력

    /**
     * 설정 요청을 만든다. 직전 시즌(seasonNumber - 1)부터 끝까지 진행한 시즌을 최근 순으로 최대 3개 보낸다 — 직전 시즌에
     * 흐름·리포트가 없으면(이 기능 전의 시즌 등) 비교할 기록이 없으므로 empty를 돌려주고 호출부는 기존 무대 소개를 쓴다.
     *
     * @param past         지난 시즌 전부(최근 순).
     * @param allowedBreads NPC가 쓸 수 있는 빵 종류(내 빵 종류 제외).
     */
    static Optional<SeasonDesignRequest> request(int seasonNumber, String gender, int age, TraitsDto traits,
                                                 AiSeasonSetting setting, List<AiPastSeason> past,
                                                 List<String> allowedBreads) {
        List<SeasonDesignRequest.PreviousSeason> previous = new ArrayList<>();
        for (AiPastSeason season : past) {
            if (previous.size() >= PREVIOUS_SEASONS_MAX
                    || season.seasonNumber() != seasonNumber - 1 - previous.size()) {
                break;
            }
            Optional<SeasonDesignRequest.PreviousSeason> converted = previousSeason(season);
            if (converted.isEmpty()) {
                break;
            }
            previous.add(converted.get());
        }
        List<String> breads = allowedBreads.stream().distinct().toList();
        if (previous.isEmpty() || traits == null || breads.size() < CAST_SIZE) {
            return Optional.empty();
        }
        return Optional.of(new SeasonDesignRequest(gender.toLowerCase(), age, traits, seasonNumber,
                setting.wireValue(), previous, breads, previousCastNames(past),
                distinctLines(past.stream().map(AiPastSeason::stageSubtitle), CATALOG_MAX)));
    }

    private static Optional<SeasonDesignRequest.PreviousSeason> previousSeason(AiPastSeason season) {
        AiReportView report = season.report();
        if (season.setting() == null || season.route() == null || report == null) {
            return Optional.empty();
        }
        String tendency = line(report.tendency());
        String feedback = text(report.feedback());
        if (tendency == null || feedback == null) {
            return Optional.empty();
        }
        SeasonDesignRequest.PreviousPartner partner = null;
        if (season.route() == AiStoryRoute.ROMANCE) {
            if (season.partner() == null || line(season.partner().name()) == null) {
                return Optional.empty();
            }
            AiPastSeason.Partner p = season.partner();
            String oneLine = line(p.oneLine());
            partner = new SeasonDesignRequest.PreviousPartner(line(p.name()), designCastRole(p.castRole()),
                    oneLine != null ? oneLine : p.castRole().seasonRole());
        }
        List<SeasonDesignRequest.PreviousChoice> choices = season.choices().stream()
                .filter(c -> Objects.equals(EPISODE_OF_POINT.get(c.pointId()), c.episodeOrder()))
                .sorted(Comparator.comparingInt(AiPastSeason.Choice::pointId))
                .map(c -> new SeasonDesignRequest.PreviousChoice(c.pointId(), c.episodeOrder(), choiceSummary(c)))
                .toList();
        return Optional.of(new SeasonDesignRequest.PreviousSeason(season.seasonNumber(),
                season.setting().wireValue(), season.route().wireValue(), partner, choices,
                new SeasonDesignRequest.PreviousReport(tendency, feedback, chips(report.strengths()),
                        chips(report.weaknesses())),
                distinctLines(season.feedbacks().stream(), USER_FEEDBACKS_MAX)));
    }

    /** 이전 시즌 출연진 이름 전체(최근 시즌부터). 최종 상대 이름도 여기 들어 있다. */
    private static List<String> previousCastNames(List<AiPastSeason> past) {
        return distinctLines(past.stream().flatMap(s -> s.castNames().stream()), CATALOG_MAX);
    }

    /** 선택 기록 한 줄. 고른 상대·수락 여부·거절 사유를 문장으로 담는다(설정 입력에는 별도 필드가 없다). */
    static String choiceSummary(AiPastSeason.Choice choice) {
        if (choice.breadName() == null) {
            return "혼자 있기를 골랐다.";
        }
        String action = choice.pointId() == AiChoiceRules.CONFESSION_POINT
                ? choice.breadName() + "에게 마음을 전했"
                : choice.breadName() + "에게 함께하자고 했";
        if (choice.accepted()) {
            return action + "고 상대가 받아들였다.";
        }
        action += "지만";
        String reason = switch (choice.reasonCode() == null ? "" : choice.reasonCode()) {
            case AiChoiceRules.REASON_ANXIOUS_HURT -> " 이전 선택에 서운했던 상대가 거절했다.";
            case AiChoiceRules.REASON_AVOIDANT_RETREAT -> " 상대가 한발 물러서며 거절했다.";
            default -> " 아직 가까워지지 않아 상대가 거절했다.";
        };
        return action + reason;
    }

    /** 설정 계약의 castRole: 연애 후보 candidate, 친구 friend, 분위기 메이커 host. */
    static String designCastRole(AiCastRole role) {
        return switch (role) {
            case FRIEND -> SeasonDesignResponse.FRIEND;
            case MOOD_MAKER -> SeasonDesignResponse.HOST;
            default -> SeasonDesignResponse.CANDIDATE;
        };
    }

    // ---------------------------------------------------------------- 응답 검사·배정

    /**
     * 저장 전에 한 번 더 검사한다(AI도 검사하지만 출연진 배정이 이 값에 기대므로). 어긋나면
     * {@link AiStoryInvalidResponseException}으로 막고 호출부가 1회 다시 생성한다.
     */
    static void validate(SeasonDesignResponse response, SeasonDesignRequest request) {
        require(response != null && response.stage() != null && response.cast() != null
                && response.episodes() != null, "설정 응답에 stage/cast/episodes가 없습니다.");
        AiSeasonSetting setting = AiSeasonSetting.valueOf(request.setting().toUpperCase());
        require(setting.title().equals(response.stage().title()), "stage title이 배경과 다릅니다.");
        requireLine(response.stage().subtitle(), SUBTITLE_MAX, "stage subtitle");
        require(response.cast().size() == CAST_SIZE, "NPC가 5명이 아닙니다: " + response.cast().size());

        Set<String> previousNames = new HashSet<>(request.previousCastNames());
        Set<String> names = new HashSet<>();
        Set<String> breads = new HashSet<>();
        Map<String, Integer> roles = new HashMap<>();
        for (SeasonDesignResponse.Cast cast : response.cast()) {
            require(cast.castRole() != null, "castRole이 없습니다.");
            roles.merge(cast.castRole(), 1, Integer::sum);
            require(cast.name() != null && NAME.matcher(cast.name()).matches(), "이름 형식: " + cast.name());
            require(names.add(cast.name()) && !previousNames.contains(cast.name()), "겹치는 이름: " + cast.name());
            require(request.allowedBreadTypes().contains(cast.breadType()) && breads.add(cast.breadType()),
                    "허용되지 않거나 겹치는 빵 종류: " + cast.breadType());
            requireLine(cast.role(), ROLE_MAX, "cast role");
            requireLine(cast.description(), DESCRIPTION_MAX, "cast description");
            requireTraits(cast.traits());
        }
        require(roles.equals(Map.of(SeasonDesignResponse.CANDIDATE, CANDIDATES, SeasonDesignResponse.FRIEND, 1,
                SeasonDesignResponse.HOST, 1)), "NPC 역할 구성이 다릅니다: " + roles);

        require(response.episodes().size() == EPISODES, "회차 초안이 10개가 아닙니다.");
        for (int i = 0; i < EPISODES; i++) {
            SeasonDesignResponse.Episode episode = response.episodes().get(i);
            require(episode != null && episode.order() == i + 1, "회차 초안 순서가 다릅니다.");
            require(Objects.equals(episode.choicePoint(), AiStoryCatalog.pointOf(episode.order())),
                    "선택 지점이 다릅니다: " + episode.order() + "화");
            require(episode.title() != null && !episode.title().isBlank()
                    && episode.situation() != null && !episode.situation().isBlank(), "회차 초안 문구가 없습니다.");
        }
    }

    /**
     * NPC를 출연진 역할에 배정한다. 친구 → 친구, host → 분위기 메이커. 연애 후보 셋의 애착 유형은 설정의 성향 점수로
     * 정한다: 관계 회피도가 가장 높은 후보가 회피형, 남은 둘 중 관계 불안도가 높은 후보가 불안형, 나머지가 안정형(같으면
     * 응답 순서). 이름·소개 문구로 역할을 추측하지 않는다.
     */
    static Map<AiCastRole, SeasonDesignResponse.Cast> assign(SeasonDesignResponse design) {
        Map<AiCastRole, SeasonDesignResponse.Cast> assigned = new EnumMap<>(AiCastRole.class);
        List<SeasonDesignResponse.Cast> candidates = new ArrayList<>();
        for (SeasonDesignResponse.Cast cast : design.cast()) {
            switch (cast.castRole()) {
                case SeasonDesignResponse.FRIEND -> assigned.put(AiCastRole.FRIEND, cast);
                case SeasonDesignResponse.HOST -> assigned.put(AiCastRole.MOOD_MAKER, cast);
                default -> candidates.add(cast);
            }
        }
        SeasonDesignResponse.Cast avoidant = highest(candidates, TraitsDto::relationshipAvoidance);
        candidates.remove(avoidant);
        SeasonDesignResponse.Cast anxious = highest(candidates, TraitsDto::relationshipAnxiety);
        candidates.remove(anxious);
        assigned.put(AiCastRole.AVOIDANT, avoidant);
        assigned.put(AiCastRole.ANXIOUS, anxious);
        assigned.put(AiCastRole.SECURE, candidates.get(0));
        return assigned;
    }

    private static SeasonDesignResponse.Cast highest(List<SeasonDesignResponse.Cast> candidates,
                                                     Function<TraitsDto, BigDecimal> score) {
        SeasonDesignResponse.Cast best = candidates.get(0);
        for (SeasonDesignResponse.Cast cast : candidates) {
            if (score.apply(cast.traits()).compareTo(score.apply(best.traits())) > 0) {
                best = cast;
            }
        }
        return best;
    }

    /** 저장 값: 설정 결과와 이 시즌의 비교 자료(직전 시즌 하트 대사·약점·강점, 이전 출연진 이름). */
    static AiSeasonDesignRecord record(SeasonDesignRequest request, SeasonDesignResponse response,
                                       List<AiPastSeason> past) {
        AiPastSeason latest = past.get(0);
        SeasonDesignRequest.PreviousReport report = request.previousSeasons().get(0).report();
        return new AiSeasonDesignRecord(request.traits(), response,
                distinctLines(latest.likedLines().stream(), LIKED_LINES_MAX), request.previousCastNames(),
                report.weaknesses(), report.strengths());
    }

    // ---------------------------------------------------------------- 저장한 설정 쓰기

    /** 1화 무대 소개: 설정의 제목·부제와 NPC 역할·소개. 내 빵은 역할 "나", 소개 없음. */
    static AiStageIntroView stageIntro(AiStorySnapshot snapshot, AiSeasonDesignRecord record) {
        Map<AiCastRole, SeasonDesignResponse.Cast> assigned = assign(record.design());
        List<AiStageIntroView.StageCast> cast = snapshot.cast().stream()
                .map(m -> {
                    SeasonDesignResponse.Cast designed = assigned.get(m.castRole());
                    return m.isMe() || designed == null
                            ? new AiStageIntroView.StageCast(m.breadId(), m.name(), m.breadType(),
                                    AiStoryService.MY_BREAD_STAGE_ROLE, null)
                            : new AiStageIntroView.StageCast(m.breadId(), m.name(), m.breadType(), designed.role(),
                                    designed.description());
                })
                .toList();
        return new AiStageIntroView(snapshot.setting().wireValue(), record.design().stage().title(),
                record.design().stage().subtitle(), cast);
    }

    static StoryTypes.EpisodeSeasonContext episodeContext(int seasonNumber, AiSeasonDesignRecord record, int order) {
        return new StoryTypes.EpisodeSeasonContext(seasonNumber, record.traits(), episode(record, order).testsWeakness(),
                record.likedLines(), record.previousCastNames());
    }

    static StoryTypes.ReportSeasonContext reportContext(int seasonNumber, AiSeasonDesignRecord record) {
        return new StoryTypes.ReportSeasonContext(seasonNumber, record.previousWeaknesses(),
                record.previousStrengths());
    }

    /** 본문 참고 줄거리: 이번 시즌 회차 초안을 앞에 두고, 기존 본편 참고 줄거리가 있으면 뒤에 붙인다. */
    static String referencePlot(String base, AiSeasonDesignRecord record, int order) {
        SeasonDesignResponse.Episode draft = episode(record, order);
        String plot = "[이번 시즌 " + order + "화 초안]\n제목: " + draft.title() + "\n상황: " + draft.situation()
                + "\n장면 전환: " + draft.transitionText();
        return base == null || base.isBlank() ? plot : plot + "\n\n[기존 본편 참고 줄거리]\n" + base;
    }

    private static SeasonDesignResponse.Episode episode(AiSeasonDesignRecord record, int order) {
        return record.design().episodes().get(order - 1);
    }

    // ---------------------------------------------------------------- 문자열

    /** 한 줄 문자열(줄바꿈 → 공백, 1000자까지). 비면 null. */
    static String line(String value) {
        if (value == null) {
            return null;
        }
        String single = LINE_BREAKS.matcher(value).replaceAll(" ").trim();
        if (single.isEmpty()) {
            return null;
        }
        return single.length() > LINE_MAX ? single.substring(0, LINE_MAX) : single;
    }

    private static String text(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > FEEDBACK_MAX ? trimmed.substring(0, FEEDBACK_MAX) : trimmed;
    }

    private static List<String> chips(List<String> values) {
        return distinctLines(values == null ? Stream.empty() : values.stream(), REPORT_CHIPS_MAX);
    }

    private static List<String> distinctLines(Stream<String> values, int limit) {
        return values.map(AiSeasonDesigns::line)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .limit(limit)
                .toList();
    }

    private static void requireTraits(TraitsDto traits) {
        require(traits != null, "traits가 없습니다.");
        for (BigDecimal score : Arrays.asList(traits.affectionExpression(), traits.relationshipAnxiety(),
                traits.relationshipAvoidance(), traits.emotionalAttunement(), traits.relationshipInitiative(),
                traits.practicalPriority(), traits.reassuranceNeed(), traits.jealousyReactivity(),
                traits.relationshipEnergyDependence(), traits.emotionalSuppression(), traits.conflictConfrontation(),
                traits.relationshipPace(), traits.interestExpressionFrequency())) {
            require(score != null && score.signum() >= 0 && score.compareTo(BigDecimal.valueOf(SCORE_MAX)) <= 0,
                    "traits 점수 범위: " + score);
        }
    }

    private static void requireLine(String value, int max, String field) {
        require(value != null && !value.isBlank() && value.length() <= max
                && !LINE_BREAKS.matcher(value).find(), field + " 형식");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AiStoryInvalidResponseException(message);
        }
    }
}
