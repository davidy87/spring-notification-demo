package com.example.notification.application.service;

import com.example.notification.application.dto.CommentRequestDto;
import com.example.notification.application.dto.PostUploadRequestDto;
import com.example.notification.application.entity.UserPost;
import com.example.notification.application.repository.UserPostRepository;
import com.example.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class UserPostService {

    private final UserPostRepository userPostRepository;

    private final NotificationService notificationService;

    public Long uploadPost(PostUploadRequestDto requestDto) {
        String username = requestDto.getUsername();
        String content = requestDto.getContent();
        UserPost userPost = userPostRepository.save(new UserPost(username, content));

        return userPost.getId();
    }

    public String leaveComment(Long postId, CommentRequestDto requestDto) {
        UserPost userPost = userPostRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found."));

        String notification = String.format("User %s left a comment on %s's post. Comment: %s",
                requestDto.getUsername(), userPost.getOwner(), requestDto.getComment());

        notificationService.notifyEvent(userPost.getOwner(), notification);

        return "success";
    }
}
