package com.loremind.infrastructure.persistence.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.shared.template.BlockPosition;
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
                    // L'id stable est retro-rempli avec le nom (les valeurs de Page
                    // sont deja rangees par nom -> id == name, aucune migration).
                    String name = item.asText();
                    TemplateField legacy = TemplateField.text(name);
                    legacy.setId(name);
                    result.add(legacy);
                } else if (item.isObject()) {
                    // Nouveau format : {id?, name, type, layout?, labels?, foundryPath?, pos?}
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
                    List<String> labels = null;
                    if (type == FieldType.KEY_VALUE_LIST || type == FieldType.TABLE) {
                        JsonNode labelsNode = item.path("labels");
                        if (labelsNode.isArray()) {
                            labels = new ArrayList<>();
                            for (JsonNode label : labelsNode) {
                                if (label.isTextual()) labels.add(label.asText());
                            }
                        }
                    }
                    // foundryPath : lu via hasNonNull pour eviter le piege NullNode
                    // (asText() renverrait la chaine "null"). Historiquement omis a la
                    // relecture -> il etait perdu au save+reload : corrige ici.
                    String foundryPath = item.hasNonNull("foundryPath")
                            ? item.get("foundryPath").asText() : null;
                    // id : explicite si present, sinon retro-rempli avec le nom.
                    String id = item.hasNonNull("id") ? item.get("id").asText() : null;
                    if (id == null || id.isBlank()) {
                        id = name;
                    }
                    BlockPosition pos = readPos(item.path("pos"));
                    if (name != null && !name.isBlank()) {
                        result.add(TemplateField.builder()
                                .id(id)
                                .name(name)
                                .type(type)
                                .layout(layout)
                                .labels(labels)
                                .foundryPath(foundryPath)
                                .pos(pos)
                                .build());
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

    /**
     * Lit le placement {@code pos: {x, y, w, h}} d'un bloc. Renvoie null si le
     * noeud n'est pas un objet ou si toutes les coordonnees sont absentes
     * (-> auto-flow empile, comportement historique).
     */
    private static BlockPosition readPos(JsonNode posNode) {
        if (posNode == null || !posNode.isObject()) {
            return null;
        }
        Integer x = intOrNull(posNode, "x");
        Integer y = intOrNull(posNode, "y");
        Integer w = intOrNull(posNode, "w");
        Integer h = intOrNull(posNode, "h");
        if (x == null && y == null && w == null && h == null) {
            return null;
        }
        return new BlockPosition(x, y, w, h);
    }

    /** Lit un entier optionnel d'un noeud JSON ; null si absent ou non numerique. */
    private static Integer intOrNull(JsonNode node, String field) {
        JsonNode n = node.path(field);
        return n.isNumber() ? n.asInt() : null;
    }

    // typeRef garde pour reference future si on veut deserialiser directement.
    @SuppressWarnings("unused")
    private static final TypeReference<List<TemplateField>> TYPE_REF =
            new TypeReference<>() {};
}
