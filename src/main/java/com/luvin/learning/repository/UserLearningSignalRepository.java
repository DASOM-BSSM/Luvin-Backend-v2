package com.luvin.learning.repository;

import com.luvin.learning.domain.UserLearningSignal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

public interface UserLearningSignalRepository extends JpaRepository<UserLearningSignal, Long> {

    /** 원본 하나당 신호 1개. 같은 원본을 다시 넣으면(수정 등) 아무것도 하지 않는다. */
    @Modifying
    @Query(value = "insert into user_learning_signal (user_id, source_type, source_id, created_at) "
            + "values (:userId, :sourceType, :sourceId, now()) on conflict (source_type, source_id) do nothing",
            nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId, @Param("sourceType") String sourceType,
                       @Param("sourceId") Long sourceId);

    @Modifying
    @Query(value = "delete from user_learning_signal where source_type = :sourceType and source_id = :sourceId",
            nativeQuery = true)
    int deleteBySource(@Param("sourceType") String sourceType, @Param("sourceId") Long sourceId);

    /** 일기 삭제 전: 그 일기와 일기에 달린 댓글(곧 함께 지워짐)의 신호. */
    @Modifying
    @Query(value = "delete from user_learning_signal where (source_type = 'DIARY' and source_id = :diaryId) "
            + "or (source_type = 'COMMENT' and source_id in (select id from diary_comment where diary_id = :diaryId))",
            nativeQuery = true)
    int deleteForDiary(@Param("diaryId") Long diaryId);

    /** 공유방 삭제 전: 방의 일기·그 댓글·그룹 질문 답의 신호. */
    @Modifying
    @Query(value = "delete from user_learning_signal where "
            + "(source_type = 'DIARY' and source_id in (select id from diary where room_id = :roomId)) "
            + "or (source_type = 'COMMENT' and source_id in (select c.id from diary_comment c "
            + "join diary d on d.id = c.diary_id where d.room_id = :roomId)) "
            + "or (source_type = 'ROOM_ANSWER' and source_id in (select a.id from room_question_answer a "
            + "join room_question q on q.id = a.question_id where q.room_id = :roomId))",
            nativeQuery = true)
    int deleteForRoom(@Param("roomId") Long roomId);

    @Modifying
    @Query("delete from UserLearningSignal s where s.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);

    /**
     * 말투 학습을 돌릴 사용자: 학습 동의가 켜져 있고, 아직 반영 안 된 신호가 minCount개 이상이거나 가장 오래된 것이
     * olderThan보다 오래됐다.
     */
    @Query(value = "select s.user_id from user_learning_signal s join users u on u.user_id = s.user_id "
            + "where u.learning_enabled = true and s.speech_consumed_at is null and s.source_type in (:types) "
            + "group by s.user_id having count(*) >= :minCount or min(s.created_at) < :olderThan "
            + "order by min(s.created_at) limit :limit", nativeQuery = true)
    List<Long> findSpeechDueUsers(@Param("types") Collection<String> types, @Param("minCount") long minCount,
                                  @Param("olderThan") OffsetDateTime olderThan, @Param("limit") int limit);

    @Query(value = "select s.user_id from user_learning_signal s join users u on u.user_id = s.user_id "
            + "where u.learning_enabled = true and s.personality_consumed_at is null and s.source_type in (:types) "
            + "group by s.user_id having count(*) >= :minCount or min(s.created_at) < :olderThan "
            + "order by min(s.created_at) limit :limit", nativeQuery = true)
    List<Long> findPersonalityDueUsers(@Param("types") Collection<String> types, @Param("minCount") long minCount,
                                       @Param("olderThan") OffsetDateTime olderThan, @Param("limit") int limit);

    @Query(value = "select * from user_learning_signal where user_id = :userId and speech_consumed_at is null "
            + "and source_type in (:types) order by created_at desc limit :limit", nativeQuery = true)
    List<UserLearningSignal> findPendingSpeech(@Param("userId") Long userId,
                                               @Param("types") Collection<String> types, @Param("limit") int limit);

    @Query(value = "select * from user_learning_signal where user_id = :userId and personality_consumed_at is null "
            + "and source_type in (:types) order by created_at desc limit :limit", nativeQuery = true)
    List<UserLearningSignal> findPendingPersonality(@Param("userId") Long userId,
                                                    @Param("types") Collection<String> types,
                                                    @Param("limit") int limit);

    /** @return 실제로 소비 처리한 행 수(다른 실행이 먼저 소비했으면 그만큼 적다) */
    @Modifying
    @Query("update UserLearningSignal s set s.speechConsumedAt = :now where s.id in :ids and s.speechConsumedAt is null")
    int markSpeechConsumed(@Param("ids") Collection<Long> ids, @Param("now") OffsetDateTime now);

    @Modifying
    @Query("update UserLearningSignal s set s.personalityConsumedAt = :now "
            + "where s.id in :ids and s.personalityConsumedAt is null")
    int markPersonalityConsumed(@Param("ids") Collection<Long> ids, @Param("now") OffsetDateTime now);
}
