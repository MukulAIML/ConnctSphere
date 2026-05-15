package com.connectsphere.likeservice.dto;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ReactionSummaryResponse {
	private int targetId;
	private String targetType;
	private long totalCount;
	// All reactions with their counts, ordered by count descending (e.g. {"LIKE":10,"LOVE":5})
	private Map<String, Long> reactions;
	// Top 3 reaction types by count (for Facebook-style icon display)
	private List<String> topReactions;
}
