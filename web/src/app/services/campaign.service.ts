import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Campaign, CampaignCreate, Arc, ArcCreate, Chapter, ChapterCreate, Scene, SceneCreate, Quest } from './campaign.model';
import { CampaignReadinessAssessment } from './readiness.model';
import { Npc } from './npc.model';
import { RandomTable } from './random-table.model';
import { Enemy } from './enemy.model';

/**
 * Arbre de campagne AGRÉGÉ (une seule requête HTTP) — miroir de CampaignTreeDTO.
 * Remplace la rafale d'appels (~15-20) que la sidebar déclenchait à chaque navigation.
 */
export interface CampaignTreeResponse {
  arcs: Arc[];
  chaptersByArc: Record<string, Chapter[]>;
  scenesByChapter: Record<string, Scene[]>;
  npcs: Npc[];
  randomTables: RandomTable[];
  enemies: Enemy[];
  quests: Quest[];
  readiness: CampaignReadinessAssessment;
}

/**
 * Périmètre de l'export Foundry (modale) :
 * - maps     : Scenes Foundry (battlemaps) + acteurs/tokens des ennemis liés.
 * - journals : journaux narratifs (arcs, chapitres, scènes, PNJ, bestiaire).
 * - tables   : tables aléatoires (RollTables).
 */
export interface FoundryExportOptions {
  maps: boolean;
  journals: boolean;
  tables: boolean;
}

/** Compte des entités qui seront supprimées en cascade avec la campagne. */
export interface CampaignDeletionImpact {
  arcs: number;
  chapters: number;
  scenes: number;
  playthroughs: number;
}

/** Compte des entités qui seront supprimées en cascade avec un arc. */
export interface ArcDeletionImpact {
  chapters: number;
  scenes: number;
}

/** Compte des scènes qui tomberont avec un chapitre. */
export interface ChapterDeletionImpact {
  scenes: number;
}

/**
 * Service HTTP pour la gestion des Campagnes.
 * Port de sortie vers le Backend Java (Architecture Hexagonale).
 */
@Injectable({
  providedIn: 'root'
})
export class CampaignService {
  private apiUrl = '/api/campaigns';

  constructor(private http: HttpClient) {}

  getAllCampaigns(): Observable<Campaign[]> {
    return this.http.get<Campaign[]>(this.apiUrl);
  }

  getCampaignById(id: string): Observable<Campaign> {
    return this.http.get<Campaign>(`${this.apiUrl}/${id}`);
  }

  /** Bilan de préparation (Pilier B) — alimente les pastilles de l'arbre de la sidebar. */
  getReadiness(id: string): Observable<CampaignReadinessAssessment> {
    return this.http.get<CampaignReadinessAssessment>(`${this.apiUrl}/${id}/readiness`);
  }

  /** Arbre complet de la campagne en UNE requête (sidebar : structure + quêtes + readiness). */
  getTree(id: string): Observable<CampaignTreeResponse> {
    return this.http.get<CampaignTreeResponse>(`${this.apiUrl}/${id}/tree`);
  }

  /**
   * Télécharge le bundle d'export Foundry de la campagne (.zip).
   * Périmètre optionnel (tout par défaut) : cartes+ennemis / journaux / tables.
   */
  exportFoundry(id: string, opts?: FoundryExportOptions): Observable<Blob> {
    let params = new HttpParams();
    if (opts) {
      params = params
        .set('maps', String(opts.maps))
        .set('journals', String(opts.journals))
        .set('tables', String(opts.tables));
    }
    return this.http.get(`${this.apiUrl}/${id}/foundry-export`, { responseType: 'blob', params });
  }

