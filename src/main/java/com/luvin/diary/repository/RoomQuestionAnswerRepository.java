package com.luvin.diary.repository;

import com.luvin.diary.domain.RoomQuestionAnswer;
import com.luvin.diary.dto.RoomQuestionDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoomQuestionAnswerRepository extends JpaRepository<RoomQuestionAnswer, Long> {

    /** 답을 쓰거나 바꾼다. (question_id, user_id) UNIQUE라 동시 요청에도 1행. */
    @Modifying(clearAutomatically = true)
    @Query(value = "insert into room_question_answer (question_id, user_id, content, created_at, updated_at) "
            + "values (:questionId, :userId, :content, now(), now()) "
            + "on conflict (question_id, user_id) do update set content = excluded.content, updated_at = now()",
            nativeQuery = true)
    int upsert(@Param("questionId") Long questionId, @Param("userId") Long userId, @Param("content") String content);

    Optional<RoomQuestionAnswer> findByQuestionIdAndUserId(Long questionId, Long userId);

    @Modifying(clearAutomatically = true)
    @Query("delete from RoomQuestionAnswer a where a.questionId = :questionId and a.userId = :userId")
    int deleteByQuestionIdAndUserId(@Param("questionId") Long questionId, @Param("userId") Long userId);

    /** 답 목록(작성순). 닉네임은 피드와 같이 닉네임이 없으면 이름. 필드 순서는 AnswerItem과 같아야 한다. */
    @Query("select new com.luvin.diary.dto.RoomQuestionDto$AnswerItem(a.id, a.userId, coalesce(u.nickname, u.name), "
            + "a.content, a.createdAt, a.updatedAt) "
            + "from RoomQuestionAnswer a left join User u on u.id = a.userId "
            + "where a.questionId = :questionId order by a.createdAt asc, a.id asc")
    List<RoomQuestionDto.AnswerItem> findAnswerItems(@Param("questionId") Long questionId);

    @Query("select a.questionId, count(a) from RoomQuestionAnswer a where a.questionId in :questionIds "
            + "group by a.questionId")
    List<Object[]> countByQuestionIds(@Param("questionIds") Collection<Long> questionIds);

    @Modifying(clearAutomatically = true)
    @Query("delete from RoomQuestionAnswer a where a.questionId in "
            + "(select q.id from RoomQuestion q where q.roomId = :roomId)")
    void deleteAllByRoomId(@Param("roomId") Long roomId);
}
