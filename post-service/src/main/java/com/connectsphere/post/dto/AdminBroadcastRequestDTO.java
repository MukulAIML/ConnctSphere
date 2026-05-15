package com.connectsphere.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminBroadcastRequestDTO {

    public enum RecipientScope {
        ALL,
        TARGETED
    }

    @Builder.Default
    private RecipientScope recipientScope = RecipientScope.ALL;

    private List<Long> recipientIds;

    @NotBlank(message = "Message is required")
    @Size(max = 500, message = "Message must be at most 500 characters")
    private String message;
}
