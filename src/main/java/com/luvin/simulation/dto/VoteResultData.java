package com.luvin.simulation.dto;

import java.util.List;

/**
 * GET /vote-results 응답과 POST /vote-results 요청에 공통으로 쓰는 구조.
 */
public class VoteResultData {
    private List<PickItem> picks;
    private List<MatchItem> matches;

    public VoteResultData() {}

    public VoteResultData(List<PickItem> picks, List<MatchItem> matches) {
        this.picks = picks;
        this.matches = matches;
    }

    public List<PickItem> getPicks() { return picks; }
    public List<MatchItem> getMatches() { return matches; }
    public void setPicks(List<PickItem> picks) { this.picks = picks; }
    public void setMatches(List<MatchItem> matches) { this.matches = matches; }

    public static class PickItem {
        private Long participantId;
        private Long votedForParticipantId;

        public PickItem() {}

        public PickItem(Long participantId, Long votedForParticipantId) {
            this.participantId = participantId;
            this.votedForParticipantId = votedForParticipantId;
        }

        public Long getParticipantId() { return participantId; }
        public Long getVotedForParticipantId() { return votedForParticipantId; }
        public void setParticipantId(Long participantId) { this.participantId = participantId; }
        public void setVotedForParticipantId(Long votedForParticipantId) { this.votedForParticipantId = votedForParticipantId; }
    }

    public static class MatchItem {
        private Long participantAId;
        private Long participantBId;
        private boolean isFinal;

        public MatchItem() {}

        public MatchItem(Long participantAId, Long participantBId, boolean isFinal) {
            this.participantAId = participantAId;
            this.participantBId = participantBId;
            this.isFinal = isFinal;
        }

        public Long getParticipantAId() { return participantAId; }
        public Long getParticipantBId() { return participantBId; }
        public boolean getIsFinal() { return isFinal; }
        public void setParticipantAId(Long participantAId) { this.participantAId = participantAId; }
        public void setParticipantBId(Long participantBId) { this.participantBId = participantBId; }
        public void setIsFinal(boolean isFinal) { this.isFinal = isFinal; }
    }
}
