import {
  Bookmark,
  Theater, Drama, Compass, Flag, Calendar, Map, Castle, Tent,
  Swords, Skull, Crown, Heart, Eye, Footprints, Dice5, Hourglass,
  Flame, Snowflake, Cloud, Sun, Moon, Star, Zap, Target,
  Music, MessageCircle, Lock, Key as KeyIcon, Coins, Gift,
  LucideIconData
} from 'lucide-angular';

// Type local equivalent a IconOption de lore-icons. Duplique volontairement
// pour eviter une dependance circulaire (lore-icons importe ce fichier).
export interface CampaignIconOption {
  key: string;
  icon: LucideIconData;
}

/**
 * Banque d'icones dediee aux entites narratives d'une campagne (arc, chapitre, scene).
 *
 * Pourquoi separe de LORE_ICON_OPTIONS ? Les icones lore sont plutot orientees
 * "objets de monde" (chateau, foret, dragon...). Ici on est sur du sequencement
 * narratif et de la mise en scene : ambiances, actes, decisions. Cles uniques
 * (prefixees `c-` quand un nom existerait deja dans le lore) pour eviter les
 * collisions avec LORE_ICON_OPTIONS — le resolver consulte les deux registres.
 */
export const CAMPAIGN_ICON_OPTIONS: CampaignIconOption[] = [
  { key: 'c-theater',      icon: Theater },
  { key: 'c-drama',        icon: Drama },
  { key: 'c-compass',      icon: Compass },
  { key: 'c-flag',         icon: Flag },
  { key: 'c-calendar',     icon: Calendar },
  { key: 'c-map',          icon: Map },
  { key: 'c-castle',       icon: Castle },
  { key: 'c-tent',         icon: Tent },
  { key: 'c-swords',       icon: Swords },
  { key: 'c-skull',        icon: Skull },
  { key: 'c-crown',        icon: Crown },
  { key: 'c-heart',        icon: Heart },
  { key: 'c-eye',          icon: Eye },
  { key: 'c-footprints',   icon: Footprints },
  { key: 'c-dice',         icon: Dice5 },
  { key: 'c-hourglass',    icon: Hourglass },
  { key: 'c-flame',        icon: Flame },
  { key: 'c-snowflake',    icon: Snowflake },
  { key: 'c-cloud',        icon: Cloud },
  { key: 'c-sun',          icon: Sun },
  { key: 'c-moon',         icon: Moon },
  { key: 'c-star',         icon: Star },
  { key: 'c-zap',          icon: Zap },
  { key: 'c-target',       icon: Target },
  { key: 'c-music',        icon: Music },
  { key: 'c-message',      icon: MessageCircle },
  { key: 'c-lock',         icon: Lock },
  { key: 'c-key',          icon: KeyIcon },
  { key: 'c-coins',        icon: Coins },
  { key: 'c-gift',         icon: Gift },
];

/** Icone par defaut quand une entite narrative n'en a pas. */
export const DEFAULT_CAMPAIGN_ICON: LucideIconData = Bookmark;

/** Resolveur dedie. Prefere passer par `resolveIcon` dans lore-icons qui consulte les deux. */
export function resolveCampaignIcon(key: string | null | undefined): LucideIconData {
  if (!key) return DEFAULT_CAMPAIGN_ICON;
  return CAMPAIGN_ICON_OPTIONS.find(o => o.key === key)?.icon ?? DEFAULT_CAMPAIGN_ICON;
}
