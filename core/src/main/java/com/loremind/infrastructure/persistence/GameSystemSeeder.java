package com.loremind.infrastructure.persistence;

import com.loremind.domain.gamesystemcontext.GameSystem;
import com.loremind.domain.gamesystemcontext.ports.GameSystemRepository;
import com.loremind.domain.shared.template.ImageLayout;
import com.loremind.domain.shared.template.TemplateField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seed 3 rulesets libres au premier démarrage (si la table game_systems est vide).
 * <p>
 * Objectif : donner à l'utilisateur un point de départ pour comprendre le format
 * attendu (markdown structuré par titres H2) et permettre une démo "out of the box"
 * sans devoir taper ses propres règles.
 * <p>
 * Les rulesets fournis sont des <b>extraits libres</b> (Nimble, SRD 5.1 extrait,
 * homebrew exemple) — pas des règles officielles complètes. L'utilisateur est
 * libre de les éditer, supprimer, ou les utiliser comme template.
 * <p>
 * Idempotence : ne seed qu'une fois. Si l'utilisateur supprime un ruleset seedé,
 * il ne revient pas au redémarrage — c'est voulu (respect du choix utilisateur).
 * <p>
 * Backfill 2026-04-30 : pour les GameSystems existants (avant la refonte
 * template-based), on remplit aussi les templates PJ/PNJ par defaut s'ils
 * sont vides — sinon les fiches restent inutilisables.
 */
@Component
public class GameSystemSeeder {

    private static final Logger log = LoggerFactory.getLogger(GameSystemSeeder.class);

    private final GameSystemRepository gameSystemRepository;

