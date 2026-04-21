package com.loremind.infrastructure.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * DTO interne de l'Adapter : format JSON envoyé au Brain Python.
 * Package-private : n'existe que pour la couche infrastructure.
 * <p>
 * Le contrat HTTP côté Python utilise snake_case — on le matche ici
 * pour éviter de configurer Jackson globalement (impact sur le reste du projet).
 */
record BrainGeneratePageRequest(@JsonProperty("lore_name") String loreName,
                                @JsonProperty("lore_description") String loreDescription,
                                @JsonProperty("folder_name") String folderName,
                                @JsonProperty("template_name") String templateName,
                                @JsonProperty("template_fields") List<String> templateFields,
                                @JsonProperty("page_title") String pageTitle) {

}
