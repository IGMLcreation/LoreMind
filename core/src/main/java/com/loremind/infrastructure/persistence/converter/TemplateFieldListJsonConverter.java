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
                TemplateField field = parseField(item);
                if (field != null) result.add(field);
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Erreur deserialisation JSON -> List<TemplateField>", e);
        }
    }

    /** Un element du tableau JSON -> TemplateField, ou null si non convertible (ignore). */
    private static TemplateField parseField(JsonNode item) {
        if (item.isTextual()) {
            return parseLegacyField(item.asText());
        }
        if (item.isObject()) {
            return parseObjectField(item);
        }
        // Autres types de noeuds (nombre, booleen...) : ignores silencieusement.
        return null;
    }

    /**
     * Format legacy : chaine simple, on suppose TEXT par defaut. L'id stable est
     * retro-rempli avec le nom (les valeurs de Page sont deja rangees par nom ->
     * id == name, aucune migration).
     */
    private static TemplateField parseLegacyField(String name) {
        TemplateField legacy = TemplateField.text(name);
        legacy.setId(name);
        return legacy;
    }

    /** Nouveau format : {id?, name, type, layout?, labels?, foundryPath?, pos?}. Null si nom vide. */
    private static TemplateField parseObjectField(JsonNode item) {
        String name = item.path("name").asText(null);
        if (name == null || name.isBlank()) {
            return null;
        }
        FieldType type = parseType(item.path("type").asText("TEXT"));
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
        return TemplateField.builder()
                .id(id)
                .name(name)
                .type(type)
                .layout(parseLayout(item, type))
                .labels(parseLabels(item, type))
                .foundryPath(foundryPath)
                .pos(readPos(item.path("pos")))
                .build();
    }

    /** Type du champ ; fallback TEXT si inconnu (ajoute par une version future). */
    private static FieldType parseType(String typeStr) {
        try {
            return FieldType.valueOf(typeStr);
        } catch (IllegalArgumentException ex) {
            return FieldType.TEXT;
        }
    }

    /** Layout d'un champ IMAGE ; null si absent/inconnu (-> rendu GALLERY par defaut cote UI). */
    private static ImageLayout parseLayout(JsonNode item, FieldType type) {
        if (type != FieldType.IMAGE) {
            return null;
        }
        String layoutStr = item.path("layout").asText(null);
        if (layoutStr == null || layoutStr.isBlank()) {
            return null;
        }
        try {
            return ImageLayout.valueOf(layoutStr);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Libelles d'un champ KEY_VALUE_LIST/TABLE ; null pour les autres types. */
    // S1168 : null est ici SEMANTIQUE ("champ sans libelles"), distinct d'une liste vide —
    // le JSON persiste ecrit "labels":null (compat ascendante) et le front distingue null/[].
    @SuppressWarnings("java:S1168")
    private static List<String> parseLabels(JsonNode item, FieldType type) {
        if (type != FieldType.KEY_VALUE_LIST && type != FieldType.TABLE) {
            return null;
        }
        JsonNode labelsNode = item.path("labels");
        if (!labelsNode.isArray()) {
            return null;
        }
        List<String> labels = new ArrayList<>();
        for (JsonNode label : labelsNode) {
            if (label.isTextual()) labels.add(label.asText());
        }
        return labels;
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
