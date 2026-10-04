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

    /** 일기 하나 삭제 전 반응 정리 (DB FK에 ON DELETE CASCADE가 없음). */
    @Modifying(clearAutomatically = true)
    @Query("delete from DiaryReaction r where r.diary.id = :diaryId")
    void deleteReactionsByDiaryId(@Param("diaryId") Long diaryId);

    /** 공유방 삭제 시: 그 방 일기들의 반응 → (댓글은 DiaryCommentRepository) → 일기 순서로 지운다. */
    @Modifying(clearAutomatically = true)
    @Query("delete from DiaryReaction r where r.diary.id in (select d.id from Diary d where d.room.id = :roomId)")
    void deleteReactionsByRoomId(@Param("roomId") Long roomId);

    @Modifying(clearAutomatically = true)
    @Query("delete from Diary d where d.room.id = :roomId")
    void deleteAllByRoomId(@Param("roomId") Long roomId);

    /**
     * 같은 이모지로 이미 반응했으면 지운다 (= 같은 이모지를 다시 누르면 취소).
     * @return 취소됐으면 1, 아니면 0
     */
    // clearAutomatically를 쓰지 않는다: 이미 불러온 Diary가 떨어져 나가면 뒤의 권한 확인에서 room을 못 읽는다.
    @Modifying
    @Query("delete from DiaryReaction r where r.id.diaryId = :diaryId and r.id.userId = :userId and r.emoji = :emoji")
    int deleteReactionIfSame(@Param("diaryId") Long diaryId, @Param("userId") Long userId, @Param("emoji") String emoji);

    /**
     * 반응을 추가하거나 다른 이모지로 바꾼다. (diary_id, user_id) PK 충돌을 DB가 원자적으로 처리하므로
     * 동시 요청에도 한 사람당 반응은 1개만 남는다.
     */
    @Modifying
    @Query(value = "insert into diary_reaction (diary_id, user_id, emoji, created_at) "
            + "values (:diaryId, :userId, :emoji, now()) "
            + "on conflict (diary_id, user_id) do update set emoji = excluded.emoji, created_at = now()",
            nativeQuery = true)
    int upsertReaction(@Param("diaryId") Long diaryId, @Param("userId") Long userId, @Param("emoji") String emoji);

    @Query("select count(r) from DiaryReaction r where r.diary.id = :diaryId")
    long countReactions(@Param("diaryId") Long diaryId);

    /** 공유방 일기 목록. 반응 수, 댓글 수, 내 반응을 서브쿼리로 한 번에 가져온다 (N+1 방지). 최신순. */
    @Query("select new com.luvin.diary.dto.DiaryDto$FeedItem(" + FEED_SELECT
            + "from Diary d left join User u on u.id = d.userId "
            + "where d.room.id = :roomId "
            + "order by d.createdAt desc")
    List<DiaryDto.FeedItem> findRoomFeed(@Param("roomId") Long roomId, @Param("me") Long me);

    /**
     * 내가 속한(방장이거나 멤버인) 모든 방의 일기 목록. 최신순.
     * 개수 제한은 pageable로 한다 (JPQL에는 limit을 직접 못 씀).
     */
    @Query("select new com.luvin.diary.dto.DiaryDto$FeedItem(" + FEED_SELECT
            + "from Diary d left join User u on u.id = d.userId "
            + "where d.room.id in (select m.id.roomId from DiaryRoomMember m where m.id.userId = :me) "
            + "or d.room.ownerId = :me "
            + "order by d.createdAt desc")
    List<DiaryDto.FeedItem> findMyRoomsFeed(@Param("me") Long me, Pageable pageable);

    /** FeedItem 필드 순서와 정확히 같아야 한다. */
    // 작성자 정보는 Diary-User 연관관계(FK)가 없어서 조건으로 조인한다(left join: 사용자가 없어도 일기는 나옴).
    String FEED_SELECT = "d.id, d.room.id, d.userId, coalesce(u.nickname, u.name), u.personalityType, "
            + "d.title, d.content, "
            + "(case when d.userId = :me then true else false end), "
            + "(select count(r) from DiaryReaction r where r.diary = d), "
            + "(select count(c) from DiaryComment c where c.diary = d), "
            + "(case when exists (select 1 from DiaryReaction r2 where r2.diary = d and r2.id.userId = :me) "
            + "then true else false end), "
            + "(select r3.emoji from DiaryReaction r3 where r3.diary = d and r3.id.userId = :me), "
            + "d.createdAt, d.updatedAt) ";
}
