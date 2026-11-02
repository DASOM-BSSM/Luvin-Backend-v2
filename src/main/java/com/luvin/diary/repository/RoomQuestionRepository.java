package com.luvin.diary.repository;

import com.luvin.diary.domain.RoomQuestion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface RoomQuestionRepository extends JpaRepository<RoomQuestion, Long> {

    /**
     * 이 칸의 질문 자리를 잡는다. (room_id, slot_start_at) UNIQUE라 동시에 눌러도 1행만 생긴다.
     * @return 새로 잡았으면 1, 이미 있으면 0
     */
    @Modifying
    @Query(value = "insert into room_question (room_id, question_date, slot_start_at, status, requested_by, created_at, updated_at) "
            + "values (:roomId, :date, :slotStartAt, 'GENERATING', :userId, now(), now()) "
            + "on conflict (room_id, slot_start_at) do nothing", nativeQuery = true)
    int claimSlot(@Param("roomId") Long roomId, @Param("slotStartAt") OffsetDateTime slotStartAt,
                  @Param("date") LocalDate date, @Param("userId") Long userId);

    /**
     * 실패했거나 staleBefore보다 오래 GENERATING에 멈춘 질문을 다시 생성 중으로 바꾼다.
     * @return 바꿨으면 1 (= 이 요청이 생성을 맡는다)
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "update room_question set status = 'GENERATING', content = null, requested_by = :userId, "
            + "updated_at = now() where id = :id and (status = 'FAILED' "
            + "or (status = 'GENERATING' and updated_at < :staleBefore))", nativeQuery = true)
    int reclaim(@Param("id") Long id, @Param("userId") Long userId, @Param("staleBefore") OffsetDateTime staleBefore);

    @Modifying(clearAutomatically = true)
    @Query(value = "update room_question set status = 'READY', content = :content, updated_at = now() "
            + "where id = :id and status = 'GENERATING'", nativeQuery = true)
    int markReady(@Param("id") Long id, @Param("content") String content);

    @Modifying(clearAutomatically = true)
    @Query(value = "update room_question set status = 'FAILED', updated_at = now() "
            + "where id = :id and status = 'GENERATING'", nativeQuery = true)
    int markFailed(@Param("id") Long id);

    Optional<RoomQuestion> findByRoomIdAndSlotStartAt(Long roomId, OffsetDateTime slotStartAt);

    /** 지금 질문 = 가장 최근 칸의 질문. */
    Optional<RoomQuestion> findFirstByRoomIdOrderBySlotStartAtDesc(Long roomId);

    /** 지난 질문 목록(최신순). */
    List<RoomQuestion> findByRoomIdOrderBySlotStartAtDesc(Long roomId, Pageable pageable);

    /** 지난 질문 목록 중 칸이 [from, to)인 것(최신순). */
    @Query("select q from RoomQuestion q where q.roomId = :roomId and q.slotStartAt >= :from and q.slotStartAt < :to "
            + "order by q.slotStartAt desc")
    List<RoomQuestion> findInSlotRange(@Param("roomId") Long roomId, @Param("from") OffsetDateTime from,
                                       @Param("to") OffsetDateTime to, Pageable pageable);

    long countByRoomId(Long roomId);

    /** AI에 넘길 최근 질문(중복 방지용). */
    @Query("select q.content from RoomQuestion q where q.roomId = :roomId and q.content is not null "
            + "order by q.slotStartAt desc")
    List<String> findRecentContents(@Param("roomId") Long roomId, Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("delete from RoomQuestion q where q.roomId = :roomId")
    void deleteAllByRoomId(@Param("roomId") Long roomId);
}
