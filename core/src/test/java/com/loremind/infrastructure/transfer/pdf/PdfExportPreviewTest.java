package com.loremind.infrastructure.transfer.pdf;

import com.loremind.domain.campaigncontext.structure.ArcType;
import com.loremind.domain.campaigncontext.structure.LinkType;
import com.loremind.domain.campaigncontext.quest.NodeType;
import com.loremind.domain.campaigncontext.quest.Prerequisite;
import com.loremind.domain.campaigncontext.quest.QuestNodeRef;
import com.loremind.domain.campaigncontext.structure.Room;
import com.loremind.domain.campaigncontext.structure.RoomBranch;
import com.loremind.domain.campaigncontext.structure.SceneBattlemap;
import com.loremind.domain.campaigncontext.structure.SceneBranch;
import com.loremind.domain.files.ports.FileStorage;
import com.loremind.domain.images.ports.ImageStorage;
import com.loremind.domain.shared.template.TemplateField;
import com.loremind.infrastructure.persistence.entity.*;
import com.loremind.infrastructure.persistence.jpa.*;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Genere un livret PDF de DEMONSTRATION avec des donnees riches et representatives
 * (arcs, chapitres, scenes, quetes, PNJ, bestiaire, lore, images placeholder) SANS
 * Spring ni base : tout est mocke Mockito. Le PDF est ecrit dans
 * {@code target/campaign-preview.pdf} pour inspection visuelle de la mise en page.
 */
class PdfExportPreviewTest {

    private static final String LOREM = """
            Le vent du nord charrie des cendres depuis trois jours. Les habitants de Valfroide \
            murmurent que la Tour de Jais s'est rallumée, et que son maître, qu'on croyait mort \
            à la bataille des Gués, aurait passé un pacte avec les Profondeurs.
            Les héros arrivent alors que la foire d'automne bat son plein : un contraste saisissant \
            entre les lampions colorés et la peur qui se lit sur les visages dès qu'on prononce \
            le nom de la tour.""";

    private static final String LONG_NOTES = """
            Points importants à garder en tête :
            - Maëlis ment sur son passé (elle était scribe de la Tour).
            - Le médaillon des PJ réagit à proximité des sceaux — décrire une chaleur diffuse.
            - Si les joueurs interrogent le forgeron, il oriente vers la crypte SANS parler du pacte.
            Ne pas révéler le vrai nom du Maître de Jais avant l'acte III ; toute divination \
            renvoie une image brouillée et un goût de cendre.""";

