import { test, expect } from '@playwright/test';
import {
  seedLoreWithFolder,
  seedTemplate,
  deleteLore,
  getPagesForLore,
  type SeededLore,
  type SeededTemplate,
} from '../../fixtures/api';

test.describe('Page creation', () => {
  let seeded: SeededLore;
  let template: SeededTemplate;

  test.beforeEach(async ({ request }) => {
    seeded = await seedLoreWithFolder(request);
    template = await seedTemplate(request, {
      loreId: seeded.id,
      defaultNodeId: seeded.rootFolderId,
      fieldNames: ['Apparence', 'Motivation'],
    });
  });

  test.afterEach(async ({ request }) => {
    if (seeded?.id) await deleteLore(request, seeded.id);
  });

  test('creates an empty page from a template and redirects to edit', async ({ page, request }) => {
    const pageTitle = `Maître Eldrin ${Date.now()}`;

    await page.goto(`/lore/${seeded.id}/pages/create`);

    await expect(page.getByRole('heading', { name: /Créer une nouvelle Page/i })).toBeVisible();

    await page.getByLabel(/Titre de la page/i).fill(pageTitle);

    await page.locator('.template-card', { hasText: template.name }).click();
    await expect(page.locator('.template-card.selected', { hasText: template.name })).toBeVisible();

    await expect(page.locator('#page-node')).toHaveValue(seeded.rootFolderId);

    await page.getByRole('button', { name: /^Créer la page$/i }).click();

    await expect(page).toHaveURL(new RegExp(`/lore/${seeded.id}/pages/[^/]+/edit$`));

    const pages = await getPagesForLore(request, seeded.id);
    const created = pages.find((p) => p.title === pageTitle);
    expect(created).toBeDefined();
    expect(created?.templateId).toBe(template.id);
    expect(created?.nodeId).toBe(seeded.rootFolderId);
  });

  test('submit is disabled until title, template and folder are set', async ({ page }) => {
    await page.goto(`/lore/${seeded.id}/pages/create`);

    const submit = page.getByRole('button', { name: /^Créer la page$/i });
    await expect(submit).toBeDisabled();

    await page.getByLabel(/Titre de la page/i).fill('Un titre');
    await expect(submit).toBeDisabled();

    await page.locator('.template-card', { hasText: template.name }).click();
    await expect(submit).toBeEnabled();
  });

  test('entering on a folder-scoped route preselects that folder', async ({ page, request }) => {
    const pageTitle = `Page scoped ${Date.now()}`;

    const secondFolderRes = await request.post('/api/lore-nodes', {
      data: { loreId: seeded.id, name: 'Autre dossier', icon: 'folder', description: '' },
    });
    expect(secondFolderRes.ok()).toBeTruthy();
    const secondFolderId = (await secondFolderRes.json()).id;

    await page.goto(`/lore/${seeded.id}/nodes/${secondFolderId}/pages/create`);

    const nodeSelect = page.locator('#page-node');
    await expect(nodeSelect).toHaveValue(secondFolderId);
    await expect(nodeSelect).toBeDisabled();

    await page.getByLabel(/Titre de la page/i).fill(pageTitle);
    await page.locator('.template-card', { hasText: template.name }).click();
    await page.getByRole('button', { name: /^Créer la page$/i }).click();

    await expect(page).toHaveURL(new RegExp(`/lore/${seeded.id}/pages/[^/]+/edit$`));

    const pages = await getPagesForLore(request, seeded.id);
    expect(pages.find((p) => p.title === pageTitle)?.nodeId).toBe(secondFolderId);
  });
});
