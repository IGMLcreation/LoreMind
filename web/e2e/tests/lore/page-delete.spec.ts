import { test, expect } from '@playwright/test';
import {
  seedLoreWithFolder,
  seedTemplate,
  seedPage,
  deleteLore,
  type SeededLore,
  type SeededTemplate,
  type SeededPage,
} from '../../fixtures/api';

test.describe('Page delete', () => {
  let seeded: SeededLore;
  let template: SeededTemplate;
  let pageEntity: SeededPage;

  test.beforeEach(async ({ request }) => {
    seeded = await seedLoreWithFolder(request);
    template = await seedTemplate(request, {
      loreId: seeded.id,
      defaultNodeId: seeded.rootFolderId,
    });
    pageEntity = await seedPage(request, {
      loreId: seeded.id,
      nodeId: seeded.rootFolderId,
      templateId: template.id,
    });
  });

  test.afterEach(async ({ request }) => {
    if (seeded?.id) await deleteLore(request, seeded.id);
  });

  test('deletes the page after accepting confirm', async ({ page, request }) => {
    page.on('dialog', (dialog) => dialog.accept());

    await page.goto(`/lore/${seeded.id}/pages/${pageEntity.id}/edit`);
    await page.getByRole('button', { name: /^Supprimer$/i }).click();

    // Le composant redirige vers la racine du Lore après suppression.
    await expect(page).toHaveURL(new RegExp(`/lore/${seeded.id}$`));

    const res = await request.get(`/api/pages/${pageEntity.id}`);
    expect(res.status()).toBe(404);
  });

  test('keeps the page when confirm is dismissed', async ({ page, request }) => {
    page.on('dialog', (dialog) => dialog.dismiss());

    await page.goto(`/lore/${seeded.id}/pages/${pageEntity.id}/edit`);
    await page.getByRole('button', { name: /^Supprimer$/i }).click();

    await expect(page).toHaveURL(new RegExp(`/lore/${seeded.id}/pages/${pageEntity.id}/edit$`));

    const res = await request.get(`/api/pages/${pageEntity.id}`);
    expect(res.ok()).toBeTruthy();
  });
});
