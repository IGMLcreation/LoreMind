package com.loremind.infrastructure.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO interne de l'Adapter : JSON reçu du Brain sur POST /import/rules.
 *
 * @Data + @NoArgsConstructor : requis par Jackson pour la désérialisation.
 */
@Data
@NoArgsConstructor
class BrainRulesImportResponse {

    @JsonProperty("sections")
    private Map<String, String> sections;

    @JsonProperty("page_count")
    private int pageCount;

    @JsonProperty("ocr_page_count")
    private int ocrPageCount;

    /** Règles en deux langues : {code langue → sections} (absent d'un Brain antérieur). */
    @JsonProperty("sections_by_language")
    private Map<String, Map<String, String>> sectionsByLanguage;

    @JsonProperty("source_language")
    private String sourceLanguage;
}
