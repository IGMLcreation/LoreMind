export type ConversationRole = 'user' | 'assistant' | 'system';

export interface ConversationMessage {
  id?: string;
  role: ConversationRole;
  content: string;
  createdAt?: string;
}

export interface Conversation {
  id: string;
  title: string;
  loreId?: string | null;
  campaignId?: string | null;
  entityType?: string | null;
  entityId?: string | null;
  createdAt: string;
  updatedAt: string;
  messages?: ConversationMessage[];
}

/**
 * Filtre strict pour le listing sidebar. Fournir soit loreId soit campaignId.
 * entityType + entityId vont ensemble — tous deux null = niveau racine.
 */
export interface ConversationContext {
  loreId?: string | null;
  campaignId?: string | null;
  entityType?: 'page' | 'arc' | 'chapter' | 'scene' | 'character' | 'npc' | null;
  entityId?: string | null;
}

export interface CreateConversationPayload extends ConversationContext {
  title?: string;
}
