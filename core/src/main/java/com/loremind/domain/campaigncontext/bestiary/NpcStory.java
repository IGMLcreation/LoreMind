package com.loremind.domain.campaigncontext.bestiary;

import java.util.Locale;
import java.util.Set;

/**
 * Fiche « rôle dans l'histoire » d'un PNJ, indépendante du système de jeu (le gabarit du
 * système porte les caractéristiques) : ce qui rend le PNJ jouable et utile au récit.
 *
 * @param role       place dans l'histoire : {@code OPPONENT} (s'oppose aux PJ), {@code ALLY}
 *                   (les aide), {@code RESOURCE} (détient une information ou un service),
 *                   {@code EXTRA} (figurant) ; null = non précisé
 * @param motivation ce qu'il veut, et ce qu'il est prêt à faire pour l'obtenir
 * @param knowledge  ce qu'il sait (et peut révéler) — réservé au MJ
 * @param mannerism  signe distinctif à jouer (geste, phrasé, tic)
 */
public record NpcStory(String role, String motivation, String knowledge, String mannerism) {

    public static final Set<String> ROLES = Set.of("OPPONENT", "ALLY", "RESOURCE", "EXTRA");
    private static final int SHORT_MAX = 300;
    private static final int LONG_MAX = 10_000;

    /** Copie nettoyée : rôle inconnu → null, textes rognés (blancs → null) et bornés. */
    public NpcStory normalized() {
        String r = role == null ? null : role.trim().toUpperCase(Locale.ROOT);
        // Set.of(...).contains(null) lève une NullPointerException : rôle absent testé à part.
        return new NpcStory(r != null && ROLES.contains(r) ? r : null,
                clean(motivation, LONG_MAX), clean(knowledge, LONG_MAX), clean(mannerism, SHORT_MAX));
    }

    /** Vrai si au moins un élément est renseigné. */
    public boolean hasContent() {
        NpcStory n = normalized();
        return n.role() != null || n.motivation() != null || n.knowledge() != null || n.mannerism() != null;
    }

    /** Un PNJ qui pèse sur l'intrigue (opposant ou allié) : sa motivation est attendue. */
    public boolean drivesThePlot() {
        String r = normalized().role();
        return "OPPONENT".equals(r) || "ALLY".equals(r);
    }

    private static String clean(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.length() > max ? t.substring(0, max) : t;
    }
}
