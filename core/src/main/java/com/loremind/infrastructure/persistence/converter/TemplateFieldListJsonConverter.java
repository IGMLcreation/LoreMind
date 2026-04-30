package com.loremind.infrastructure.persistence.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.shared.template.FieldType;
import com.loremind.domain.shared.template.ImageLayout;
import com.loremind.domain.shared.template.TemplateField;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Convertisseur JPA pour {@code List<TemplateField>}.
 *
 * <h3>Backward compatibility (CRITIQUE)</h3>
 * Les templates crees avant l'introduction de {@link TemplateField} sont
 * persistes au format legacy : {@code ["Nom", "Histoire", "Portrait"]}.
 * Les nouveaux templates utilisent le format : {@code [{"name":"Nom","type":"TEXT"}, ...]}.
 * <p>
 * Ce converter sait lire les DEUX formats en lecture (tolerant) mais ecrit
 * toujours au nouveau format. Cela evite une migration de donnees risquee :
 * la premiere ecriture d'un template legacy suffit a le convertir.
 *
 * <h3>Responsabilite</h3>
 * Adaptateur technique pur : le domaine ne connait jamais ce converter.
 */
@Converter
public class TemplateFieldListJsonConverter
        implements AttributeConverter<List<TemplateField>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<TemplateField> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "[]";
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Erreur serialisation List<TemplateField> -> JSON", e);
        }
    }

    @Override
    public List<TemplateField> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return Collections.emptyList();
        }
        try {
            JsonNode root = MAPPER.readTree(dbData);
            if (!root.isArray()) {
                return Collections.emptyList();
            }
            List<TemplateField> result = new ArrayList<>();
            for (JsonNode item : root) {
                if (item.isTextual()) {
                    // Format legacy : chaine simple, on suppose TEXT par defaut.
                    result.add(TemplateField.text(item.asText()));
                } else if (item.isObject()) {
                    // Nouveau format : {name, type}
                    String name = item.path("name").asText(null);
                    String typeStr = item.path("type").asText("TEXT");
                    FieldType type;
                    try {
                        type = FieldType.valueOf(typeStr);
                    } catch (IllegalArgumentException ex) {
                        // Type inconnu (ajoute par une version future) : fallback TEXT.
                        type = FieldType.TEXT;
                    }
                    ImageLayout layout = null;
                    if (type == FieldType.IMAGE) {
                        String layoutStr = item.path("layout").asText(null);
                        if (layoutStr != null && !layoutStr.isBlank()) {
                            try {
                                layout = ImageLayout.valueOf(layoutStr);
                            } catch (IllegalArgumentException ex) {
                                // Layout inconnu : on laisse null → rendu GALLERY par defaut cote UI.
                                layout = null;
                            }
                        }
                    }
                    if (name != null && !name.isBlank()) {
                        result.add(new TemplateField(name, type, layout));
                    }
                }
                // Autres types de noeuds (nombre, booleen...) : ignores silencieusement.
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Erreur deserialisation JSON -> List<TemplateField>", e);
        }
    }

    // typeRef garde pour reference future si on veut deserialiser directement.
    @SuppressWarnings("unused")
    private static final TypeReference<List<TemplateField>> TYPE_REF =
            new TypeReference<>() {};
}