    @Test
    void dumpPreviewPdf() throws Exception {
        var campaignRepo = mock(CampaignJpaRepository.class);
        var arcRepo = mock(ArcJpaRepository.class);
        var chapterRepo = mock(ChapterJpaRepository.class);
        var sceneRepo = mock(SceneJpaRepository.class);
        var questRepo = mock(QuestJpaRepository.class);
        var npcRepo = mock(NpcJpaRepository.class);
        var enemyRepo = mock(EnemyJpaRepository.class);
        var randomTableRepo = mock(RandomTableJpaRepository.class);
        var gameSystemRepo = mock(GameSystemJpaRepository.class);
        var imageRepo = mock(ImageJpaRepository.class);
        var storedFileRepo = mock(StoredFileJpaRepository.class);
        var loreNodeRepo = mock(LoreNodeJpaRepository.class);
        var pageRepo = mock(PageJpaRepository.class);
        var templateRepo = mock(TemplateJpaRepository.class);
        var imageStorage = mock(ImageStorage.class);
        var fileStorage = mock(FileStorage.class);

        // --- Campagne -------------------------------------------------------
        when(campaignRepo.findById(1L)).thenReturn(Optional.of(CampaignJpaEntity.builder()
                .id(1L).name("Les Cendres de la Tour de Jais")
                .description("Une campagne d'enquête et d'exploration pour 4 à 5 joueurs, du niveau 3 au niveau 8.\n"
                        + "Les héros devront démêler les fils d'un pacte ancien avant que la Tour ne s'éveille tout à fait.")
                .gameSystemId("10").loreId("20").arcsCount(2).build()));

        // --- Structure narrative : 2 arcs -> chapitres -> scenes -------------
        when(arcRepo.findByCampaignId(1L)).thenReturn(List.of(
                ArcJpaEntity.builder().id(100L).campaignId(1L).order(0).name("Acte I — Les braises de Valfroide")
                        .description(LOREM)
                        .themes("Enquête, faux-semblants, communauté sous tension.")
                        .stakes("Découvrir qui ravive les sceaux de la Tour avant la nuit des Cendres.")
                        .rewards("Le médaillon du Guet (500 po), la confiance du conseil de Valfroide.")
                        .resolution("L'acte se clôt quand les PJ identifient le rôle de Maëlis, quelle que soit leur décision à son égard.")
                        .gmNotes(LONG_NOTES)
                        .illustrationImageIds(List.of("400"))
                        .build(),
                ArcJpaEntity.builder().id(101L).campaignId(1L).order(1).name("Acte II — Sous les Gués")
                        .description("Les indices convergent vers les ruines noyées de l'ancien champ de bataille.")
                        .stakes("Récupérer le troisième sceau avant les émissaires des Profondeurs.")
                        .build(),
                // Arc technique SYSTEM (« Quêtes libres ») : conteneurs des quêtes hors arc.
                // Doit etre MASQUE de la narration du livret (comme dans l'arbre UI).
                ArcJpaEntity.builder().id(199L).campaignId(1L).order(9999).name("Quêtes libres")
                        .type(ArcType.SYSTEM).build()));

        when(chapterRepo.findByArcId(100L)).thenReturn(List.of(
                ChapterJpaEntity.builder().id(200L).arcId(100L).order(0).name("La foire d'automne")
                        .description("Arrivée à Valfroide en pleine foire ; premiers contacts, premières fausses notes.")
                        .playerObjectives("Rencontrer le conseil ; retrouver la trace du colporteur disparu.")
                        .narrativeStakes("Gagner (ou perdre) la confiance des habitants — cela pèsera sur tout l'acte.")
                        .gmNotes("Jouer la foire comme un moment léger : le contraste rendra la suite plus sombre.")
                        .build(),
                ChapterJpaEntity.builder().id(201L).arcId(100L).order(1).name("La crypte du Guet")
                        .description("Sous la chapelle, la crypte scellée que le forgeron refuse d'évoquer.")
                        .playerObjectives("Ouvrir la crypte ; comprendre la nature des sceaux.")
                        .build()));
        when(chapterRepo.findByArcId(101L)).thenReturn(List.of(
                ChapterJpaEntity.builder().id(202L).arcId(101L).order(0).name("Les ruines noyées")
                        .description("Exploration des Gués engloutis, entre brume et souvenirs de bataille.")
                        .build(),
                // Chapitre-conteneur JUMEAU de la quête de hub « Les trois sceaux » (même nom,
                // description vide) : le livret doit le rendre comme « Quête » avec les champs
                // de la quête fusionnés — et NE PAS répéter la quête dans la partie « Quêtes ».
                ChapterJpaEntity.builder().id(203L).arcId(101L).order(1).name("Les trois sceaux")
                        .description("").build()));
        // Conteneur d'une quête LIBRE, hébergé dans l'arc SYSTEM.
        when(chapterRepo.findByArcId(199L)).thenReturn(List.of(
                ChapterJpaEntity.builder().id(250L).arcId(199L).order(0)
                        .name("Chasse au trésor du vieux moulin").description("").build()));

        when(sceneRepo.findByChapterId(200L)).thenReturn(List.of(
                SceneJpaEntity.builder().id(300L).chapterId(200L).order(0).name("L'auberge du Chaudron Fêlé")
                        .location("Grande salle de l'auberge, Valfroide.")
                        .timing("Soirée du premier jour, pendant la foire.")
                        .atmosphere("Chaleureuse en surface : feux, musique, odeur de cidre chaud. Mais les conversations s'arrêtent quand on parle de la Tour.")
                        .playerNarration("La porte s'ouvre sur une vague de chaleur et de rires. Une serveuse vous fait signe : « Installez-vous, on ne refuse personne la semaine de la foire ! »")
                        .gmSecretNotes("Maëlis observe les PJ depuis l'alcôve nord. Si un PJ la remarque (Perception DD 15), elle lève son verre sans se cacher.")
                        .choicesConsequences("Si les PJ paient une tournée : +1 aux tests sociaux à Valfroide.\nS'ils mentionnent la Tour à voix haute : la salle se vide en dix minutes.")
                        .branches(List.of(
                                SceneBranch.of("Enquêter au stand dès le matin", "301"),
                                new SceneBranch("Suivre la silhouette aperçue dans l'alcôve", "302",
                                        "si un PJ a repéré Maëlis", LinkType.CLUE)))
                        .build(),
                SceneJpaEntity.builder().id(301L).chapterId(200L).order(1).name("Le stand du colporteur")
                        .location("Allée marchande, place du Guet.")
                        .atmosphere("Le stand est intact mais abandonné ; la marchandise n'a pas été pillée, ce qui inquiète plus que tout.")
                        .gmSecretNotes("Sous le comptoir : un carnet dont trois pages ont été arrachées, imprégnées d'odeur de cendre.")
                        .build(),
                SceneJpaEntity.builder().id(302L).chapterId(200L).order(2).name("Embuscade des masques de suie")
                        .location("Ruelle derrière la halle aux grains.")
                        .timing("Nuit, après la fermeture de la foire.")
                        .playerNarration("Trois silhouettes se détachent des ombres, visages couverts de suie. Celle du centre tient votre carnet volé.")
                        .combatDifficulty("Rencontre moyenne : 3 sbires (masques de suie) + 1 sergent. Les sbires fuient si le sergent tombe.")
                        .enemyIds(List.of("850"))
                        .enemies("Le sergent : profil du sbire avec +5 PV et une rapière (+1 aux dégâts).")
                        .battlemaps(List.of(new SceneBattlemap("Nuit", "500", null),
                                new SceneBattlemap("Jour", "501", null)))
                        .build()));
        when(sceneRepo.findByChapterId(201L)).thenReturn(List.of(
                SceneJpaEntity.builder().id(303L).chapterId(201L).order(0).name("Le seuil scellé")
                        .location("Crypte sous la chapelle du Guet.")
                        .atmosphere("Froid minéral ; les torches brûlent bleu à l'approche du sceau.")
                        .gmSecretNotes("Le sceau se brise si on prononce le nom du colporteur — indice dans son carnet.")
                        // Scene explorable : deux pieces reliees (exerce le rendu des Rooms).
                        .rooms(List.of(
                                Room.builder().id("r1").name("Antichambre du Guet").order(0)
                                        .description("Bancs de pierre renversés ; des traces de suie récentes mènent au mur nord.")
                                        .traps("Dalle piégée devant la porte nord (DD 13, dard empoisonné).")
                                        .branches(List.of(RoomBranch.of("Porte nord descellée", "r2")))
                                        .build(),
                                Room.builder().id("r2").name("Salle du premier sceau").order(1).floor(-1)
                                        .description("Une rotonde basse ; le sceau pulse d'une lueur froide au centre.")
                                        .enemyIds(List.of("851"))
                                        .loot("Le fragment du premier sceau (objet de quête) ; 120 po en offrandes anciennes.")
                                        .gmNotes("Si le sceau est brisé ICI, la Sentinelle se reforme chaque nuit.")
                                        .branches(List.of(new RoomBranch("Remontée par l'éboulis", "r1",
                                                "si les PJ ont déclenché l'alarme")))
                                        .build()))
                        .build()));
        when(sceneRepo.findByChapterId(202L)).thenReturn(List.of(
                SceneJpaEntity.builder().id(304L).chapterId(202L).order(0).name("La brume des Gués")
                        .location("Marais des Gués, ancien champ de bataille.")
                        .atmosphere("Brume à hauteur de poitrine ; les sons portent mal, les distances trompent.")
                        .build()));
        when(sceneRepo.findByChapterId(203L)).thenReturn(List.of(
                SceneJpaEntity.builder().id(305L).chapterId(203L).order(0).name("Le premier sceau")
                        .location("Salle des archives englouties.")
                        .gmSecretNotes("Le sceau porte le sceau personnel de Maëlis — indice majeur.")
                        .build()));
        when(sceneRepo.findByChapterId(250L)).thenReturn(List.of(
                SceneJpaEntity.builder().id(350L).chapterId(250L).order(0).name("Le moulin abandonné")
                        .location("Vieux moulin, une lieue au nord de Valfroide.")
                        .atmosphere("Poussière de farine, toiles d'araignée, et un coffre trop propre pour le décor.")
                        .build()));

        // --- Quêtes -----------------------------------------------------------
        when(questRepo.findByCampaignId(1L)).thenReturn(List.of(
                QuestJpaEntity.builder().id(900L).campaignId(1L).order(0).name("Retrouver le colporteur")
                        .description("Bertoul le colporteur a disparu la veille de la foire en laissant tout son stock.")
                        .playerObjectives("Suivre sa trace ; récupérer son carnet ; découvrir ce qu'il a vu à la Tour.")
                        .narrativeStakes("Bertoul est le seul témoin vivant du rallumage des sceaux.")
                        .gmNotes("S'il est sauvé, Bertoul devient un allié récurrent (et une cible).")
                        .nodes(List.of(new QuestNodeRef(NodeType.SCENE, "301", 0),
                                new QuestNodeRef(NodeType.SCENE, "302", 1),
                                new QuestNodeRef(NodeType.CHAPTER, "201", 2)))
                        .build(),
                // Quête de HUB (arcId=101, conteneur jumeau 203) : fusionnée dans la narration.
                QuestJpaEntity.builder().id(901L).campaignId(1L).order(1).name("Les trois sceaux")
                        .arcId(101L)
                        .description("Trois sceaux maintiennent la Tour endormie ; deux faiblissent déjà.")
                        .prerequisites(List.of(new Prerequisite.QuestCompleted("900"),
                                new Prerequisite.SessionReached(3),
                                new Prerequisite.FlagSet("crypte_ouverte")))
                        .nodes(List.of(new QuestNodeRef(NodeType.CHAPTER, "203", 0),
                                new QuestNodeRef(NodeType.SCENE, "304", 1)))
                        .playerObjectives("Localiser et renforcer (ou briser) les trois sceaux.")
                        .build(),
                // Quête LIBRE (conteneur 250 en arc SYSTEM) : rendue dans la partie « Quêtes »
                // AVEC les scènes de son conteneur (sinon elles disparaîtraient du livret).
                QuestJpaEntity.builder().id(902L).campaignId(1L).order(2).name("Chasse au trésor du vieux moulin")
                        .description("Une carte au trésor achetée à la foire mène au vieux moulin. Trop beau pour être honnête.")
                        .nodes(List.of(new QuestNodeRef(NodeType.CHAPTER, "250", 0)))
                        .playerObjectives("Suivre la carte ; découvrir qui l'a mise en circulation.")
                        .gmNotes("La carte est un appât des masques de suie pour jauger les PJ.")
                        .build()));

        // --- Systeme de jeu : templates PNJ / ennemis ------------------------
        when(gameSystemRepo.findById(10L)).thenReturn(Optional.of(GameSystemJpaEntity.builder()
                .id(10L).name("D&D 5e (maison)")
                .npcTemplate(List.of(
                        TemplateField.text("Apparence"),
                        TemplateField.text("Personnalité"),
                        TemplateField.text("Objectifs"),
                        TemplateField.text("Secrets"),
                        TemplateField.keyValueList("Caractéristiques", List.of("FOR", "DEX", "CON", "INT", "SAG", "CHA"))))
                .enemyTemplate(List.of(
                        TemplateField.text("Description"),
                        TemplateField.text("Tactique"),
                        TemplateField.keyValueList("Caractéristiques", List.of("FOR", "DEX", "CON", "INT", "SAG", "CHA")),
                        TemplateField.table("Attaques", List.of("Nom", "Bonus", "Dégâts", "Portée"))))
                .build()));

        // --- PNJ --------------------------------------------------------------
        when(npcRepo.findByCampaignIdOrderByOrderAsc(1L)).thenReturn(List.of(
                NpcJpaEntity.builder().id(800L).campaignId(1L).order(0).name("Maëlis la Discrète")
                        .folder("Valfroide").portraitImageId("410")
                        .values(Map.of(
                                "Apparence", "Quarantaine élégante, cheveux gris coupés court, toujours une écharpe couleur cendre.",
                                "Personnalité", "Posée, observatrice, répond aux questions par des questions.",
                                "Objectifs", "Empêcher le réveil de la Tour sans révéler son propre rôle passé.",
                                "Secrets", "Ancienne scribe de la Tour de Jais ; c'est elle qui a rédigé le pacte."))
                        .keyValueValues(Map.of("Caractéristiques", Map.of(
                                "FOR", "9", "DEX", "12", "CON", "10", "INT", "17", "SAG", "15", "CHA", "14")))
                        .build(),
                NpcJpaEntity.builder().id(801L).campaignId(1L).order(1).name("Bertoul le colporteur")
                        .folder("Valfroide").portraitImageId("411")
                        .values(Map.of(
                                "Apparence", "Petit homme rond au sourire édenté, mains toujours en mouvement.",
                                "Personnalité", "Bavard, superstitieux, incapable de garder un secret plus d'une journée.",
                                "Objectifs", "Survivre. Éventuellement vendre quelque chose au passage."))
                        .build(),
                NpcJpaEntity.builder().id(802L).campaignId(1L).order(0).name("Le Maître de Jais")
                        .folder("Antagonistes")
                        .values(Map.of(
                                "Apparence", "Nul ne l'a vu depuis la bataille ; les rares témoignages décrivent une silhouette sans ombre.",
                                "Secrets", "Son vrai nom est la clé du troisième sceau."))
                        .build()));

        // --- Bestiaire ----------------------------------------------------------
        when(enemyRepo.findByCampaignIdOrderByOrderAsc(1L)).thenReturn(List.of(
                EnemyJpaEntity.builder().id(850L).campaignId(1L).order(0).name("Sbire des masques de suie")
                        .folder("Humanoïdes").level("1/2").portraitImageId("412")
                        .values(Map.of(
                                "Description", "Hommes de main recrutés dans les faubourgs, visage couvert de suie rituelle.",
                                "Tactique", "Attaquent en meute, fuient dès que le combat tourne mal."))
                        .keyValueValues(Map.of("Caractéristiques", Map.of(
                                "FOR", "12", "DEX", "14", "CON", "11", "INT", "9", "SAG", "10", "CHA", "8")))
                        .foundryStats(Map.of(
                                "attributes.hp.value", "11",
                                "attributes.hp.max", "11",
                                "attributes.ac.flat", "13",
                                "attributes.movement.walk", "30",
                                "details.creatureType", "humanoid",
                                "details.alignment", "neutral evil",
                                "attributes.spellcasting", "",
                                "details.rollMode", "public"))
                        .build(),
                EnemyJpaEntity.builder().id(851L).campaignId(1L).order(1).name("Sentinelle de cendre")
                        .folder("Créatures de la Tour").level("4")
                        .values(Map.of(
                                "Description", "Armure vide animée par les cendres du champ de bataille ; s'effondre en tas de suie une fois vaincue.",
                                "Tactique", "Garde un point fixe ; ne poursuit jamais au-delà de 20 mètres de son sceau."))
                        .build()));

        // --- Tables aleatoires ---------------------------------------------------
        when(randomTableRepo.findByCampaignIdOrderByOrderAsc(1L)).thenReturn(List.of(
                RandomTableJpaEntity.builder().id(950L).campaignId(1L).order(0)
                        .name("Rencontres dans la brume des Gués").diceFormula("1d6")
                        .description("À lancer par tranche de 4 heures passées dans le marais.")
                        .entries(List.of(
                                RandomTableEntryJpaEntity.builder().id(1L).position(0).minRoll(1).maxRoll(2)
                                        .label("Nappe de brume dense").detail("Vision réduite à 3 mètres pendant 1d4 heures.").build(),
                                RandomTableEntryJpaEntity.builder().id(2L).position(1).minRoll(3).maxRoll(4)
                                        .label("Échos de la bataille").detail("Des voix spectrales rejouent les Gués ; test de SAG DD 12 ou désorientation.").build(),
                                RandomTableEntryJpaEntity.builder().id(3L).position(2).minRoll(5).maxRoll(5)
                                        .label("Patrouille des masques de suie").build(),
                                RandomTableEntryJpaEntity.builder().id(4L).position(3).minRoll(6).maxRoll(6)
                                        .label("Une Sentinelle de cendre émerge de la vase").build()))
                        .build(),
                RandomTableJpaEntity.builder().id(951L).campaignId(1L).order(1)
                        .name("Rumeurs au Chaudron Fêlé").diceFormula("1d4")
                        .entries(List.of(
                                RandomTableEntryJpaEntity.builder().id(5L).position(0).minRoll(1).maxRoll(1)
                                        .label("« La Tour s'est rallumée, j'te dis. Trois nuits de suite. »").build(),
                                RandomTableEntryJpaEntity.builder().id(6L).position(1).minRoll(2).maxRoll(2)
                                        .label("« Le colporteur ? Parti sans payer, comme un voleur. »").build(),
                                RandomTableEntryJpaEntity.builder().id(7L).position(2).minRoll(3).maxRoll(3)
                                        .label("« Maëlis ? Elle n'est pas d'ici. Personne ne sait d'où elle vient. »").build(),
                                RandomTableEntryJpaEntity.builder().id(8L).position(3).minRoll(4).maxRoll(4)
                                        .label("« Mon cousin du Guet dit qu'on a muré la crypte pour une bonne raison. »").build()))
                        .build()));

        // --- Lore : dossiers + templates + pages --------------------------------
        when(loreNodeRepo.findByLoreId(20L)).thenReturn(List.of(
                LoreNodeJpaEntity.builder().id(600L).loreId(20L).name("Géographie").order(0).build(),
                LoreNodeJpaEntity.builder().id(601L).loreId(20L).name("Factions").order(1).build(),
                LoreNodeJpaEntity.builder().id(602L).loreId(20L).name("Le Nord").parentId(600L).order(0).build()));
        when(templateRepo.findByLoreId(20L)).thenReturn(List.of(
                TemplateJpaEntity.builder().id(700L).loreId(20L).name("Lieu")
                        .fields(List.of(
                                TemplateField.text("Résumé"),
                                TemplateField.text("Histoire"),
                                TemplateField.keyValueList("En bref", List.of("Population", "Dirigeant", "Ressources")),
                                TemplateField.table("Rumeurs", List.of("d6", "Rumeur", "Vraie ?"))))
                        .build()));
        when(pageRepo.findByLoreId(20L)).thenReturn(List.of(
                PageJpaEntity.builder().id(750L).loreId(20L).nodeId(602L).order(0).templateId(700L)
                        .title("Valfroide")
                        .values(Map.of(
                                "Résumé", "Bourg fortifié de deux mille âmes, dernier marché avant les terres hautes.",
                                "Histoire", "Fondée sur les ruines d'un poste de garde impérial, Valfroide a survécu à trois sièges et à un hiver de sept mois. On y respecte deux choses : la parole donnée et le feu bien entretenu."))
                        .keyValueValues(Map.of("En bref", Map.of(
                                "Population", "≈ 2 000", "Dirigeant", "Conseil des Cinq", "Ressources", "Laine, sel, minerai de fer")))
                        .tableValues(Map.of("Rumeurs", List.of(
                                Map.of("d6", "1-2", "Rumeur", "La Tour s'est rallumée ; on a vu de la lumière trois nuits de suite.", "Vraie ?", "Oui"),
                                Map.of("d6", "3-4", "Rumeur", "Le colporteur est parti sans payer sa taxe de foire.", "Vraie ?", "Non"),
                                Map.of("d6", "5-6", "Rumeur", "Le forgeron garde la clé d'une porte que personne n'a jamais vue.", "Vraie ?", "Oui"))))
                        .build(),
                PageJpaEntity.builder().id(751L).loreId(20L).nodeId(601L).order(0)
                        .title("Les masques de suie")
                        .values(Map.of("Description", "Réseau de mercenaires et d'informateurs au service d'un commanditaire inconnu. Signe de reconnaissance : une trace de suie sous l'œil gauche."))
                        .build()));

        // --- Images placeholder (portraits, illustration, battlemaps) ----------
        when(imageRepo.findById(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return Optional.of(ImageJpaEntity.builder().id(id).storageKey("img-" + id).contentType("image/png").build());
        });
        when(storedFileRepo.findById(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return Optional.of(StoredFileJpaEntity.builder().id(id).storageKey("map-" + id).contentType("image/png").build());
        });
        when(imageStorage.download(anyString())).thenAnswer(inv -> placeholder(inv.getArgument(0), false));
        when(fileStorage.download(anyString())).thenAnswer(inv -> placeholder(inv.getArgument(0), true));

        PdfImageEncoder imageEncoder = new PdfImageEncoder(imageRepo, storedFileRepo, imageStorage, fileStorage);
        PdfStructureLoader structureLoader = new PdfStructureLoader(arcRepo, chapterRepo, sceneRepo, questRepo, enemyRepo);
        PdfExportService service = new PdfExportService(campaignRepo, npcRepo, enemyRepo, randomTableRepo,
                gameSystemRepo, loreNodeRepo, pageRepo, templateRepo, structureLoader, imageEncoder);

        byte[] pdf = service.export("1");

        assertEquals("%PDF-", new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII));
        assertTrue(pdf.length > 10_000, "le livret de démo doit être substantiel");

        Path out = Path.of("target", "campaign-preview.pdf");
        Files.createDirectories(out.getParent());
        Files.write(out, pdf);
        System.out.println("[preview] PDF écrit : " + out.toAbsolutePath());
    }

    /** Image placeholder deterministe : portrait carre ou battlemap avec grille. */
    private static InputStream placeholder(String key, boolean map) throws Exception {
        int w = map ? 1200 : 600, h = map ? 800 : 700;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        int hue = Math.abs(key.hashCode()) % 360;
        g.setPaint(new GradientPaint(0, 0, Color.getHSBColor(hue / 360f, 0.35f, 0.75f),
                w, h, Color.getHSBColor(hue / 360f, 0.55f, 0.35f)));
        g.fillRect(0, 0, w, h);
        if (map) {
            g.setColor(new Color(255, 255, 255, 70));
            for (int x = 0; x < w; x += 60) g.drawLine(x, 0, x, h);
            for (int y = 0; y < h; y += 60) g.drawLine(0, y, w, y);
        }
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 40));
        g.drawString(key, 30, h / 2);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return new ByteArrayInputStream(out.toByteArray());
    }
}
