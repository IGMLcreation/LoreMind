package com.loremind.infrastructure.web.dto.conversationcontext;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMessageDTO {
    private String id;
    private String role;
    private String content;
    private LocalDateTime createdAt;
}
