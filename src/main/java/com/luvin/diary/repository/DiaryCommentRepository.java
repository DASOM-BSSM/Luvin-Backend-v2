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
}
