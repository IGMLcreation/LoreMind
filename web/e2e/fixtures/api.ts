import { APIRequestContext, expect } from '@playwright/test';

export interface SeededLore {
  id: string;
  name: string;
  rootFolderId: string;
  rootFolderName: string;
}

/**
 * Seed un Lore + un dossier racine via l'API backend.
 * Les noms sont uniques (timestamp + random) pour éviter les collisions en parallèle.
 */
export async function seedLoreWithFolder(request: APIRequestContext): Promise<SeededLore> {
  const suffix = `${Date.now()}-${Math.floor(Math.random() * 10000)}`;
  const loreName = `E2E Lore ${suffix}`;
  const folderName = `E2E Folder ${suffix}`;

  const loreRes = await request.post('/api/lores', {
    data: { name: loreName, description: 'Seeded by Playwright' },
  });
  expect(loreRes.ok(), `POST /api/lores -> ${loreRes.status()}`).toBeTruthy();
  const lore = await loreRes.json();

  const folderRes = await request.post('/api/lore-nodes', {
    data: { loreId: lore.id, name: folderName, icon: 'folder', description: '' },
  });
  expect(folderRes.ok(), `POST /api/lore-nodes -> ${folderRes.status()}`).toBeTruthy();
  const folder = await folderRes.json();

  return { id: lore.id, name: loreName, rootFolderId: folder.id, rootFolderName: folderName };
}

/** Cleanup best-effort — n'échoue pas si déjà supprimé. */
export async function deleteLore(request: APIRequestContext, loreId: string): Promise<void> {
  await request.delete(`/api/lores/${loreId}`).catch(() => undefined);
}

export async function getTemplatesForLore(
  request: APIRequestContext,
  loreId: string,
): Promise<Array<{ id: string; name: string }>> {
  const res = await request.get(`/api/templates?loreId=${loreId}`);
  expect(res.ok(), `GET /api/templates -> ${res.status()}`).toBeTruthy();
  return res.json();
}

export interface SeededTemplate {
  id: string;
  name: string;
}

export async function seedTemplate(
  request: APIRequestContext,
  opts: { loreId: string; defaultNodeId: string; name?: string; fieldNames?: string[] },
): Promise<SeededTemplate> {
  const templateName = opts.name ?? `E2E Template ${Date.now()}-${Math.floor(Math.random() * 10000)}`;
  const fields = (opts.fieldNames ?? ['Nom', 'Description']).map((name) => ({ name, type: 'TEXT' }));

  const res = await request.post('/api/templates', {
    data: {
      loreId: opts.loreId,
      name: templateName,
      description: 'Seeded by Playwright',
      defaultNodeId: opts.defaultNodeId,
      fields,
    },
  });
  expect(res.ok(), `POST /api/templates -> ${res.status()}`).toBeTruthy();
  const tpl = await res.json();
  return { id: tpl.id, name: templateName };
}

export async function deleteCampaign(request: APIRequestContext, campaignId: string): Promise<void> {
  await request.delete(`/api/campaigns/${campaignId}`).catch(() => undefined);
}

export interface SeededCampaign {
  id: string;
  name: string;
}

export async function seedCampaign(
  request: APIRequestContext,
  opts: { name?: string; loreId?: string | null; playerCount?: number } = {},
): Promise<SeededCampaign> {
  const name = opts.name ?? `E2E Campaign ${Date.now()}-${Math.floor(Math.random() * 10000)}`;
  const res = await request.post('/api/campaigns', {
    data: {
      name,
      description: 'Seeded by Playwright',
      playerCount: opts.playerCount ?? 4,
      loreId: opts.loreId ?? null,
      gameSystemId: null,
    },
  });
  expect(res.ok(), `POST /api/campaigns -> ${res.status()}`).toBeTruthy();
  const c = await res.json();
  return { id: c.id, name };
}

export interface SeededArc {
  id: string;
  name: string;
}

export async function seedArc(
  request: APIRequestContext,
  opts: { campaignId: string; name?: string; order?: number },
): Promise<SeededArc> {
  const name = opts.name ?? `E2E Arc ${Date.now()}-${Math.floor(Math.random() * 10000)}`;
  const res = await request.post('/api/arcs', {
    data: {
      campaignId: opts.campaignId,
      name,
      description: '',
      order: opts.order ?? 1,
    },
  });
  expect(res.ok(), `POST /api/arcs -> ${res.status()}`).toBeTruthy();
  const a = await res.json();
  return { id: a.id, name };
}

export interface SeededChapter {
  id: string;
  name: string;
}

