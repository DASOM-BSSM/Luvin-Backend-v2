package com.luvin.diary.controller;

import com.luvin.diary.service.DiaryRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/diary-rooms")
public class DiaryRoomController {

    private final DiaryRoomService diaryRoomService;
}
