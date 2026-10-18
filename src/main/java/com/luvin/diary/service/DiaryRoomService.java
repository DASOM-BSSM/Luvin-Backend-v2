package com.luvin.diary.service;

import com.luvin.diary.repository.DiaryRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DiaryRoomService {

    private final DiaryRoomRepository diaryRoomRepository;
}
