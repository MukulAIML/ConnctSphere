package com.connectsphere.likeservice.client;

import com.connectsphere.likeservice.dto.PostSummaryDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

@FeignClient(name = "post-service")
public interface PostServiceClient {
	@PutMapping("/posts/{postId}/like/increment")
	void incrementLikeCount(@PathVariable("postId") int postId);

	@PutMapping("/posts/{postId}/like/decrement")
	void decrementLikeCount(@PathVariable("postId") int postId);

	@GetMapping("/posts/{postId}")
	PostSummaryDTO getPostById(@PathVariable("postId") int postId);
}
