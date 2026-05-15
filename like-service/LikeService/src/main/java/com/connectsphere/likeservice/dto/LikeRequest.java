package com.connectsphere.likeservice.dto;

import lombok.Data;

@Data
public class LikeRequest {
	private int targetId;
	private String targetType;
	private String reactionType = "LIKE";
}
