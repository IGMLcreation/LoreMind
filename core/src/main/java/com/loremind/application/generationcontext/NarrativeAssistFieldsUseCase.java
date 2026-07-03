package com.loremind.application.generationcontext;

import com.loremind.domain.campaigncontext.EntityFieldPatchProposal;
import com.loremind.domain.campaigncontext.FieldProposal;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.campaigncontext.ports.NarrativeFieldAssistant;
import com.loremind.domain.gamesystemcontext.ports.GameSystemRepository;
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
    private final CampaignRepository campaignRepository;
    private final GameSystemRepository gameSystemRepository;
    private final NarrativeFieldAssistant assistant;

    public NarrativeAssistFieldsUseCase(
            NarrativeFieldCatalog catalog,
            CampaignRepository campaignRepository,
            GameSystemRepository gameSystemRepository,
            NarrativeFieldAssistant assistant) {
        this.catalog = catalog;
        this.campaignRepository = campaignRepository;
        this.gameSystemRepository = gameSystemRepository;
        this.assistant = assistant;
    }

    public EntityFieldPatchProposal execute(String entityType, String entityId, String campaignId, String instruction) {
        NarrativeFieldCatalog.Snapshot snap = catalog.read(entityType, entityId);

        List<NarrativeFieldAssistant.FieldSpec> specs = snap.defs().stream()
                .map(d -> new NarrativeFieldAssistant.FieldSpec(d.key(), d.label()))
                .collect(Collectors.toList());
        Set<String> allowed = snap.defs().stream()
                .map(NarrativeFieldCatalog.FieldDef::key).collect(Collectors.toSet());

        String context = buildContext(campaignId, snap);
        List<NarrativeFieldAssistant.ProposedField> proposed =
                assistant.assist(snap.entityType(), context, instruction, specs);

        List<FieldProposal> fields = new ArrayList<>();
        for (NarrativeFieldAssistant.ProposedField pf : proposed) {
            if (pf.key() == null || !allowed.contains(pf.key())) continue;
            if (pf.value() == null || pf.value().isBlank()) continue;
            String current = snap.current().getOrDefault(pf.key(), "");
            fields.add(new FieldProposal(pf.key(), current, pf.value()));
        }
        return new EntityFieldPatchProposal(snap.entityType(), entityId, "patch", fields);
    }

    /** Contexte compact : type + titre + valeurs actuelles non vides + méta campagne. */
    private String buildContext(String campaignId, NarrativeFieldCatalog.Snapshot snap) {
        StringBuilder sb = new StringBuilder();
        sb.append(entityLabel(snap.entityType())).append(" : ")
                .append(blankToLabel(snap.title(), "(sans titre)")).append("\n");
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
            campaignRepository.findById(campaignId).ifPresent(c -> {
                sb.append("Campagne : ").append(c.getName());
                if (c.getDescription() != null && !c.getDescription().isBlank()) {
                    sb.append(" — ").append(c.getDescription().trim());
                }
                sb.append("\n");
                if (c.getGameSystemId() != null && !c.getGameSystemId().isBlank()) {
                    gameSystemRepository.findById(c.getGameSystemId())
                            .ifPresent(gs -> sb.append("Système de jeu : ").append(gs.getName()).append("\n"));
                }
            });
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

    private static String blankToLabel(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
