package com.loremind.application.generationcontext;

import com.loremind.application.campaigncontext.CampaignContextFormatter;
import com.loremind.domain.campaigncontext.generation.EntityFieldPatchProposal;
import com.loremind.domain.campaigncontext.generation.FieldProposal;
import com.loremind.domain.campaigncontext.ports.NarrativeFieldAssistant;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Use case (Pilier A — co-création) : produit une PROPOSITION d'étoffage des champs d'une
 * entité narrative (arc / chapitre / scène), non persistée. Générique par {@code entityType}
 * grâce à {@link NarrativeFieldCatalog} (source de vérité des champs) : la même mécanique
 * couvre les trois types, seul le catalogue de champs change.
 *
 * <p>Contexte volontairement COMPACT (entité + méta campagne). Zéro écriture — l'application
 * est un second appel explicite (human-in-the-loop).</p>
 */
@Service
public class NarrativeAssistFieldsUseCase {

    private final NarrativeFieldCatalog catalog;
    private final CampaignContextFormatter campaignContextFormatter;
    private final NarrativeFieldAssistant assistant;

    public NarrativeAssistFieldsUseCase(
            NarrativeFieldCatalog catalog,
            CampaignContextFormatter campaignContextFormatter,
            NarrativeFieldAssistant assistant) {
        this.catalog = catalog;
        this.campaignContextFormatter = campaignContextFormatter;
        this.assistant = assistant;
    }

    public EntityFieldPatchProposal execute(String entityType, String entityId, String campaignId, String instruction) {
        NarrativeFieldCatalog.Snapshot snap = catalog.read(entityType, entityId);

        List<NarrativeFieldAssistant.FieldSpec> specs = snap.defs().stream()
                .map(d -> new NarrativeFieldAssistant.FieldSpec(d.key(), d.label()))
                .toList();
        Set<String> allowed = snap.defs().stream()
                .map(NarrativeFieldCatalog.FieldDef::key).collect(Collectors.toSet());

        String context = buildContext(campaignId, snap);
        List<NarrativeFieldAssistant.ProposedField> proposed =
                assistant.assist(snap.entityType(), context, instruction, specs);

        List<FieldProposal> fields = new ArrayList<>();
        for (NarrativeFieldAssistant.ProposedField pf : proposed) {
            FieldProposal proposal = toFieldProposal(pf, allowed, snap);
            if (proposal != null) fields.add(proposal);
        }
        return new EntityFieldPatchProposal(snap.entityType(), entityId, "patch", fields);
    }

    /** Convertit un champ proposé par l'IA en FieldProposal, ou null si sa clé n'est pas autorisée ou sa valeur vide. */
    private static FieldProposal toFieldProposal(
            NarrativeFieldAssistant.ProposedField pf, Set<String> allowed, NarrativeFieldCatalog.Snapshot snap) {
        if (pf.key() == null || !allowed.contains(pf.key())) return null;
        if (pf.value() == null || pf.value().isBlank()) return null;
        String current = snap.current().getOrDefault(pf.key(), "");
        return new FieldProposal(pf.key(), current, pf.value());
    }

    /** Contexte compact : type + titre + valeurs actuelles non vides + méta campagne. */
    private String buildContext(String campaignId, NarrativeFieldCatalog.Snapshot snap) {
        StringBuilder sb = new StringBuilder();
        sb.append(entityLabel(snap.entityType())).append(" : ")
                .append(blankToLabel(snap.title())).append("\n");
        sb.append("État actuel des champs :\n");
        boolean any = false;
        for (Map.Entry<String, String> e : snap.current().entrySet()) {
            String v = e.getValue();
            if (v != null && !v.isBlank()) {
                sb.append("- ").append(e.getKey()).append(" : ").append(v.trim()).append("\n");
                any = true;
            }
        }
        if (!any) {
            sb.append("- (tous les champs sont vides — à créer de zéro)\n");
        }
        if (campaignId != null && !campaignId.isBlank()) {
            String campaignBlock = campaignContextFormatter.format(campaignId);
            if (!campaignBlock.isBlank()) {
                sb.append(campaignBlock).append("\n");
            }
        }
        return sb.toString();
    }

    private static String entityLabel(String entityType) {
        return switch (entityType == null ? "" : entityType) {
            case "arc" -> "Arc";
            case "chapter" -> "Chapitre";
            case "scene" -> "Scène";
            default -> "Entité";
        };
    }

    private static String blankToLabel(String value) {
        return value == null || value.isBlank() ? "(sans titre)" : value;
    }
}
