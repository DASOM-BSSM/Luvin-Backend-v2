package com.luvin.diary.controller;

import com.luvin.diary.service.DiaryCommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/diaries/{diaryId}/comments")
public class DiaryCommentController {

    private final DiaryCommentService diaryCommentService;
}
