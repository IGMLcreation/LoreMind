import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Campaign, CampaignCreate, Arc, ArcCreate, Chapter, ChapterCreate, Scene, SceneCreate } from './campaign.model';

/** Compte des entités qui seront supprimées en cascade avec la campagne. */
export interface CampaignDeletionImpact {
  arcs: number;
  chapters: number;
  scenes: number;
  characters: number;
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

  // ========== CHAPTER ==========
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

  search(q: string): Observable<Campaign[]> {
    const params = new HttpParams().set('q', q);
    return this.http.get<Campaign[]>(`${this.apiUrl}/search`, { params });
  }
}