export async function seedChapter(
  request: APIRequestContext,
  opts: { arcId: string; name?: string; order?: number },
): Promise<SeededChapter> {
  const name = opts.name ?? `E2E Chapter ${Date.now()}-${Math.floor(Math.random() * 10000)}`;
  const res = await request.post('/api/chapters', {
    data: { arcId: opts.arcId, name, description: '', order: opts.order ?? 1 },
  });
  expect(res.ok(), `POST /api/chapters -> ${res.status()}`).toBeTruthy();
  const c = await res.json();
  return { id: c.id, name };
}

export async function getChapterById(
  request: APIRequestContext,
  chapterId: string,
): Promise<{
  id: string;
  name: string;
  description?: string;
  gmNotes?: string | null;
  playerObjectives?: string | null;
  narrativeStakes?: string | null;
}> {
  const res = await request.get(`/api/chapters/${chapterId}`);
  expect(res.ok(), `GET /api/chapters/${chapterId} -> ${res.status()}`).toBeTruthy();
  return res.json();
}

export interface SeededScene {
  id: string;
  name: string;
}

export async function seedScene(
  request: APIRequestContext,
  opts: { chapterId: string; name?: string; order?: number },
): Promise<SeededScene> {
  const name = opts.name ?? `E2E Scene ${Date.now()}-${Math.floor(Math.random() * 10000)}`;
  const res = await request.post('/api/scenes', {
    data: { chapterId: opts.chapterId, name, description: '', order: opts.order ?? 1 },
  });
  expect(res.ok(), `POST /api/scenes -> ${res.status()}`).toBeTruthy();
  const s = await res.json();
  return { id: s.id, name };
}

export async function getSceneById(
  request: APIRequestContext,
  sceneId: string,
): Promise<{
  id: string;
  name: string;
  description?: string;
  location?: string | null;
  timing?: string | null;
  atmosphere?: string | null;
  playerNarration?: string | null;
  gmSecretNotes?: string | null;
  choicesConsequences?: string | null;
  combatDifficulty?: string | null;
  enemies?: string | null;
  branches?: Array<{ label: string; targetSceneId: string; condition?: string }>;
}> {
  const res = await request.get(`/api/scenes/${sceneId}`);
  expect(res.ok(), `GET /api/scenes/${sceneId} -> ${res.status()}`).toBeTruthy();
  return res.json();
}

export async function getArcById(
  request: APIRequestContext,
  arcId: string,
): Promise<{
  id: string;
  name: string;
  description?: string;
  themes?: string | null;
  stakes?: string | null;
  gmNotes?: string | null;
  rewards?: string | null;
  resolution?: string | null;
}> {
  const res = await request.get(`/api/arcs/${arcId}`);
  expect(res.ok(), `GET /api/arcs/${arcId} -> ${res.status()}`).toBeTruthy();
  return res.json();
}

export async function getCampaigns(
  request: APIRequestContext,
): Promise<Array<{ id: string; name: string; loreId: string | null }>> {
  const res = await request.get('/api/campaigns');
  expect(res.ok(), `GET /api/campaigns -> ${res.status()}`).toBeTruthy();
  return res.json();
}

export async function getPagesForLore(
  request: APIRequestContext,
  loreId: string,
): Promise<Array<{ id: string; title: string; nodeId: string; templateId: string }>> {
  const res = await request.get(`/api/pages?loreId=${loreId}`);
  expect(res.ok(), `GET /api/pages -> ${res.status()}`).toBeTruthy();
  return res.json();
}

export interface SeededPage {
  id: string;
  title: string;
}

export async function seedPage(
  request: APIRequestContext,
  opts: { loreId: string; nodeId: string; templateId: string; title?: string },
): Promise<SeededPage> {
  const title = opts.title ?? `E2E Page ${Date.now()}-${Math.floor(Math.random() * 10000)}`;
  const res = await request.post('/api/pages', {
    data: { loreId: opts.loreId, nodeId: opts.nodeId, templateId: opts.templateId, title },
  });
  expect(res.ok(), `POST /api/pages -> ${res.status()}`).toBeTruthy();
  const page = await res.json();
  return { id: page.id, title };
}

export async function getPageById(
  request: APIRequestContext,
  pageId: string,
): Promise<{
  id: string;
  title: string;
  nodeId: string;
  values?: Record<string, string>;
  tags?: string[];
  notes?: string;
}> {
  const res = await request.get(`/api/pages/${pageId}`);
  expect(res.ok(), `GET /api/pages/${pageId} -> ${res.status()}`).toBeTruthy();
  return res.json();
}

export async function getTemplateById(
  request: APIRequestContext,
  templateId: string,
): Promise<{
  id: string;
  name: string;
  description?: string;
  defaultNodeId?: string | null;
  fields: Array<{ name: string; type: string }>;
}> {
  const res = await request.get(`/api/templates/${templateId}`);
  expect(res.ok(), `GET /api/templates/${templateId} -> ${res.status()}`).toBeTruthy();
  return res.json();
}
