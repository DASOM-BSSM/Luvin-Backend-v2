package com.luvin.diary.repository;

import com.luvin.diary.domain.DiaryRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiaryRoomRepository extends JpaRepository<DiaryRoom, Long> {
    // DiaryRoomMember 중 id.roomId == roomId AND id.userId == userId 인 행이 있는가?
    @Query("select count(m) > 0 from DiaryRoomMember m where m.id.roomId = :roomId AND m.id.userId = :userId")
    boolean isMember(@Param("roomId") Long roomId, @Param("userId") Long userId);
}
