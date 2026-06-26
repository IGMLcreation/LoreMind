package com.loremind.infrastructure.web.mapper;

import com.loremind.domain.shared.template.BlockPosition;
import com.loremind.domain.shared.template.FieldType;
import com.loremind.domain.shared.template.ImageLayout;
import com.loremind.domain.shared.template.TemplateField;
import com.loremind.infrastructure.web.dto.shared.BlockPositionDTO;
import com.loremind.infrastructure.web.dto.shared.TemplateFieldDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Mapper pour convertir entre {@link TemplateField} (domaine) et
 * {@link TemplateFieldDTO} (wire).
 * <p>
 * Tolerance : un type inconnu recu du client est interprete comme TEXT.
 * Un layout inconnu ou absent sur un champ IMAGE est interprete comme GALLERY.
 * Layout/labels forces a null pour les types qui ne les utilisent pas.
 */
@Component
public class TemplateFieldMapper {

    public TemplateFieldDTO toDTO(TemplateField field) {
        if (field == null) return null;
        String typeStr = field.getType() != null ? field.getType().name() : FieldType.TEXT.name();
        String layoutStr = null;
        if (field.getType() == FieldType.IMAGE) {
            ImageLayout layout = field.getLayout() != null ? field.getLayout() : ImageLayout.GALLERY;
            layoutStr = layout.name();
        }
        List<String> labels = null;
        if ((field.getType() == FieldType.KEY_VALUE_LIST || field.getType() == FieldType.TABLE)
                && field.getLabels() != null) {
            labels = new ArrayList<>(field.getLabels());
        }
        TemplateFieldDTO dto = new TemplateFieldDTO(
                field.getName(), typeStr, layoutStr, labels, field.getFoundryPath());
        dto.setId(resolveId(field.getId(), field.getName()));
        dto.setPos(toPosDTO(field.getPos()));
        return dto;
    }

    public TemplateField toDomain(TemplateFieldDTO dto) {
        if (dto == null) return null;
        FieldType type;
        try {
            type = dto.getType() != null ? FieldType.valueOf(dto.getType()) : FieldType.TEXT;
        } catch (IllegalArgumentException ex) {
            type = FieldType.TEXT;
        }
        ImageLayout layout = null;
        if (type == FieldType.IMAGE) {
            try {
                layout = dto.getLayout() != null
                        ? ImageLayout.valueOf(dto.getLayout())
                        : ImageLayout.GALLERY;
            } catch (IllegalArgumentException ex) {
                layout = ImageLayout.GALLERY;
            }
        }
        List<String> labels = null;
        if ((type == FieldType.KEY_VALUE_LIST || type == FieldType.TABLE) && dto.getLabels() != null) {
            labels = new ArrayList<>(dto.getLabels());
        }
        return TemplateField.builder()
                .id(resolveId(dto.getId(), dto.getName()))
                .name(dto.getName())
                .type(type)
                .layout(layout)
                .labels(labels)
                .foundryPath(dto.getFoundryPath())
                .pos(toPosDomain(dto.getPos()))
                .build();
    }

    /**
     * Renvoie l'id du bloc, retro-rempli avec le nom quand il est absent.
     * Garantit une cle d'ancrage stable meme pour les clients qui n'envoient
     * pas encore d'id (front anterieur a la grille).
     */
    private String resolveId(String id, String name) {
        return id != null && !id.isBlank() ? id : name;
    }

    private BlockPositionDTO toPosDTO(BlockPosition pos) {
        if (pos == null) return null;
        return new BlockPositionDTO(pos.getX(), pos.getY(), pos.getW(), pos.getH());
    }

    private BlockPosition toPosDomain(BlockPositionDTO pos) {
        if (pos == null) return null;
        return new BlockPosition(pos.getX(), pos.getY(), pos.getW(), pos.getH());
    }

    /** Mappe une liste de champs domaine → DTO ({@code null} → liste vide). */
    public List<TemplateFieldDTO> toDTOList(List<TemplateField> fields) {
        if (fields == null) return new ArrayList<>();
        List<TemplateFieldDTO> out = new ArrayList<>(fields.size());
        for (TemplateField f : fields) out.add(toDTO(f));
        return out;
    }

    /** Mappe une liste de champs DTO → domaine ({@code null} → liste vide). */
    public List<TemplateField> toDomainList(List<TemplateFieldDTO> dtos) {
        if (dtos == null) return new ArrayList<>();
        List<TemplateField> out = new ArrayList<>(dtos.size());
        for (TemplateFieldDTO d : dtos) out.add(toDomain(d));
        return out;
    }
}
