import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SessionEntry, SessionEntryInput } from './session-entry.model';

/**
 * Service HTTP pour le journal d'une Session.
 * Endpoints imbriqués : /api/sessions/{sessionId}/entries.
 */
@Injectable({
  providedIn: 'root'
})
export class SessionEntryService {
  private base(sessionId: string): string {
    return `/api/sessions/${sessionId}/entries`;
  }

  constructor(private http: HttpClient) {}

  getEntries(sessionId: string): Observable<SessionEntry[]> {
    return this.http.get<SessionEntry[]>(this.base(sessionId));
  }

  createEntry(sessionId: string, input: SessionEntryInput): Observable<SessionEntry> {
    return this.http.post<SessionEntry>(this.base(sessionId), input);
  }

  updateEntry(sessionId: string, entryId: string, input: SessionEntryInput): Observable<SessionEntry> {
    return this.http.put<SessionEntry>(`${this.base(sessionId)}/${entryId}`, input);
  }

  deleteEntry(sessionId: string, entryId: string): Observable<void> {
    return this.http.delete<void>(`${this.base(sessionId)}/${entryId}`);
  }
}
