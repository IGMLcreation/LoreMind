package com.loremind.infrastructure.transfer;

import com.loremind.domain.campaigncontext.ArcType;
import com.loremind.domain.campaigncontext.Room;
import com.loremind.infrastructure.persistence.entity.*;
import com.loremind.infrastructure.persistence.jpa.*;
import com.loremind.infrastructure.transfer.dto.ContentExport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Export SÉLECTIF par campagne ({@link ExportService#buildExport(String, ExportRequest)}) :
 * la clôture ne contient QUE la campagne ciblée, et les options lore/jeu/images en règlent
 * le périmètre. Vérifie l'isolation vis-à-vis des autres campagnes et le débrayage des
 * sections optionnelles.
 */
@SpringBootTest
@Transactional
class SelectiveExportTest {

    @Autowired private ExportService exportService;
    @Autowired private LoreJpaRepository loreRepo;
    @Autowired private LoreNodeJpaRepository loreNodeRepo;
    @Autowired private PageJpaRepository pageRepo;
    @Autowired private CampaignJpaRepository campaignRepo;
    @Autowired private ArcJpaRepository arcRepo;
    @Autowired private ChapterJpaRepository chapterRepo;
    @Autowired private SceneJpaRepository sceneRepo;
    @Autowired private NpcJpaRepository npcRepo;
    @Autowired private ImageJpaRepository imageRepo;
    @Autowired private PlaythroughJpaRepository playthroughRepo;
    @Autowired private CharacterJpaRepository characterRepo;

    private Long lastLoreId;

    @Test
    void targetCampaign_scopesToCampaign_andExcludesPlayWhenOff() {
        Long a = seedCampaign("Camp A", true, true);
        seedCampaign("Camp B", true, true); // doit être absente de l'export de A

        ContentExport ex = exportService.buildExport("t", new ExportRequest(a, true, false, true));

        assertEquals("Camp A", ex.manifest().scope());
        // Une seule campagne exportée : A.
        assertEquals(List.of(a), ex.campaigns().stream().map(ContentExport.CampaignDto::id).toList());
        // Arcs/PNJ : uniquement ceux de A.
        assertFalse(ex.arcs().isEmpty());
        assertTrue(ex.arcs().stream().allMatch(arc -> a.equals(arc.campaignId())));
        assertTrue(ex.npcs().stream().allMatch(n -> a.equals(n.campaignId())));
        // Jeu débrayé → ni parties ni feuilles de perso.
        assertTrue(ex.playthroughs().isEmpty());
        assertTrue(ex.characters().isEmpty());
        // Lore inclus → lien conservé.
        assertFalse(ex.lores().isEmpty());
        assertEquals(String.valueOf(lastLoreIdOf(a)), ex.campaigns().get(0).loreId());
    }

    @Test
    void withPlayOn_includesTargetCampaignPlayOnly() {
        Long a = seedCampaign("Camp A", false, true);
        Long b = seedCampaign("Camp B", false, true);

        ContentExport ex = exportService.buildExport("t", new ExportRequest(a, true, true, true));

        assertFalse(ex.playthroughs().isEmpty());
        assertTrue(ex.playthroughs().stream().allMatch(p -> a.equals(p.campaignId())));
        assertTrue(ex.playthroughs().stream().noneMatch(p -> b.equals(p.campaignId())));
        assertFalse(ex.characters().isEmpty());
    }

    @Test
    void withoutLore_nullifiesLinkAndOmitsLore() {
        Long a = seedCampaign("Camp A", true, false);

        ContentExport ex = exportService.buildExport("t", new ExportRequest(a, false, true, true));

        assertTrue(ex.lores().isEmpty());
        assertTrue(ex.pages().isEmpty());
        // Lien lore neutralisé pour éviter une référence pendante.
        assertNull(ex.campaigns().get(0).loreId());
    }

    @Test
    void roomImages_areCollectedInClosure() {
        // Image référencée UNIQUEMENT par une salle (Room) d'une scène (cas oublié initialement).
        ImageJpaEntity img = imageRepo.save(ImageJpaEntity.builder()
                .filename("plan.png").contentType("image/png").sizeBytes(5).storageKey("images/room-plan.png").build());
        CampaignJpaEntity camp = campaignRepo.save(CampaignJpaEntity.builder().name("Camp R").arcsCount(1).build());
        ArcJpaEntity arc = arcRepo.save(ArcJpaEntity.builder()
                .name("Arc").campaignId(camp.getId()).order(0).type(ArcType.LINEAR).build());
        ChapterJpaEntity chap = chapterRepo.save(ChapterJpaEntity.builder()
                .name("Chap").arcId(arc.getId()).order(0).build());
        Room room = Room.builder().id("r1").name("Salle")
                .illustrationImageIds(new ArrayList<>(List.of(String.valueOf(img.getId())))).build();
        sceneRepo.save(SceneJpaEntity.builder().name("Scene").chapterId(chap.getId()).order(0)
                .rooms(new ArrayList<>(List.of(room))).build());

        ContentExport ex = exportService.buildExport("t", new ExportRequest(camp.getId(), true, false, true));

        assertTrue(ex.images().stream().anyMatch(i -> img.getId().equals(i.id())),
                "l'image référencée par une room doit être collectée dans l'export ciblé");
    }

    @Test
    void withoutImages_omitsImageMetadataAndBinaries() {
        ImageJpaEntity img = imageRepo.save(ImageJpaEntity.builder()
                .filename("i.png").contentType("image/png").sizeBytes(5).storageKey("images/no-img.png").build());
        CampaignJpaEntity camp = campaignRepo.save(CampaignJpaEntity.builder().name("Camp NI").arcsCount(1).build());
        arcRepo.save(ArcJpaEntity.builder().name("Arc").campaignId(camp.getId()).order(0).type(ArcType.LINEAR)
                .illustrationImageIds(new ArrayList<>(List.of(String.valueOf(img.getId())))).build());

        ContentExport ex = exportService.buildExport("t", new ExportRequest(camp.getId(), true, true, false));

        assertTrue(ex.images().isEmpty(), "includeImages=false → aucune métadonnée d'image exportée");
        assertTrue(ex.storedFiles().isEmpty());
    }

    // --- Helpers de seed ------------------------------------------------------

    /** Crée une campagne (+ arc + PNJ, + lore optionnel, + partie/perso optionnels) et renvoie son id. */
    private Long seedCampaign(String name, boolean withLore, boolean withPlay) {
        String loreLink = null;
        if (withLore) {
            LoreJpaEntity lore = loreRepo.save(LoreJpaEntity.builder().name(name + " Lore").build());
            lastLoreId = lore.getId();
            LoreNodeJpaEntity node = loreNodeRepo.save(LoreNodeJpaEntity.builder()
                    .name("N").loreId(lore.getId()).order(0).build());
            pageRepo.save(PageJpaEntity.builder()
                    .loreId(lore.getId()).nodeId(node.getId()).title(name + " Page").build());
            loreLink = String.valueOf(lore.getId());
        }
        CampaignJpaEntity camp = campaignRepo.save(CampaignJpaEntity.builder()
                .name(name).loreId(loreLink).arcsCount(1).build());
        camp.setLoreId(loreLink); // au cas où le builder ignorerait null
        campaignRepo.save(camp);
        loreByCampaign.put(camp.getId(), lastLoreId);
        arcRepo.save(ArcJpaEntity.builder()
                .name(name + " Arc").campaignId(camp.getId()).order(0).type(ArcType.LINEAR).build());
        npcRepo.save(NpcJpaEntity.builder()
                .name(name + " Npc").campaignId(camp.getId()).order(0).build());
        if (withPlay) {
            PlaythroughJpaEntity pt = playthroughRepo.save(PlaythroughJpaEntity.builder()
                    .campaignId(camp.getId()).name(name + " PT").build());
            characterRepo.save(CharacterJpaEntity.builder()
                    .name(name + " Hero").campaignId(camp.getId()).playthroughId(pt.getId()).order(0).build());
        }
        return camp.getId();
    }

    private final java.util.Map<Long, Long> loreByCampaign = new java.util.HashMap<>();

    private Long lastLoreIdOf(Long campaignId) {
        return loreByCampaign.get(campaignId);
    }
}
