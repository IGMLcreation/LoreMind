import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Lore, LoreCreate, LoreNode, LoreNodeCreate } from './lore.model';

/**
 * Service HTTP pour la gestion des Lores.
 * Port de sortie vers le Backend Java (Architecture Hexagonale).
 */
@Injectable({
  providedIn: 'root'
})
export class LoreService {
  private apiUrl = 'http://localhost:8080/api/lores';
  private nodesUrl = 'http://localhost:8080/api/lore-nodes';

  constructor(private http: HttpClient) {}

  getAllLores(): Observable<Lore[]> {
    return this.http.get<Lore[]>(this.apiUrl);
  }

  getLoreById(id: string): Observable<Lore> {
    return this.http.get<Lore>(`${this.apiUrl}/${id}`);
  }

  createLore(lore: LoreCreate): Observable<Lore> {
    return this.http.post<Lore>(this.apiUrl, lore);
  }

  updateLore(id: string, lore: LoreCreate): Observable<Lore> {
    return this.http.put<Lore>(`${this.apiUrl}/${id}`, lore);
  }

  deleteLore(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  getLoreNodes(loreId: string): Observable<LoreNode[]> {
    return this.http.get<LoreNode[]>(`${this.nodesUrl}?loreId=${loreId}`);
  }

  getLoreNodeById(id: string): Observable<LoreNode> {
    return this.http.get<LoreNode>(`${this.nodesUrl}/${id}`);
  }

  createLoreNode(node: LoreNodeCreate): Observable<LoreNode> {
    return this.http.post<LoreNode>(this.nodesUrl, node);
  }

  /** PUT complet — envoie l'objet entier au backend (qui attend un LoreNodeDTO). */
  updateLoreNode(id: string, node: LoreNode): Observable<LoreNode> {
    return this.http.put<LoreNode>(`${this.nodesUrl}/${id}`, node);
  }

  deleteLoreNode(id: string): Observable<void> {
    return this.http.delete<void>(`${this.nodesUrl}/${id}`);
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