    public GameSystemSeeder(GameSystemRepository gameSystemRepository) {
        this.gameSystemRepository = gameSystemRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedIfEmpty() {
        List<GameSystem> existing = gameSystemRepository.findAll();
        if (existing.isEmpty()) {
            log.info("Seed initial des GameSystems (table vide)...");
            for (GameSystem gs : defaultSystems()) {
                gameSystemRepository.save(gs);
            }
            log.info("GameSystems seedés : {}", defaultSystems().size());
            return;
        }
        log.debug("GameSystem seed skipped — table non vide. Backfill templates si necessaire...");
        backfillEmptyTemplates(existing);
    }

    /**
     * Backfill idempotent : pour chaque GameSystem existant ou les deux templates
     * sont vides (PJ ET PNJ), injecte le template generique. Si l'utilisateur a
     * deja personnalise au moins un des deux, on ne touche a rien.
     */
    private void backfillEmptyTemplates(List<GameSystem> systems) {
        int patched = 0;
        for (GameSystem gs : systems) {
            boolean charEmpty = gs.getCharacterTemplate() == null || gs.getCharacterTemplate().isEmpty();
            boolean npcEmpty = gs.getNpcTemplate() == null || gs.getNpcTemplate().isEmpty();
            if (charEmpty && npcEmpty) {
                gs.replaceCharacterTemplate(genericCharacterTemplate());
                gs.replaceNpcTemplate(genericNpcTemplate());
                gameSystemRepository.save(gs);
                patched++;
            }
        }
        if (patched > 0) log.info("Backfill templates GameSystem : {} systeme(s) patche(s).", patched);
    }

    private List<GameSystem> defaultSystems() {
        return List.of(
                GameSystem.builder()
                        .name("Nimble (extrait)")
                        .description("Système léger et narratif, résolution rapide des combats.")
                        .author("LoreMind seed")
                        .isPublic(false)
                        .rulesMarkdown(NIMBLE_RULES)
                        .characterTemplate(nimbleCharacterTemplate())
                        .npcTemplate(genericNpcTemplate())
                        .build(),
                GameSystem.builder()
                        .name("D&D 5e SRD (extrait)")
                        .description("Extrait libre des bases du System Reference Document 5.1.")
                        .author("LoreMind seed")
                        .isPublic(false)
                        .rulesMarkdown(DND_SRD_RULES)
                        .characterTemplate(dndCharacterTemplate())
                        .npcTemplate(genericNpcTemplate())
                        .build(),
                GameSystem.builder()
                        .name("Homebrew Exemple")
                        .description("Template minimaliste à dupliquer pour créer votre propre système.")
                        .author("LoreMind seed")
                        .isPublic(false)
                        .rulesMarkdown(HOMEBREW_EXAMPLE)
                        .characterTemplate(genericCharacterTemplate())
                        .npcTemplate(genericNpcTemplate())
                        .build()
        );
    }

    // --- Templates par defaut ---------------------------------------------

    /** Template generique PJ — utilise pour Homebrew, backfill, et fallback. */
    private static List<TemplateField> genericCharacterTemplate() {
        return List.of(
                TemplateField.text("Histoire"),
                TemplateField.text("Personnalite"),
                TemplateField.text("Apparence"),
                TemplateField.image("Galerie", ImageLayout.GALLERY),
                TemplateField.text("Notes")
        );
    }

    /** Template generique PNJ — focus besoins MJ. */
    private static List<TemplateField> genericNpcTemplate() {
        return List.of(
                TemplateField.text("Apparence"),
                TemplateField.text("Motivation"),
                TemplateField.text("Faction"),
                TemplateField.text("Notes MJ")
        );
    }

    private static List<TemplateField> nimbleCharacterTemplate() {
        return List.of(
                TemplateField.text("Classe"),
                TemplateField.number("Blessures graves max"),
                TemplateField.text("Capacites de classe"),
                TemplateField.text("Equipement"),
                TemplateField.text("Histoire"),
                TemplateField.text("Objectifs personnels"),
                TemplateField.image("Galerie", ImageLayout.GALLERY)
        );
    }

    private static List<TemplateField> dndCharacterTemplate() {
        return List.of(
                TemplateField.text("Classe"),
                TemplateField.text("Race"),
                TemplateField.text("Historique"),
                TemplateField.text("Alignement"),
                TemplateField.number("Niveau"),
                TemplateField.number("PV max"),
                TemplateField.number("CA"),
                TemplateField.keyValueList("Caracteristiques",
                        List.of("FOR", "DEX", "CON", "INT", "SAG", "CHA")),
                TemplateField.text("Competences"),
                TemplateField.text("Equipement"),
                TemplateField.text("Sorts"),
                TemplateField.text("Histoire"),
                TemplateField.image("Galerie", ImageLayout.GALLERY)
        );
    }

    private static final String NIMBLE_RULES = """
            Système Nimble — résolution rapide, narration fluide, peu de tableaux. Agnostique (aucun univers imposé).

            ## Combat
            - Initiative libre : les joueurs décrivent leur action dans l'ordre qu'ils veulent, le MJ joue les ennemis quand la fiction l'exige.
            - Résolution : 1d20 + mod, difficulté 10/15/20 (facile/normal/dur). 20 naturel = critique (double dégâts).
            - Dégâts : arme légère 1d6, arme lourde 1d10, projectile 1d8. Pas de table d'armure, l'armure augmente la difficulté à toucher.
            - Blessures : un PJ peut encaisser 3 blessures graves avant de tomber. Pas de PV fins — on raconte les coups.

            ## Classes
            - **Guerrier** : +2 en combat, peut relancer un dé de dégât 1×/scène.
            - **Explorateur** : +2 en perception/survie, ignore la première blessure d'une scène.
            - **Mage** : peut lancer un effet de magie par scène, nécessite une composante racontée.
            - **Barde** : +2 en social, peut inspirer un allié (relance de dé).

            ## Monstres
            Les monstres ont 3 stats : Menace (difficulté à toucher), Dégâts (dé de dégât), Résistance (nombre de blessures).
            Exemples : Gobelin (Menace 10, 1d6, 1), Ogre (Menace 13, 1d10, 3), Dragon adulte (Menace 18, 2d10, 6).
            """;

    private static final String DND_SRD_RULES = """
            Extrait libre du SRD 5.1 (Open Game License). Pour les règles complètes, consulter le SRD officiel.

            ## Combat
            - Initiative : 1d20 + mod Dex au début du combat, ordre fixe par round.
            - Action par tour : une action, une action bonus (si classe le permet), une réaction, mouvement jusqu'à la vitesse.
            - Attaque : 1d20 + mod caractéristique + bonus maîtrise vs CA de la cible.
            - Dégâts : dé de l'arme + mod caractéristique. Critique sur 20 naturel (double les dés de dégâts).
            - Avantage/Désavantage : lancer 2d20 et garder le meilleur / pire.

            ## Classes
            - **Barbare** : d12 PV, rage (+dégâts, résistance). Caractéristique principale : Force.
            - **Barde** : d8 PV, sorts + inspiration bardique. Caractéristique : Charisme.
            - **Clerc** : d8 PV, sorts divins, canalise la divinité. Caractéristique : Sagesse.
            - **Druide** : d8 PV, sorts nature + forme animale. Caractéristique : Sagesse.
            - **Ensorceleur** : d6 PV, sorts innés + métamagie. Caractéristique : Charisme.
            - **Guerrier** : d10 PV, maîtrise martiale, second souffle. Caractéristique : Force ou Dextérité.
            - **Magicien** : d6 PV, livre de sorts, grande flexibilité. Caractéristique : Intelligence.
            - **Moine** : d8 PV, arts martiaux + ki. Caractéristique : Dextérité + Sagesse.
            - **Paladin** : d10 PV, sorts + serment + imposition des mains. Caractéristique : Force + Charisme.
            - **Rôdeur** : d10 PV, ennemi juré + explorateur + sorts. Caractéristique : Dextérité + Sagesse.
            - **Roublard** : d8 PV, attaque sournoise + expertise. Caractéristique : Dextérité.

            ## Monstres
            Stat block standard : CA, PV, Vitesse, For/Dex/Con/Int/Sag/Cha, jets de sauvegarde, compétences, sens, langues, Facteur de Puissance (FP).
            Exemples : Gobelin (FP 1/4, CA 15, 7 PV), Ogre (FP 2, CA 11, 59 PV), Dragon rouge adulte (FP 17, CA 19, 256 PV).
            """;

    private static final String HOMEBREW_EXAMPLE = """
            Template vide à dupliquer et remplir pour créer votre propre système.

            ## Combat
            (Décrivez ici comment se résout un combat : initiative, jet d'attaque, dégâts, points de vie, critiques...)

            ## Classes
            (Listez les archétypes jouables : nom, stats de base, capacités signature.)

            ## Monstres
            (Format de stat block pour vos créatures : stats, capacités spéciales, FP/niveau.)

            ## Magie
            (Si votre système a un système de magie : écoles, coût, composantes, listes de sorts/pouvoirs.)

            ## Progression
            (Comment les PJ montent en puissance : XP, niveaux, acquisitions par niveau.)
            """;
}
