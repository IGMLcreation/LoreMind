package com.loremind.domain.campaigncontext;

import java.util.function.UnaryOperator;

/**
 * Fiche « Publication » d'une campagne : ce qu'un module vendu ou partagé affiche en
 * couverture, page technique, introduction pour le MJ et page de crédits.
 * <p>
 * Tout est en texte libre (« 1 à 3 », « 3-5 joueurs », « 2 séances de 4 h ») : chaque
 * système de jeu a ses conventions, l'auteur écrit ce qu'il veut voir imprimé.
 * Valeur immuable ; {@link #normalized()} nettoie une saisie (blancs → null, longueurs bornées).
 *
 * @param subtitle        sous-titre de couverture
 * @param author          auteur(s) affichés en couverture
 * @param system          système de jeu tel qu'il doit apparaître (« D&amp;D 5e », « Chroniques Oubliées »)
 * @param levels          niveaux des personnages
 * @param players         nombre de joueurs
 * @param duration        durée de jeu estimée
 * @param pitch           argument : l'aventure en quelques lignes
 * @param gmBackground    contexte caché, réservé au MJ (ce qui s'est vraiment passé)
 * @param hooks           accroches pour impliquer les personnages (une par ligne « - … »)
 * @param contentWarnings avertissements de contenu (thèmes sensibles)
 * @param credits         crédits : auteur, illustrations, relecture, testeurs…
 * @param legal           licence et mentions légales
 * @param version         version du document (« 1.0 »)
 * @param coverImageId    image de couverture (id d'image), nullable
 */
public record CampaignPublication(
        String subtitle,
        String author,
        String system,
        String levels,
        String players,
        String duration,
        String pitch,
        String gmBackground,
        String hooks,
        String contentWarnings,
        String credits,
        String legal,
        String version,
        String coverImageId
) {

    private static final int SHORT_MAX = 200;
    private static final int LONG_MAX = 20_000;

    public static CampaignPublication empty() {
        return new CampaignPublication(null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
    }

    /** Copie nettoyée : textes rognés, blancs → null, longueurs bornées. */
    public CampaignPublication normalized() {
        UnaryOperator<String> shortText = s -> clean(s, SHORT_MAX);
        UnaryOperator<String> longText = s -> clean(s, LONG_MAX);
        return new CampaignPublication(
                shortText.apply(subtitle), shortText.apply(author), shortText.apply(system),
                shortText.apply(levels), shortText.apply(players), shortText.apply(duration),
                longText.apply(pitch), longText.apply(gmBackground), longText.apply(hooks),
                longText.apply(contentWarnings), longText.apply(credits), longText.apply(legal),
                shortText.apply(version), shortText.apply(coverImageId));
    }

    /**
     * Vrai si au moins un champ est renseigné. (Pas de getter {@code isEmpty()} : il serait
     * sérialisé en JSON comme un champ de plus.)
     */
    public boolean hasContent() {
        return !normalized().equals(empty());
    }

    /** Au moins une information de la page technique (système, niveaux, joueurs, durée). */
    public boolean hasTechnicalSheet() {
        return present(system) || present(levels) || present(players) || present(duration);
    }

    /** Au moins une information de la page de crédits. */
    public boolean hasCredits() {
        return present(author) || present(credits) || present(legal) || present(version);
    }

    private static boolean present(String s) {
        return s != null && !s.isBlank();
    }

    private static String clean(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.length() > max ? t.substring(0, max) : t;
    }
}
