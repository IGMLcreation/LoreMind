import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { StoredFile } from './stored-file.model';

/**
 * Service HTTP pour les fichiers generiques (/api/files).
 * Pendant de {@link ImageService} pour les battlemaps : media (image/video) +
 * sidecar JSON Universal VTT, qui sortent du perimetre des images (mp4, json,
 * taille jusqu'a 128 Mo).
 */
@Injectable({ providedIn: 'root' })
export class StoredFileService {
  readonly apiBase = '';
  private apiUrl = `${this.apiBase}/api/files`;

  constructor(private http: HttpClient) {}

  /** Upload multipart/form-data. Le backend valide le MIME et la taille (128 Mo max). */
  upload(file: File): Observable<StoredFile> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<StoredFile>(this.apiUrl, form);
  }

  getById(id: string): Observable<StoredFile> {
    return this.http.get<StoredFile>(`${this.apiUrl}/${id}`);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  /** URL absolue du binaire (peu utile : la battlemap n'est pas affichee). */
  contentUrl(id: string): string {
    return `${this.apiUrl}/${id}/content`;
  }
}
