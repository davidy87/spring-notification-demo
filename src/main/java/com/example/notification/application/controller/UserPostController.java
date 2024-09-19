package com.example.notification.application.controller;

import com.example.notification.application.dto.CommentRequestDto;
import com.example.notification.application.dto.PostUploadRequestDto;
import com.example.notification.application.service.UserPostService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/api/posts")
@RequiredArgsConstructor
@RestController
public class UserPostController {

    private final UserPostService userPostService;

    @PostMapping
    public Long uploadPost(@RequestBody PostUploadRequestDto requestDto) {
        return userPostService.uploadPost(requestDto);
    }

    @PostMapping("/{postId}/comments")
    public String leaveComment(@PathVariable Long postId, @RequestBody CommentRequestDto requestDto) {
        return userPostService.leaveComment(postId, requestDto);
    }
}
