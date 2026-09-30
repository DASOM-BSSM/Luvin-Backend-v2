package com.luvin.diary.repository;

import com.luvin.diary.domain.Diary;
import com.luvin.diary.dto.DiaryDto;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DiaryRepository extends JpaRepository<Diary, Long> {

    List<Diary> findAllByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

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

    /**
     * 공유방 일기 목록. 공감 수, 댓글 수, 내 공감 여부를 서브쿼리로 한 번에 가져온다 (N+1 방지).
     * PRIVATE 일기는 작성자 본인 것만 포함한다. 최신순.
     */
    @Query("select new com.luvin.diary.dto.DiaryDto$FeedItem("
            + "d.id, rm.id, d.userId, d.title, d.content, d.visibility, "
            + "(case when d.userId = :me then true else false end), "
            + "(select count(r) from DiaryReaction r where r.diary = d), "
            + "(select count(c) from DiaryComment c where c.diary = d), "
            + "(case when exists (select 1 from DiaryReaction r2 where r2.diary = d and r2.id.userId = :me) "
            + "then true else false end), "
            + "d.createdAt, d.updatedAt) "
            + "from Diary d left join d.room rm "
            + "where d.room.id = :roomId "
            + "and (d.visibility <> com.luvin.diary.domain.DiaryVisibility.PRIVATE or d.userId = :me) "
            + "order by d.createdAt desc")
    List<DiaryDto.FeedItem> findRoomFeed(@Param("roomId") Long roomId, @Param("me") Long me);

    /**
     * 커뮤니티 일기 목록. PUBLIC 일기만 최신순으로 가져온다.
     * select 부분은 findRoomFeed와 같고, 개수 제한은 pageable로 한다 (JPQL에는 limit을 직접 못 씀).
     */
    @Query("select new com.luvin.diary.dto.DiaryDto$FeedItem("
            + "d.id, rm.id, d.userId, d.title, d.content, d.visibility, "
            + "(case when d.userId = :me then true else false end), "
            + "(select count(r) from DiaryReaction r where r.diary = d), "
            + "(select count(c) from DiaryComment c where c.diary = d), "
            + "(case when exists (select 1 from DiaryReaction r2 where r2.diary = d and r2.id.userId = :me) "
            + "then true else false end), "
            + "d.createdAt, d.updatedAt) "
            + "from Diary d left join d.room rm "
            + "where d.visibility = com.luvin.diary.domain.DiaryVisibility.PUBLIC "
            + "order by d.createdAt desc")
    List<DiaryDto.FeedItem> findCommunityFeed(@Param("me") Long me, Pageable pageable);
}
