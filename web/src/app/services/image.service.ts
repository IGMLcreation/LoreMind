import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Image } from './image.model';

/**
 * Service HTTP pour le Shared Kernel images.
 * Port de sortie vers le backend Java (/api/images).
 */
@Injectable({ providedIn: 'root' })
export class ImageService {
  /** Base absolue du backend — utile pour construire des URLs complètes (<img src>). */
  readonly apiBase = 'http://localhost:8080';
  private apiUrl = `${this.apiBase}/api/images`;

  constructor(private http: HttpClient) {}

  /**
   * Upload d'un fichier via multipart/form-data.
   * Le backend valide le MIME et la taille (10 Mo max).
   */
  upload(file: File): Observable<Image> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<Image>(this.apiUrl, form);
  }

  getById(id: string): Observable<Image> {
    return this.http.get<Image>(`${this.apiUrl}/${id}`);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  /**
   * Construit l'URL absolue du binaire d'une image.
   * Utilise par les balises <img src> dans les composants.
   */
  contentUrl(id: string): string {
    return `${this.apiUrl}/${id}/content`;
  }
}
