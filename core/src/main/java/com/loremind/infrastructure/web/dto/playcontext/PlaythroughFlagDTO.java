package com.loremind.infrastructure.web.dto.playcontext;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlaythroughFlagDTO {
    private String name;
    private boolean value;
}