  /** Génère et télécharge le livret PDF de la campagne. */
  exportPdf(id: string): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/${id}/pdf-export`, { responseType: 'blob' });
  }

  createCampaign(campaign: CampaignCreate): Observable<Campaign> {
    return this.http.post<Campaign>(this.apiUrl, campaign);
  }

  updateCampaign(id: string, campaign: CampaignCreate): Observable<Campaign> {
    return this.http.put<Campaign>(`${this.apiUrl}/${id}`, campaign);
  }

  deleteCampaign(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  getCampaignDeletionImpact(id: string): Observable<CampaignDeletionImpact> {
    return this.http.get<CampaignDeletionImpact>(`${this.apiUrl}/${id}/deletion-impact`);
  }

  // ========== ARC ==========
  getArcs(campaignId: string): Observable<Arc[]> {
    const params = new HttpParams().set('campaignId', campaignId);
    return this.http.get<Arc[]>('/api/arcs', { params });
  }

  getArcById(id: string): Observable<Arc> {
    return this.http.get<Arc>(`/api/arcs/${id}`);
  }

  createArc(payload: ArcCreate): Observable<Arc> {
    return this.http.post<Arc>('/api/arcs', payload);
  }

  updateArc(id: string, payload: ArcCreate): Observable<Arc> {
    return this.http.put<Arc>(`/api/arcs/${id}`, payload);
  }

  deleteArc(id: string): Observable<void> {
    return this.http.delete<void>(`/api/arcs/${id}`);
  }

  getArcDeletionImpact(id: string): Observable<ArcDeletionImpact> {
    return this.http.get<ArcDeletionImpact>(`/api/arcs/${id}/deletion-impact`);
  }

  /** Réordonne les arcs d'une campagne (glisser-déposer) : order = position. */
  reorderArcs(orderedIds: string[]): Observable<void> {
    return this.http.put<void>('/api/arcs/reorder', { orderedIds });
  }

  // ========== CHAPTER ==========
  /** Liste les chapitres d'un arc (donnée de scénario pure). */
  getChapters(arcId: string): Observable<Chapter[]> {
    const params = new HttpParams().set('arcId', arcId);
    return this.http.get<Chapter[]>('/api/chapters', { params });
  }

  getChapterById(id: string): Observable<Chapter> {
    return this.http.get<Chapter>(`/api/chapters/${id}`);
  }

  createChapter(payload: ChapterCreate): Observable<Chapter> {
    return this.http.post<Chapter>('/api/chapters', payload);
  }

  updateChapter(id: string, payload: ChapterCreate): Observable<Chapter> {
    return this.http.put<Chapter>(`/api/chapters/${id}`, payload);
  }

  deleteChapter(id: string): Observable<void> {
    return this.http.delete<void>(`/api/chapters/${id}`);
  }

  getChapterDeletionImpact(id: string): Observable<ChapterDeletionImpact> {
    return this.http.get<ChapterDeletionImpact>(`/api/chapters/${id}/deletion-impact`);
  }

  /** Réordonne (et déplace) les chapitres d'un arc : order = position, arcId = arc cible. */
  reorderChapters(arcId: string, orderedIds: string[]): Observable<void> {
    return this.http.put<void>('/api/chapters/reorder', { arcId, orderedIds });
  }

  // ========== SCENE ==========
  getScenes(chapterId: string): Observable<Scene[]> {
    const params = new HttpParams().set('chapterId', chapterId);
    return this.http.get<Scene[]>('/api/scenes', { params });
  }

  getSceneById(id: string): Observable<Scene> {
    return this.http.get<Scene>(`/api/scenes/${id}`);
  }

  createScene(payload: SceneCreate): Observable<Scene> {
    return this.http.post<Scene>('/api/scenes', payload);
  }

  updateScene(id: string, payload: SceneCreate): Observable<Scene> {
    return this.http.put<Scene>(`/api/scenes/${id}`, payload);
  }

  deleteScene(id: string): Observable<void> {
    return this.http.delete<void>(`/api/scenes/${id}`);
  }

  /** Réordonne (et déplace) les scènes d'un chapitre : order = position, chapterId = cible. */
  reorderScenes(chapterId: string, orderedIds: string[]): Observable<void> {
    return this.http.put<void>('/api/scenes/reorder', { chapterId, orderedIds });
  }

  search(q: string): Observable<Campaign[]> {
    const params = new HttpParams().set('q', q);
    return this.http.get<Campaign[]>(`${this.apiUrl}/search`, { params });
  }
}
