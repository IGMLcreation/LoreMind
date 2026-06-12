package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.Arc;
import com.loremind.domain.campaigncontext.ArcType;
import com.loremind.domain.campaigncontext.CampaignImportProgress;
import com.loremind.domain.campaigncontext.CampaignImportProposal;
import com.loremind.domain.campaigncontext.CampaignImportProposal.ArcProposal;
import com.loremind.domain.campaigncontext.CampaignImportProposal.ChapterProposal;
import com.loremind.domain.campaigncontext.CampaignImportProposal.NpcProposal;
import com.loremind.domain.campaigncontext.CampaignImportProposal.RoomProposal;
import com.loremind.domain.campaigncontext.CampaignImportProposal.SceneProposal;
import com.loremind.domain.campaigncontext.Chapter;
import com.loremind.domain.campaigncontext.Room;
import com.loremind.domain.campaigncontext.Scene;
import com.loremind.domain.campaigncontext.ports.CampaignPdfImporter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Service applicatif pour l'import d'un PDF de campagne.
 *
 * <p>Deux temps, conformes au principe « revue avant écriture » :
 *  1. {@link #importStructureStreaming} génère une PROPOSITION d'arbre (rien
 *     n'est persisté), streamée pour l'avancement.
 *  2. {@link #applyStructure} crée réellement les arcs/chapitres/scènes une fois
 *     l'arbre révisé/édité par l'utilisateur.</p>
 */
@Service
public class CampaignImportService {

    private final CampaignPdfImporter campaignPdfImporter;
    private final CampaignService campaignService;
    private final ArcService arcService;
    private final ChapterService chapterService;
    private final SceneService sceneService;
    private final NpcService npcService;

    public CampaignImportService(
            CampaignPdfImporter campaignPdfImporter,
            CampaignService campaignService,
            ArcService arcService,
            ChapterService chapterService,
            SceneService sceneService,
            NpcService npcService) {
        this.campaignPdfImporter = campaignPdfImporter;
        this.campaignService = campaignService;
        this.arcService = arcService;
        this.chapterService = chapterService;
        this.sceneService = sceneService;
        this.npcService = npcService;
    }

    /** Résumé de ce qui a été créé par {@link #applyStructure}. */
    public record ApplyResult(
            int arcsCreated, int chaptersCreated, int scenesCreated, int npcsCreated) {}

    /** Génère la proposition d'arbre (streamée). Ne persiste rien. */
    public void importStructureStreaming(
            byte[] pdfBytes,
            String filename,
            Consumer<CampaignImportProgress> onProgress,
            Runnable onHeartbeat,
            Consumer<String> onStatus,
            Consumer<CampaignImportProposal> onDone,
            Consumer<Throwable> onError) {
        campaignPdfImporter.importCampaignStreaming(
                pdfBytes, filename, onProgress, onHeartbeat, onStatus, onDone, onError);
    }

    /**
     * Crée les arcs/chapitres/scènes de l'arbre révisé dans la campagne. Les
     * arcs sont ajoutés APRÈS les arcs existants (ordre continué). Tout est créé
     * dans une seule transaction (rollback si une étape échoue).
     */
    @Transactional
    public ApplyResult applyStructure(String campaignId, CampaignImportProposal proposal) {
        if (!campaignService.campaignExists(campaignId)) {
            throw new IllegalArgumentException("Campagne introuvable : " + campaignId);
        }

        int arcsCreated = 0, chaptersCreated = 0, scenesCreated = 0;

        // Les nouveaux nœuds sont ordonnés APRÈS les frères existants (déjà comptés
        // via leur existingId dans l'arbre fusionné venu de la revue).
        int arcOrder = countExisting(proposal.arcs(), ArcProposal::existingId);
        for (ArcProposal arcP : proposal.arcs()) {
            if (isBlank(arcP.name())) continue;
            String arcId;
            if (!isBlank(arcP.existingId())) {
                arcId = arcP.existingId();  // arc déjà présent → on s'y rattache
            } else {
                arcOrder++;
                Arc arc = arcService.createArc(Arc.builder()
                        .name(arcP.name().trim())
                        .description(nullIfBlank(arcP.description()))
                        .campaignId(campaignId)
                        .order(arcOrder)
                        .type(parseArcType(arcP.type()))
                        .build());
                arcId = arc.getId();
                arcsCreated++;
            }

            int chapterOrder = countExisting(arcP.chapters(), ChapterProposal::existingId);
            for (ChapterProposal chapP : safe(arcP.chapters())) {
                if (isBlank(chapP.name())) continue;
                String chapId;
                if (!isBlank(chapP.existingId())) {
                    chapId = chapP.existingId();
                } else {
                    chapterOrder++;
                    Chapter chapter = chapterService.createChapter(
                            chapP.name().trim(), nullIfBlank(chapP.description()), arcId, chapterOrder);
                    chapId = chapter.getId();
                    chaptersCreated++;
                }

                int sceneOrder = countExisting(chapP.scenes(), SceneProposal::existingId);
                for (SceneProposal sceneP : safe(chapP.scenes())) {
                    if (isBlank(sceneP.name())) continue;
                    if (!isBlank(sceneP.existingId())) continue;  // scène déjà présente
                    sceneOrder++;
                    sceneService.createScene(Scene.builder()
                            .name(sceneP.name().trim())
                            .description(nullIfBlank(sceneP.description()))
                            .playerNarration(nullIfBlank(sceneP.playerNarration()))
                            .gmSecretNotes(nullIfBlank(sceneP.gmNotes()))
                            .chapterId(chapId)
                            .order(sceneOrder)
                            .rooms(toRooms(sceneP.rooms()))
                            .build());
                    scenesCreated++;
                }
            }
        }

        int npcsCreated = createNpcs(campaignId, proposal.npcs());
        return new ApplyResult(arcsCreated, chaptersCreated, scenesCreated, npcsCreated);
    }

    /**
     * Crée les PNJ proposés (description → values["Description"], même convention
     * que les cartes d'action des ateliers). Les PNJ portant un nom déjà présent
     * dans la campagne sont ignorés (ré-import sans doublon).
     */
    private int createNpcs(String campaignId, List<NpcProposal> proposals) {
        if (proposals == null || proposals.isEmpty()) return 0;
        java.util.Set<String> existingNames = new java.util.HashSet<>();
        for (var npc : npcService.getNpcsByCampaignId(campaignId)) {
            existingNames.add(npc.getName().trim().toLowerCase());
        }
        int created = 0;
        for (NpcProposal p : proposals) {
            if (isBlank(p.name()) || !existingNames.add(p.name().trim().toLowerCase())) {
                continue;
            }
            npcService.createNpc(new NpcService.NpcData(
                    p.name().trim(), null, null,
                    isBlank(p.description())
                            ? java.util.Map.of()
                            : java.util.Map.of("Description", p.description().trim()),
                    null, null, campaignId, null, null, null));
            created++;
        }
        return created;
    }

    /** Compte les nœuds déjà présents (existingId non vide) d'une liste. */
    private static <T> int countExisting(List<T> list, java.util.function.Function<T, String> idOf) {
        if (list == null) return 0;
        int n = 0;
        for (T t : list) {
            if (!isBlank(idOf.apply(t))) n++;
        }
        return n;
    }

    /** "HUB" (insensible à la casse) → {@link ArcType#HUB} ; tout le reste → LINEAR. */
    private static ArcType parseArcType(String type) {
        return "HUB".equalsIgnoreCase(type == null ? "" : type.trim()) ? ArcType.HUB : ArcType.LINEAR;
    }

    /** Convertit les pièces proposées en {@link Room} (ID généré, ordre = index). */
    private static List<Room> toRooms(List<RoomProposal> proposals) {
        List<Room> rooms = new ArrayList<>();
        if (proposals == null) return rooms;
        int order = 0;
        for (RoomProposal r : proposals) {
            if (isBlank(r.name())) continue;
            rooms.add(Room.builder()
                    .id(UUID.randomUUID().toString())
                    .name(r.name().trim())
                    .description(nullIfBlank(r.description()))
                    .enemies(nullIfBlank(r.enemies()))
                    .loot(nullIfBlank(r.loot()))
                    .order(order++)
                    .build());
        }
        return rooms;
    }

    private static <T> java.util.List<T> safe(java.util.List<T> list) {
        return list == null ? java.util.List.of() : list;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String nullIfBlank(String s) {
        return isBlank(s) ? null : s.trim();
    }
}
