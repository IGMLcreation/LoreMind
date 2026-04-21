import {
  Folder,
  Users, Swords, MapPin, Shield, Crown, Skull, Gem,
  BookOpen, Scroll, Wand2, Sparkles, TreePine, Mountain,
  Ship, Flame, Star, Moon, Key, Globe, Compass, LucideIconData
} from 'lucide-angular';

/**
 * Registre partagé d'icônes disponibles pour les dossiers (LoreNode).
 *
 * Utilisé à la fois par :
 *  - l'écran de création de dossier (grille de sélection)
 *  - la sidebar (résolution `iconKey → LucideIconData` pour afficher l'icône)
 *
 * Pourquoi factoriser ? Avant : deux sources de vérité risqueraient de
 * diverger (ex: ajout d'une icône dans l'un sans l'autre).
 */
export interface IconOption {
  key: string;
  icon: LucideIconData;
}

export const LORE_ICON_OPTIONS: IconOption[] = [
  { key: 'users',     icon: Users },
  { key: 'swords',    icon: Swords },
  { key: 'map-pin',   icon: MapPin },
  { key: 'shield',    icon: Shield },
  { key: 'crown',     icon: Crown },
  { key: 'skull',     icon: Skull },
  { key: 'gem',       icon: Gem },
  { key: 'book-open', icon: BookOpen },
  { key: 'scroll',    icon: Scroll },
  { key: 'wand',      icon: Wand2 },
  { key: 'sparkles',  icon: Sparkles },
  { key: 'tree',      icon: TreePine },
  { key: 'mountain',  icon: Mountain },
  { key: 'ship',      icon: Ship },
  { key: 'flame',     icon: Flame },
  { key: 'star',      icon: Star },
  { key: 'moon',      icon: Moon },
  { key: 'key',       icon: Key },
  { key: 'globe',     icon: Globe },
  { key: 'compass',   icon: Compass },
];

/** Icône par défaut pour un dossier sans icône. */
export const DEFAULT_FOLDER_ICON: LucideIconData = Folder;

/** Résout une clé d'icône en LucideIconData. Fallback : icône dossier par défaut. */
export function resolveIcon(key: string | null | undefined): LucideIconData {
  if (!key) return DEFAULT_FOLDER_ICON;
  return LORE_ICON_OPTIONS.find(o => o.key === key)?.icon ?? DEFAULT_FOLDER_ICON;
}
