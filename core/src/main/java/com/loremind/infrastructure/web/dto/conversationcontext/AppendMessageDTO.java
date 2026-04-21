package com.loremind.infrastructure.web.dto.conversationcontext;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppendMessageDTO {
    /** "user" | "assistant" | "system". */
    private String role;
    private String content;
}
