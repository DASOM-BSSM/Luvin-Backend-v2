package com.luvin.ai.client.story;

/**
 * 5-6 응답(부록 A: 기존 쪽지 응답 형식). 발신 빵은 Spring이 정한 sender를 쓰고 finalPartnerId는 쓰지 않는다.
 * (부록 A 확정 전 가정) 계약 부록 A가 AI 쪽에서 채워지기 전이라, 기존 쪽지 응답
 * (letter_id, final_partner_id, body, render_mode)을 camelCase로 옮기고 발신 빵을 빵 id로 받는다.
 */
public record EpisodeNoteResponse(String letterId, String finalPartnerId, String body, String renderMode) {
}
