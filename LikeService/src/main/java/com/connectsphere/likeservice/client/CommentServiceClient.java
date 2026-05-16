package com.connectsphere.likeservice.client;

import com.connectsphere.likeservice.dto.CommentSummaryDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

@FeignClient(name = "comment-service")
public interface CommentServiceClient {
	@PutMapping("/comments/{commentId}/like/increment")
	void incrementCommentCount(@PathVariable("commentId") int commentId);

	@PutMapping("/comments/{commentId}/like/decrement")
	void decrementCommentCount(@PathVariable("commentId") int commentId);

	@GetMapping("/comments/{commentId}")
	CommentSummaryDTO getCommentById(@PathVariable("commentId") int commentId);
}
