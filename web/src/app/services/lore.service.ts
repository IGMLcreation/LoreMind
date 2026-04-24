import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { shareReplay, tap } from 'rxjs/operators';
import { Lore, LoreCreate, LoreNode, LoreNodeCreate } from './lore.model';

/** Compte des entités qui seront supprimées en cascade avec un dossier. */
export interface LoreNodeDeletionImpact {
  /** Sous-dossiers (récursif, sans compter le dossier racine lui-même). */
  folders: number;
  /** Pages dans l'ensemble du sous-arbre. */
  pages: number;
}

/** Compte des entités qui seront supprimées / détachées en cascade avec un Lore. */
export interface LoreDeletionImpact {
  folders: number;
  pages: number;
  templates: number;
  /** Campagnes qui perdront leur référence au Lore (mais resteront présentes). */
  detachedCampaigns: number;
}

/**
 * Service HTTP pour la gestion des Lores.
 * Port de sortie vers le Backend Java (Architecture Hexagonale).
 *
 * Les lectures agrégées par la sidebar (getAllLores, getLoreById, getLoreNodes)
 * sont mises en cache via `shareReplay(1)` pour éviter 5 fetchs redondants à
 * chaque navigation interne. Toute mutation (create/update/delete) invalide
 * l'ensemble du cache du service.
 */
@Injectable({
  providedIn: 'root'
})
export class LoreService {
  private apiUrl = '/api/lores';
  private nodesUrl = '/api/lore-nodes';

  private allLoresCache: Observable<Lore[]> | null = null;
  private loreByIdCache = new Map<string, Observable<Lore>>();
  private nodesByLoreIdCache = new Map<string, Observable<LoreNode[]>>();

  constructor(private http: HttpClient) {}

  /** Vide tous les caches de lecture — appelé après toute mutation. */
  private invalidate(): void {
    this.allLoresCache = null;
    this.loreByIdCache.clear();
    this.nodesByLoreIdCache.clear();
  }

  getAllLores(): Observable<Lore[]> {
    if (!this.allLoresCache) {
      this.allLoresCache = this.http.get<Lore[]>(this.apiUrl).pipe(
        tap({ error: () => (this.allLoresCache = null) }),
        shareReplay(1)
      );
    }
    return this.allLoresCache;
  }

  getLoreById(id: string): Observable<Lore> {
    let obs = this.loreByIdCache.get(id);
    if (!obs) {
      obs = this.http.get<Lore>(`${this.apiUrl}/${id}`).pipe(
        tap({ error: () => this.loreByIdCache.delete(id) }),
        shareReplay(1)
      );
      this.loreByIdCache.set(id, obs);
    }
    return obs;
  }

  createLore(lore: LoreCreate): Observable<Lore> {
    return this.http.post<Lore>(this.apiUrl, lore).pipe(tap(() => this.invalidate()));
  }

  updateLore(id: string, lore: LoreCreate): Observable<Lore> {
    return this.http.put<Lore>(`${this.apiUrl}/${id}`, lore).pipe(tap(() => this.invalidate()));
  }

  deleteLore(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`).pipe(tap(() => this.invalidate()));
  }

  getLoreDeletionImpact(id: string): Observable<LoreDeletionImpact> {
    return this.http.get<LoreDeletionImpact>(`${this.apiUrl}/${id}/deletion-impact`);
  }

  getLoreNodes(loreId: string): Observable<LoreNode[]> {
    let obs = this.nodesByLoreIdCache.get(loreId);
    if (!obs) {
      obs = this.http.get<LoreNode[]>(`${this.nodesUrl}?loreId=${loreId}`).pipe(
        tap({ error: () => this.nodesByLoreIdCache.delete(loreId) }),
        shareReplay(1)
      );
      this.nodesByLoreIdCache.set(loreId, obs);
    }
    return obs;
  }

  getLoreNodeById(id: string): Observable<LoreNode> {
    return this.http.get<LoreNode>(`${this.nodesUrl}/${id}`);
  }

  createLoreNode(node: LoreNodeCreate): Observable<LoreNode> {
    return this.http.post<LoreNode>(this.nodesUrl, node).pipe(tap(() => this.invalidate()));
  }

  /** PUT complet — envoie l'objet entier au backend (qui attend un LoreNodeDTO). */
  updateLoreNode(id: string, node: LoreNode): Observable<LoreNode> {
    return this.http.put<LoreNode>(`${this.nodesUrl}/${id}`, node).pipe(tap(() => this.invalidate()));
  }

  deleteLoreNode(id: string): Observable<void> {
    return this.http.delete<void>(`${this.nodesUrl}/${id}`).pipe(tap(() => this.invalidate()));
  }

  getLoreNodeDeletionImpact(id: string): Observable<LoreNodeDeletionImpact> {
    return this.http.get<LoreNodeDeletionImpact>(`${this.nodesUrl}/${id}/deletion-impact`);
  }

  searchLores(q: string): Observable<Lore[]> {
    const params = new HttpParams().set('q', q);
    return this.http.get<Lore[]>(`${this.apiUrl}/search`, { params });
  }

  searchLoreNodes(q: string): Observable<LoreNode[]> {
    const params = new HttpParams().set('q', q);
    return this.http.get<LoreNode[]>(`${this.nodesUrl}/search`, { params });
  }
}
