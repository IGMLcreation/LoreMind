package com.loremind.domain.shared.template;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Value Object d'un champ de Template (kernel partage).
 * <p>
 * Un champ a un nom (affiche dans l'UI) et un type. Le type pilote
 * le rendu cote front et la logique metier (seuls les champs TEXT sont
 * envoyes a l'IA pour generation).
 * <p>
 * Pour les champs IMAGE, {@link #layout} precise la variante de rendu
 * (gallery/hero/masonry/carousel). Nullable : l'absence equivaut a GALLERY.
 * Ignore pour les autres types.
 * <p>
 * {@link #id} est la cle STABLE du bloc : les valeurs des Pages s'ancrent
 * dessus (et non sur {@link #name}), ce qui permet de renommer un bloc sans
 * orpheliner son contenu. Pour les templates anterieurs (qui n'ont pas d'id),
 * l'id est retro-rempli avec le nom courant a la lecture : comme les valeurs
 * existantes sont deja rangees par nom, {@code id == name} au depart et rien
 * n'a besoin d'etre migre.
 * <p>
 * {@link #pos} porte le placement du bloc dans la grille 12 colonnes du
 * template (voir {@link BlockPosition}). Null = auto-flow empile (rendu
 * historique).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateField {
    /**
     * Identifiant STABLE du bloc, cle d'ancrage des valeurs de Page.
     * Retro-rempli avec {@link #name} pour les templates sans id. Immuable :
     * survit aux renommages du bloc.
     */
    private String id;
    /** Nom du champ tel qu'affiche dans l'UI (ex: "Histoire", "Portrait"). */
    private String name;
    /** Type du champ, pilote le rendu et la generation IA. */
    private FieldType type;
    /** Variante de rendu pour les champs IMAGE. Null = GALLERY. */
    private ImageLayout layout;
    /**
     * Labels predefinis (ordre significatif), selon le type :
     * - KEY_VALUE_LIST : libelles des lignes. Ex: ["FOR","DEX","CON","INT","SAG","CHA"].
     * - TABLE          : noms des COLONNES. Ex: ["Objet","Prix","Description"].
     * Null/vide pour les autres types.
     */
    private List<String> labels;

    /**
     * Chemin Foundry de ce champ (ex: {@code attributes.hp.value}) quand le template
     * est calqué sur un système Foundry importé. Permet, a l'export, de construire un
     * acteur typé en posant {@code system.<foundryPath> = valeur}. Null = non mappé.
     */
    private String foundryPath;

    /**
     * Placement du bloc dans la grille 12 colonnes du template. Null = auto-flow
     * (le bloc s'empile a la suite des precedents, comme le rendu historique).
     */
    private BlockPosition pos;

    /** Constructeur de retrocompat : type seul, layout/labels=null. */
    public TemplateField(String name, FieldType type) {
        this(name, type, null, null, null);
    }

    /** Constructeur de retrocompat : type + layout, labels=null. */
    public TemplateField(String name, FieldType type, ImageLayout layout) {
        this(name, type, layout, null, null);
    }

    /** Constructeur de retrocompat (4 args) : sans foundryPath. */
    public TemplateField(String name, FieldType type, ImageLayout layout, List<String> labels) {
        this(name, type, layout, labels, null);
    }

    /**
     * Constructeur de retrocompat (5 args) : sans id ni pos. Conserve la
     * signature publique historique (id retro-rempli plus tard, pos=null).
     */
    public TemplateField(String name, FieldType type, ImageLayout layout, List<String> labels, String foundryPath) {
        this(null, name, type, layout, labels, foundryPath, null);
    }

    /** Raccourci : construit un champ de type TEXT (cas le plus courant). */
    public static TemplateField text(String name) {
        return new TemplateField(name, FieldType.TEXT, null, null);
    }

    /** Raccourci : construit un champ de type IMAGE avec layout GALLERY. */
    public static TemplateField image(String name) {
        return new TemplateField(name, FieldType.IMAGE, ImageLayout.GALLERY, null);
    }

    /** Raccourci : construit un champ IMAGE avec un layout specifique. */
    public static TemplateField image(String name, ImageLayout layout) {
        return new TemplateField(name, FieldType.IMAGE, layout, null);
    }

    /** Raccourci : construit un champ de type NUMBER. */
    public static TemplateField number(String name) {
        return new TemplateField(name, FieldType.NUMBER, null, null);
    }

    /** Raccourci : construit un champ KEY_VALUE_LIST avec labels predefinis. */
    public static TemplateField keyValueList(String name, List<String> labels) {
        return new TemplateField(name, FieldType.KEY_VALUE_LIST, null, labels);
    }

    /** Raccourci : construit un champ TABLE avec ses noms de colonnes. */
    public static TemplateField table(String name, List<String> columns) {
        return new TemplateField(name, FieldType.TABLE, null, columns);
    }
}
