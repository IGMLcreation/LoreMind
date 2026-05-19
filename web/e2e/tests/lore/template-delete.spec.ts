import { test, expect } from '@playwright/test';
import {
  seedLoreWithFolder,
  seedTemplate,
  deleteLore,
  getTemplatesForLore,
  type SeededLore,
  type SeededTemplate,
} from '../../fixtures/api';

test.describe('Template delete', () => {
  let seeded: SeededLore;
  let template: SeededTemplate;

  test.beforeEach(async ({ request }) => {
    seeded = await seedLoreWithFolder(request);
    template = await seedTemplate(request, {
      loreId: seeded.id,
      defaultNodeId: seeded.rootFolderId,
    });
  });

  test.afterEach(async ({ request }) => {
    if (seeded?.id) await deleteLore(request, seeded.id);
  });

  test('deletes the template after accepting confirm', async ({ page, request }) => {
    await page.goto(`/lore/${seeded.id}/templates/${template.id}`);
    await page.locator('.page-header .btn-danger').click();

    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await dialog.getByRole('button', { name: /^Supprimer$/i }).click();

    await expect(page).toHaveURL(new RegExp(`/lore/${seeded.id}$`));

    const templates = await getTemplatesForLore(request, seeded.id);
    expect(templates.find((t) => t.id === template.id)).toBeUndefined();
  });

  test('keeps the template when confirm is dismissed', async ({ page, request }) => {
    await page.goto(`/lore/${seeded.id}/templates/${template.id}`);
    await page.locator('.page-header .btn-danger').click();

    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await dialog.getByRole('button', { name: /^Annuler$/i }).click();

    // On reste sur l'écran d'édition (l'URL ne change pas).
    await expect(page).toHaveURL(new RegExp(`/lore/${seeded.id}/templates/${template.id}$`));

    const templates = await getTemplatesForLore(request, seeded.id);
    expect(templates.find((t) => t.id === template.id)).toBeDefined();
  });
});
