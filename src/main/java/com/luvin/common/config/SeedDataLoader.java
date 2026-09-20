package com.luvin.common.config;

import com.luvin.dailyquestion.domain.DailyQuestion;
import com.luvin.dailyquestion.domain.DailyQuestionOption;
import com.luvin.dailyquestion.repository.DailyQuestionOptionRepository;
import com.luvin.dailyquestion.repository.DailyQuestionRepository;
import com.luvin.survey.domain.Survey;
import com.luvin.survey.domain.SurveyOption;
import com.luvin.survey.domain.SurveyQuestion;
import com.luvin.survey.repository.SurveyOptionRepository;
import com.luvin.survey.repository.SurveyQuestionRepository;
import com.luvin.survey.repository.SurveyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SeedDataLoader implements ApplicationRunner {

    private final SurveyRepository surveyRepository;
    private final SurveyQuestionRepository surveyQuestionRepository;
    private final SurveyOptionRepository surveyOptionRepository;
    private final DailyQuestionRepository dailyQuestionRepository;
    private final DailyQuestionOptionRepository dailyQuestionOptionRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (surveyRepository.count() == 0) {
            seedPersonalitySurvey();
        }

        if (dailyQuestionRepository.count() == 0) {
            seedDailyQuestions();
        }
    }

    private void seedPersonalitySurvey() {
        Survey survey = surveyRepository.save(new Survey("사용자 성격 변수 판별 설문"));
        for (QuestionSeed questionSeed : personalityQuestions()) {
            SurveyQuestion question = surveyQuestionRepository.save(new SurveyQuestion(survey, questionSeed.content()));
            for (OptionSeed optionSeed : questionSeed.options()) {
                surveyOptionRepository.save(new SurveyOption(question, optionSeed.content(), optionSeed.effects()));
            }
        }
    }

    private void seedDailyQuestions() {
        for (QuestionSeed questionSeed : personalityQuestions()) {
            DailyQuestion question = dailyQuestionRepository.save(new DailyQuestion(questionSeed.content()));
            for (OptionSeed optionSeed : questionSeed.options()) {
                dailyQuestionOptionRepository.save(new DailyQuestionOption(question, optionSeed.content(), optionSeed.effects()));
            }
        }
    }

    private List<QuestionSeed> personalityQuestions() {
        return List.of(
                q("좋아하는 사람이랑 가까워지고 싶을 때 나는",
                        o("같이 할 수 있는 걸로 자리를 만든다", "관계주도성:+20,관계속도감:+10"),
                        o("자연스럽게 천천히 가까워진다", "관계속도감:+5"),
                        o("상대가 먼저 다가와주길 기다린다", "관계주도성:-20,관계속도감:-10")),
                q("사귀기 전 좋아하는 감정을 상대가 눈치챘으면 할 때 나는",
                        o("직접 말하거나 확실하게 티를 낸다", "애정표현성:+20,감정억제성:-10,관심표현빈도:+10"),
                        o("작은 행동으로 알아채주길 바란다", "애정표현성:+10,관심표현빈도:+5"),
                        o("들키지 않으려고 최대한 숨긴다", "애정표현성:-20,감정억제성:+10,관심표현빈도:-10")),
                q("답장이 평소보다 늦게 왔을 때 나는",
                        o("별로 신경 안 쓴다", "관계불안도:-20,확신요구도:-10"),
                        o("살짝 신경 쓰이지만 참는다", "관계불안도:+3"),
                        o("이유가 뭔지 여러 가지 생각이 든다", "관계불안도:+20,확신요구도:+10")),
                q("상대가 나한테 많이 의지하기 시작하면",
                        o("가까워지는 것 같아서 좋다", "관계회피성:-20,관계에너지의존도:+10"),
                        o("좋긴 한데 가끔 벅차다", ""),
                        o("조금 부담스럽고 거리를 두고 싶어진다", "관계회피성:+20,관계에너지의존도:-10")),
                q("같이 있는 사람이 기분이 안 좋으면 나는",
                        o("나도 같이 가라앉는다", "감정동조성:+20"),
                        o("신경은 쓰이지만 내 기분은 유지한다", "감정동조성:+10"),
                        o("내 기분은 따로 유지할 수 있다", "감정동조성:-20")),
                q("사귀는 사람이랑 갈등이 생겼을 때 나는",
                        o("바로 얘기해서 빨리 해결하고 싶다", "현실우선성:+20,갈등직면성:+10"),
                        o("감정 좀 식으면 그때 차분하게 얘기한다", "현실우선성:+10"),
                        o("분위기가 자연스럽게 풀릴 때까지 기다린다", "현실우선성:-20,갈등직면성:-10")),
                q("연락 패턴은",
                        o("내가 먼저 연락하는 편이다", "관계주도성:+20,관심표현빈도:+20"),
                        o("서로 비슷하게 주고받는 편이다", "관심표현빈도:+10"),
                        o("오면 잘 받지만 먼저 하진 않는다", "관계주도성:-20,관심표현빈도:-10")),
                q("상대가 힘들어 보일 때 나는",
                        o("먼저 말 걸고 바로 표현한다", "애정표현성:+20,감정억제성:-10,관심표현빈도:+10"),
                        o("티는 안 내지만 옆에서 더 잘 챙긴다", "애정표현성:+10"),
                        o("상대가 먼저 꺼내길 기다린다", "애정표현성:-20,감정억제성:+10,관심표현빈도:-10")),
                q("상대가 다른 이성이랑 친하게 지내는 걸 알게 됐을 때",
                        o("별로 개의치 않는다", "관계불안도:-20,질투반응성:-20"),
                        o("살짝 신경 쓰이지만 믿으려고 한다", ""),
                        o("신경 쓰이고 괜히 예민해진다", "관계불안도:+20,질투반응성:+20")),
                q("연애할 때 나는",
                        o("일상을 많이 공유하고 같이 있는 시간이 많았으면 좋다", "관계회피성:-20,관계에너지의존도:+10"),
                        o("같이 있는 시간도 좋고 혼자 시간도 필요하다", ""),
                        o("각자 시간이 충분히 있어야 편하다", "관계회피성:+20,관계에너지의존도:-10")),
                q("분위기가 어색한 자리에 가면 나는",
                        o("내가 분위기를 바꿔보려고 한다", "감정동조성:-20"),
                        o("어색하지만 적당히 맞춰간다", ""),
                        o("분위기에 맞게 나도 어색해진다", "감정동조성:+20")),
                q("썸 탈 때 밀당에 대해서",
                        o("솔직하게 표현하는 게 맞다고 생각한다", "현실우선성:+20"),
                        o("적당한 밀당은 자연스러운 과정이다", ""),
                        o("밀당을 즐기는 편이다", "현실우선성:-20")),
                q("사귀자는 말 없이 썸만 계속 이어지면",
                        o("자연스럽게 흘러가면 된다고 생각한다", "확신요구도:-20"),
                        o("슬슬 확인하고 싶어진다", ""),
                        o("빨리 관계를 정의하고 싶다", "확신요구도:+20")),
                q("좋아하는 사람이 나 말고 다른 사람한테 잘해주는 걸 봤을 때",
                        o("별로 신경 안 쓰인다", "질투반응성:-20"),
                        o("마음이 살짝 불편하지만 티는 안 낸다", "감정억제성:+10"),
                        o("모르게 신경 쓰이고 태도가 바뀐다", "질투반응성:+20,관계불안도:+10")),
                q("연애할 때 상대에게 기대는 편인가요",
                        o("독립적으로 각자 에너지를 채우는 편이다", "관계에너지의존도:-20"),
                        o("가끔은 기대고 가끔은 혼자 해결한다", ""),
                        o("상대가 있어야 힘이 나는 편이다", "관계에너지의존도:+20")),
                q("화가 났을 때 나는",
                        o("바로 표현한다", "감정억제성:-20,애정표현성:+10,갈등직면성:+10"),
                        o("어느 정도 참다가 적당한 타이밍에 말한다", ""),
                        o("최대한 감추고 혼자 삭힌다", "감정억제성:+20,애정표현성:-10,갈등직면성:-10")),
                q("상대방이 나를 서운하게 했을 때",
                        o("바로 얘기한다", "갈등직면성:+20,현실우선성:+10"),
                        o("한 번은 참고 두 번째면 말한다", ""),
                        o("최대한 넘어가려고 한다", "갈등직면성:-20,감정억제성:+10")),
                q("처음 만난 사람과 친해지는 속도는",
                        o("빠른 편이다", "관계속도감:+20,관계주도성:+10,관계회피성:-10"),
                        o("보통이다", ""),
                        o("느린 편이다", "관계속도감:-20,관계주도성:-10,관계회피성:+10")),
                q("좋아하는 사람한테 연락하는 편은",
                        o("생각날 때마다 자주 한다", "관심표현빈도:+20,애정표현성:+10,관계주도성:+10"),
                        o("하루에 한두 번 적당하게 한다", ""),
                        o("연락은 잘 안 하지만 만나면 잘 챙긴다", "관심표현빈도:-20,애정표현성:-10,관계주도성:-10")),
                q("혼자 있는 시간과 같이 있는 시간 중 어느 쪽이 더 충전이 되나요",
                        o("혼자 있을 때 훨씬 충전된다", "관계에너지의존도:-20,관계회피성:+10"),
                        o("둘 다 필요하다", ""),
                        o("좋아하는 사람이랑 있을 때 충전된다", "관계에너지의존도:+20,관계회피성:-10"))
        );
    }

    private QuestionSeed q(String content, OptionSeed first, OptionSeed second, OptionSeed third) {
        return new QuestionSeed(content, List.of(first, second, third));
    }

    private OptionSeed o(String content, String effects) {
        return new OptionSeed(content, effects);
    }

    private record QuestionSeed(String content, List<OptionSeed> options) {
    }

    private record OptionSeed(String content, String effects) {
    }
}
