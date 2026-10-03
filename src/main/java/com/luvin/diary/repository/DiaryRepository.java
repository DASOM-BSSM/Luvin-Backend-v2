package com.luvin.diary.repository;

import com.luvin.diary.domain.Diary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiaryRepository extends JpaRepository<Diary, Long> {

    /** DiaryReaction은 레포지토리를 따로 두지 않으므로 일기 삭제 전 공감 정리를 여기서 한다. */
    @Modifying(clearAutomatically = true)
    @Query("delete from DiaryReaction r where r.diary.id = :diaryId")
    void deleteReactionsByDiaryId(@Param("diaryId") Long diaryId);

    @Modifying
    @Query(value = "insert into diary_reaction (diary_id, user_id, created_at) "
            + "values (:diaryId, :userId, now()) on conflict do nothing",
            nativeQuery = true)
    int insertReactionIfAbsent(@Param("diaryId") Long diaryId, @Param("userId") Long userId);

    /** @return 삭제됐으면 1, 원래 없었으면 0 */
    @Modifying(clearAutomatically = true)
    @Query("delete from DiaryReaction r where r.id.diaryId = :diaryId and r.id.userId = :userId")
    int deleteReaction(@Param("diaryId") Long diaryId, @Param("userId") Long userId);

    @Query("select count(r) from DiaryReaction r where r.diary.id = :diaryId")
    long countReactions(@Param("diaryId") Long diaryId);


}
