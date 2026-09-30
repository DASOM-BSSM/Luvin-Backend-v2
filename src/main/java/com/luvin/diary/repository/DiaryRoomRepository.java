package com.luvin.diary.repository;

import com.luvin.diary.domain.DiaryRoom;
import com.luvin.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DiaryRoomRepository extends JpaRepository<DiaryRoom, Long> {

    //내가 속한 공유방 목록 조회
    @Query("select r from DiaryRoom r join DiaryRoomMember m on r.id = m.id.roomId where m.id.userId = :userId")
    List<DiaryRoom> findAllByMemberId(@Param("userId") Long userId);

    // 특정 공유방의 멤버 목록 조회
    @Query("select u from User u join DiaryRoomMember m on u.id = m.id.userId where m.id.roomId = :roomId")
    List<User> findMembersByRoomId(@Param("roomId") Long roomId);

    // DiaryRoomMember 중 id.roomId == roomId AND id.userId == userId 인 행이 있는가?
    @Query("select count(m) > 0 from DiaryRoomMember m where m.id.roomId = :roomId AND m.id.userId = :userId")
    boolean isMember(@Param("roomId") Long roomId, @Param("userId") Long userId);

    /**
     * 멤버로 추가한다. 이미 멤버면 아무것도 하지 않음.
     * (room_id, user_id) PK 충돌을 DB가 원자적으로 무시하므로 동시 요청에도 1행만 남음.
     * @return 새로 추가됐으면 1, 이미 멤버였으면 0
     */
    @Modifying
    @Query(value = "insert into diary_room_member (room_id, user_id, role, joined_at) "
            + "values (:roomId, :userId, 'MEMBER', now()) on conflict do nothing",
            nativeQuery = true)
    int insertMemberIfAbsent(@Param("roomId") Long roomId, @Param("userId") Long userId);

    /** 방 생성 시 방장을 OWNER로 등록한다. */
    @Modifying
    @Query(value = "insert into diary_room_member (room_id, user_id, role, joined_at) "
            + "values (:roomId, :userId, 'OWNER', now()) on conflict do nothing",
            nativeQuery = true)
    int insertOwner(@Param("roomId") Long roomId, @Param("userId") Long userId);

    /** 방 삭제 전 멤버 정리 (DB FK에 ON DELETE CASCADE가 없음). */
    @Modifying(clearAutomatically = true)
    @Query("delete from DiaryRoomMember m where m.id.roomId = :roomId")
    void deleteAllMembersByRoomId(@Param("roomId") Long roomId);

    /** 나가기, 강퇴 둘 다 사용. @return 삭제됐으면 1, 원래 멤버가 아니었으면 0 */
    @Modifying(clearAutomatically = true)
    @Query("delete from DiaryRoomMember m where m.id.roomId = :roomId and m.id.userId = :userId")
    int deleteMember(@Param("roomId") Long roomId, @Param("userId") Long userId);

    @Query("select count(m) from DiaryRoomMember m where m.id.roomId = :roomId")
    long countMembers(@Param("roomId") Long roomId);
}
