package com.connectsphere.commentservice.client;

import com.connectsphere.commentservice.config.FeignAuthForwardingConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "post-service", configuration = FeignAuthForwardingConfig.class)
public interface PostServiceClient {
	@org.springframework.web.bind.annotation.PutMapping("/posts/{postId}/comment/increment")
	void incrementCommentCount(@PathVariable("postId") int postId);
	
	@org.springframework.web.bind.annotation.PutMapping("/posts/{postId}/comment/decrement")
	void decrementCommentCount(@PathVariable("postId") int postId);

	@org.springframework.web.bind.annotation.GetMapping("/posts/{postId}")
	com.connectsphere.commentservice.dto.PostResponseDTO getPostById(@PathVariable("postId") int postId);
}
