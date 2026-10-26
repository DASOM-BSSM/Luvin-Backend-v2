package com.luvin.diary.repository;

import com.luvin.diary.domain.DiaryRoom;
import com.luvin.diary.domain.DiaryRoomMember;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DiaryRoomRepository extends JpaRepository<DiaryRoom, Long> {

    /** 초대 코드로 참여할 때 쓴다. findByIdForUpdate와 같이 방 행을 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from DiaryRoom r where r.inviteCode = :inviteCode")
    Optional<DiaryRoom> findByInviteCodeForUpdate(@Param("inviteCode") String inviteCode);

    boolean existsByInviteCode(String inviteCode);

    /**
     * 방 행을 잠그고 읽는다. 참여·닉네임 변경처럼 "인원/닉네임 확인 → 추가"를 해야 하는 곳에서 써서,
     * 동시에 들어온 요청이 둘 다 마지막 자리를 차지하거나 같은 닉네임을 쓰지 못하게 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from DiaryRoom r where r.id = :roomId")
    Optional<DiaryRoom> findByIdForUpdate(@Param("roomId") Long roomId);

    /** 초대 코드 교체. 강퇴처럼 이미 영속성 컨텍스트가 비워진 뒤에도 쓸 수 있게 쿼리로 직접 바꾼다. */
    @Modifying(clearAutomatically = true)
    @Query("update DiaryRoom r set r.inviteCode = :inviteCode where r.id = :roomId")
    void updateInviteCode(@Param("roomId") Long roomId, @Param("inviteCode") String inviteCode);

    // 내가 속한 공유방 목록 조회. 최근에 참여한(방장은 만든) 방이 먼저 온다.
    @Query("select r from DiaryRoom r join DiaryRoomMember m on r.id = m.id.roomId where m.id.userId = :userId "
            + "order by m.joinedAt desc, r.id desc")
    List<DiaryRoom> findAllByMemberId(@Param("userId") Long userId);

    // 특정 공유방의 멤버 목록 조회 (방 닉네임 포함). 먼저 들어온 사람이 먼저 온다.
    @Query("select m from DiaryRoomMember m join fetch m.member where m.id.roomId = :roomId order by m.joinedAt asc")
    List<DiaryRoomMember> findMembersByRoomId(@Param("roomId") Long roomId);

    /** 같은 방에 이 닉네임을 쓰는 다른 멤버가 있는가? (excludeUserId 본인은 빼고 본다) */
    @Query("select count(m) > 0 from DiaryRoomMember m where m.id.roomId = :roomId "
            + "and m.nickname = :nickname and m.id.userId <> :excludeUserId")
    boolean existsNickname(@Param("roomId") Long roomId, @Param("nickname") String nickname,
                           @Param("excludeUserId") Long excludeUserId);

    /** 방 목록 응답의 인원 수를 한 번에 센다. [roomId, count] 쌍. */
    @Query("select m.id.roomId, count(m) from DiaryRoomMember m where m.id.roomId in :roomIds group by m.id.roomId")
    List<Object[]> countMembersByRoomIds(@Param("roomIds") List<Long> roomIds);

    // DiaryRoomMember 중 id.roomId == roomId AND id.userId == userId 인 행이 있는가?
    @Query("select count(m) > 0 from DiaryRoomMember m where m.id.roomId = :roomId AND m.id.userId = :userId")
    boolean isMember(@Param("roomId") Long roomId, @Param("userId") Long userId);

    /**
     * 멤버로 추가한다. 이미 멤버면 아무것도 하지 않음.
     * (room_id, user_id) PK 충돌을 DB가 원자적으로 무시하므로 동시 요청에도 1행만 남음.
     * 충돌 대상을 PK로 한정해서, 닉네임 UNIQUE 충돌은 무시하지 않고 오류로 낸다.
     * @return 새로 추가됐으면 1, 이미 멤버였으면 0
     */
    @Modifying
    @Query(value = "insert into diary_room_member (room_id, user_id, role, nickname, joined_at) "
            + "values (:roomId, :userId, 'MEMBER', :nickname, now()) on conflict (room_id, user_id) do nothing",
            nativeQuery = true)
    int insertMemberIfAbsent(@Param("roomId") Long roomId, @Param("userId") Long userId,
                             @Param("nickname") String nickname);

    /** 방 생성 시 방장을 OWNER로 등록한다. */
    @Modifying
    @Query(value = "insert into diary_room_member (room_id, user_id, role, nickname, joined_at) "
            + "values (:roomId, :userId, 'OWNER', :nickname, now()) on conflict (room_id, user_id) do nothing",
            nativeQuery = true)
    int insertOwner(@Param("roomId") Long roomId, @Param("userId") Long userId, @Param("nickname") String nickname);

    /** 방장 닉네임 변경 (방 수정). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update DiaryRoomMember m set m.nickname = :nickname where m.id.roomId = :roomId and m.id.userId = :userId")
    int updateNickname(@Param("roomId") Long roomId, @Param("userId") Long userId, @Param("nickname") String nickname);

    /** 방 삭제 전 멤버 정리 (DB FK에 ON DELETE CASCADE가 없음). */
    @Modifying(clearAutomatically = true)
    @Query("delete from DiaryRoomMember m where m.id.roomId = :roomId")
    void deleteAllMembersByRoomId(@Param("roomId") Long roomId);

    /** 나가기, 강퇴 둘 다 사용. @return 삭제됐으면 1, 원래 멤버가 아니었으면 0 */
    @Modifying(clearAutomatically = true)
    @Query("delete from DiaryRoomMember m where m.id.roomId = :roomId and m.id.userId = :userId")
    int deleteMember(@Param("roomId") Long roomId, @Param("userId") Long userId);

    /** 회원 탈퇴 시 방장인 방을 찾는다. */
    @Query("select r.id from DiaryRoom r where r.ownerId = :ownerId")
    List<Long> findIdsByOwnerId(@Param("ownerId") Long ownerId);

    /** 회원 탈퇴 시 모든 방에서 나간다 (diary_room_member.user_id는 users FK). */
    @Modifying(clearAutomatically = true)
    @Query("delete from DiaryRoomMember m where m.id.userId = :userId")
    int deleteAllMembershipsByUserId(@Param("userId") Long userId);

    @Query("select count(m) from DiaryRoomMember m where m.id.roomId = :roomId")
    long countMembers(@Param("roomId") Long roomId);
}
