import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  Conversation,
  ConversationContext,
  ConversationMessage,
  CreateConversationPayload,
} from './conversation.model';

/**
 * Service HTTP des conversations persistees.
 *
 * Le streaming (chat/stream) reste pris en charge par AiChatService. Ce
 * service ne gere que la persistance (metadonnees + messages) et
 * l'auto-titre declenche apres le 1er echange.
 */
@Injectable({ providedIn: 'root' })
export class ConversationService {
  private readonly apiUrl = 'http://localhost:8080/api/conversations';

  constructor(private http: HttpClient) {}

  list(ctx: ConversationContext): Observable<Conversation[]> {
    let params = new HttpParams();
    if (ctx.loreId) params = params.set('loreId', ctx.loreId);
    if (ctx.campaignId) params = params.set('campaignId', ctx.campaignId);
    if (ctx.entityType) params = params.set('entityType', ctx.entityType);
    if (ctx.entityId) params = params.set('entityId', ctx.entityId);
    return this.http.get<Conversation[]>(this.apiUrl, { params });
  }

  getById(id: string): Observable<Conversation> {
    return this.http.get<Conversation>(`${this.apiUrl}/${id}`);
  }

  create(payload: CreateConversationPayload): Observable<Conversation> {
    return this.http.post<Conversation>(this.apiUrl, payload);
  }

  rename(id: string, title: string): Observable<void> {
    return this.http.patch<void>(`${this.apiUrl}/${id}/title`, { title });
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  appendMessage(
    id: string,
    role: 'user' | 'assistant' | 'system',
    content: string,
  ): Observable<ConversationMessage> {
    return this.http.post<ConversationMessage>(`${this.apiUrl}/${id}/messages`, {
      role,
      content,
    });
  }

  /** Declenche la generation auto du titre cote Brain. */
  autoTitle(id: string): Observable<{ title: string }> {
    return this.http.post<{ title: string }>(`${this.apiUrl}/${id}/auto-title`, {});
  }
}
