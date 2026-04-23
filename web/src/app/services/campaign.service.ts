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
  private apiUrl = 'http://localhost:8080/api/campaigns';

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
    return this.http.get<Arc[]>(`http://localhost:8080/api/arcs/campaign/${campaignId}`);
  }

  getArcById(id: string): Observable<Arc> {
    return this.http.get<Arc>(`http://localhost:8080/api/arcs/${id}`);
  }

  createArc(payload: ArcCreate): Observable<Arc> {
    return this.http.post<Arc>('http://localhost:8080/api/arcs', payload);
  }

  updateArc(id: string, payload: ArcCreate): Observable<Arc> {
    return this.http.put<Arc>(`http://localhost:8080/api/arcs/${id}`, payload);
  }

  deleteArc(id: string): Observable<void> {
    return this.http.delete<void>(`http://localhost:8080/api/arcs/${id}`);
  }

  getArcDeletionImpact(id: string): Observable<ArcDeletionImpact> {
    return this.http.get<ArcDeletionImpact>(`http://localhost:8080/api/arcs/${id}/deletion-impact`);
  }

  // ========== CHAPTER ==========
  getChapters(arcId: string): Observable<Chapter[]> {
    return this.http.get<Chapter[]>(`http://localhost:8080/api/chapters/arc/${arcId}`);
  }

  getChapterById(id: string): Observable<Chapter> {
    return this.http.get<Chapter>(`http://localhost:8080/api/chapters/${id}`);
  }

  createChapter(payload: ChapterCreate): Observable<Chapter> {
    return this.http.post<Chapter>('http://localhost:8080/api/chapters', payload);
  }

  updateChapter(id: string, payload: ChapterCreate): Observable<Chapter> {
    return this.http.put<Chapter>(`http://localhost:8080/api/chapters/${id}`, payload);
  }

  deleteChapter(id: string): Observable<void> {
    return this.http.delete<void>(`http://localhost:8080/api/chapters/${id}`);
  }

  getChapterDeletionImpact(id: string): Observable<ChapterDeletionImpact> {
    return this.http.get<ChapterDeletionImpact>(`http://localhost:8080/api/chapters/${id}/deletion-impact`);
  }

  // ========== SCENE ==========
  getScenes(chapterId: string): Observable<Scene[]> {
    return this.http.get<Scene[]>(`http://localhost:8080/api/scenes/chapter/${chapterId}`);
  }

  getSceneById(id: string): Observable<Scene> {
    return this.http.get<Scene>(`http://localhost:8080/api/scenes/${id}`);
  }

  createScene(payload: SceneCreate): Observable<Scene> {
    return this.http.post<Scene>('http://localhost:8080/api/scenes', payload);
  }

  updateScene(id: string, payload: SceneCreate): Observable<Scene> {
    return this.http.put<Scene>(`http://localhost:8080/api/scenes/${id}`, payload);
  }

  deleteScene(id: string): Observable<void> {
    return this.http.delete<void>(`http://localhost:8080/api/scenes/${id}`);
  }

  search(q: string): Observable<Campaign[]> {
    const params = new HttpParams().set('q', q);
    return this.http.get<Campaign[]>(`${this.apiUrl}/search`, { params });
  }
}
