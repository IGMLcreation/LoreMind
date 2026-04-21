package com.loremind.infrastructure.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO interne de l'Adapter : format JSON reçu du Brain Python.
 *
 * @Data + @NoArgsConstructor : nécessaire à Jackson pour la désérialisation.
 */
@Data
@NoArgsConstructor
class BrainGeneratePageResponse {

    @JsonProperty("values")
    private Map<String, String> values;
}
