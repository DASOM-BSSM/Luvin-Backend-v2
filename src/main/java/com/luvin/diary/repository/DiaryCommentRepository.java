package com.luvin.diary.repository;

import com.luvin.diary.domain.DiaryComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface DiaryCommentRepository extends JpaRepository<DiaryComment, Long> {
    List<DiaryComment> findByDiaryIdOrderByCreatedAtAsc(Long diaryId);
    @Modifying(clearAutomatically = true)
    @Query("delete from DiaryComment c where c.diary.id = :diaryId")
    void deleteAllByDiaryId(@Param("diaryId") Long diaryId);

    /** 공유방 삭제 시 그 방 일기들의 댓글을 지운다. */
    @Modifying(clearAutomatically = true)
    @Query("delete from DiaryComment c where c.diary.id in (select d.id from Diary d where d.room.id = :roomId)")
    void deleteAllByRoomId(@Param("roomId") Long roomId);
}
