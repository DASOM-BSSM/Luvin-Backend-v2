package com.luvin.ai.service.story;

import com.luvin.ai.client.dto.TraitsDto;
import com.luvin.ai.client.story.SeasonDesignResponse;

import java.util.List;

/**
 * ai_season_designs에 저장하는 값: 새 시즌 설정 결과와, 설정을 만들 때 정한 이 시즌의 비교 자료. 저장한 뒤로는 바꾸지
 * 않으므로 지난 시즌 기록이 나중에 바뀌어도(개발용 초기화 등) 이 시즌의 회차·리포트 입력은 그대로다.
 *
 * @param traits             내 빵의 13개 성향(이 시즌 생성 입력의 값).
 * @param likedLines         직전 시즌에 하트를 누른 대사(최대 5개).
 * @param previousCastNames  이전 시즌 출연진 이름 전체.
 * @param previousWeaknesses 직전 시즌 리포트의 약점.
 * @param previousStrengths  직전 시즌 리포트의 강점.
 */
public record AiSeasonDesignRecord(TraitsDto traits, SeasonDesignResponse design, List<String> likedLines,
                                   List<String> previousCastNames, List<String> previousWeaknesses,
                                   List<String> previousStrengths) {
}
