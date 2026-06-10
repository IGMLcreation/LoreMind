/**
 * Types de l'import de PDF de campagne (arbre arc → chapitre → scène).
 * Jumeaux des records Java (CampaignImportProposal / CampaignImportProgress).
 */

export interface RoomProposal {
  name: string;
  description: string;
  enemies: string;
  loot: string;
}

export interface SceneProposal {
  name: string;
  description: string;
  /** Texte d'encadré « à lire aux joueurs ». */
  playerNarration: string;
  /** Secrets / développement MJ. */
  gmNotes: string;
  /** Non vide ⇒ la scène est un lieu explorable (donjon). */
  rooms: RoomProposal[];
  /** ID si la scène existe déjà (revue pré-chargée) ; absent = à créer. */
  existingId?: string | null;
}

export interface ChapterProposal {
  name: string;
  description: string;
  scenes: SceneProposal[];
  existingId?: string | null;
}

export type ArcKind = 'LINEAR' | 'HUB';

export interface ArcProposal {
  name: string;
  description: string;
  type: ArcKind;
  chapters: ChapterProposal[];
  existingId?: string | null;
}

/** PNJ/créature notable détecté dans le PDF (PNJ nommés, boss). */
export interface NpcProposal {
  name: string;
  description: string;
}

export interface CampaignImportProposal {
  arcs: ArcProposal[];
  /** PNJ cochés à la revue (créés comme Npc de la campagne à l'apply). */
  npcs?: NpcProposal[];
}

/** Récapitulatif renvoyé après création effective des entités. */
export interface CampaignImportApplyResult {
  arcsCreated: number;
  chaptersCreated: number;
  scenesCreated: number;
  npcsCreated: number;
}

/**
 * Évènements du flux SSE d'import streamé.
 * - progress : avancement (total=0 ⇒ extraction en cours).
 * - done     : arbre proposé (à réviser).
 * - error    : message d'erreur côté serveur.
 */
export type CampaignImportStreamEvent =
  | {
      type: 'progress';
      current: number;
      total: number;
      pageCount: number;
      ocrPageCount: number;
      arcCount: number;
      chapterCount: number;
      sceneCount: number;
      npcCount: number;
    }
  | { type: 'done'; arcs: ArcProposal[]; npcs: NpcProposal[] }
  | { type: 'error'; message: string };
