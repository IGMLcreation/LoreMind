import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ItemCatalog, ItemCatalogCreate } from './item-catalog.model';

/**
 * CRUD des catalogues d'objets (campagne) + génération IA. Mirroir de RandomTableService.
 */
@Injectable({ providedIn: 'root' })
export class ItemCatalogService {
  private apiUrl = '/api/item-catalogs';

  constructor(private http: HttpClient) {}

  getByCampaign(campaignId: string): Observable<ItemCatalog[]> {
    return this.http.get<ItemCatalog[]>(`${this.apiUrl}/campaign/${campaignId}`);
  }

  getById(id: string): Observable<ItemCatalog> {
    return this.http.get<ItemCatalog>(`${this.apiUrl}/${id}`);
  }

  create(payload: ItemCatalogCreate): Observable<ItemCatalog> {
    return this.http.post<ItemCatalog>(this.apiUrl, payload);
  }

  update(id: string, payload: ItemCatalog): Observable<ItemCatalog> {
    return this.http.put<ItemCatalog>(`${this.apiUrl}/${id}`, payload);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  /** Génère une PROPOSITION de catalogue via l'IA (non persistée) à préremplir. */
  generate(campaignId: string, description: string): Observable<ItemCatalog> {
    return this.http.post<ItemCatalog>(`${this.apiUrl}/generate`, { campaignId, description });
  }
}
