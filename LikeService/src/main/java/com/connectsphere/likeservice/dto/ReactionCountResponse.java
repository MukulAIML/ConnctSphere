package com.connectsphere.likeservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ReactionCountResponse {
	private String reactionType;
	private Long count;
}