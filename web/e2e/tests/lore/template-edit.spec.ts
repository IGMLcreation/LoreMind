import { test, expect } from '@playwright/test';
import {
  seedLoreWithFolder,
  seedTemplate,
  deleteLore,
  getTemplateById,
  type SeededLore,
  type SeededTemplate,
} from '../../fixtures/api';

test.describe('Template edit', () => {
  let seeded: SeededLore;
  let template: SeededTemplate;

  test.beforeEach(async ({ request }) => {
    seeded = await seedLoreWithFolder(request);
    template = await seedTemplate(request, {
      loreId: seeded.id,
      defaultNodeId: seeded.rootFolderId,
      fieldNames: ['Nom', 'Description'],
    });
  });

  test.afterEach(async ({ request }) => {
    if (seeded?.id) await deleteLore(request, seeded.id);
  });

  test('form is prefilled with the current template data', async ({ page }) => {
    await page.goto(`/lore/${seeded.id}/templates/${template.id}`);

    await expect(page.getByLabel(/^Nom$/)).toHaveValue(template.name);
    await expect(page.getByLabel(/Dossier par défaut/i)).toHaveValue(seeded.rootFolderId);
    await expect(page.locator('.fields-list .field-chip', { hasText: 'Nom' })).toBeVisible();
    await expect(page.locator('.fields-list .field-chip', { hasText: 'Description' })).toBeVisible();
  });

  test('renames the template and persists to API', async ({ page, request }) => {
    const newName = `${template.name} renamed`;

    await page.goto(`/lore/${seeded.id}/templates/${template.id}`);

    const nameInput = page.getByLabel(/^Nom$/);
    await nameInput.fill(newName);
    await page.getByLabel(/Description/i).fill('Description mise à jour');

    await page.getByRole('button', { name: /^Sauvegarder$/i }).click();

    await expect(page).toHaveURL(new RegExp(`/lore/${seeded.id}$`));

    const persisted = await getTemplateById(request, template.id);
    expect(persisted.name).toBe(newName);
    expect(persisted.description).toBe('Description mise à jour');
  });

  test('adds a new field, removes an existing one, and persists the order', async ({ page, request }) => {
    await page.goto(`/lore/${seeded.id}/templates/${template.id}`);

    const addInput = page.getByPlaceholder('+ Ajouter un champ');
    await addInput.fill('Stats');
    await addInput.press('Enter');
    await expect(page.locator('.fields-list .field-chip', { hasText: 'Stats' })).toBeVisible();

    const descriptionRow = page.locator('.fields-list .field-row', { hasText: 'Description' });
    await descriptionRow.getByRole('button', { name: 'Supprimer' }).click();
    await expect(page.locator('.fields-list .field-chip', { hasText: 'Description' })).toHaveCount(0);

    await page.getByRole('button', { name: /^Sauvegarder$/i }).click();
    await expect(page).toHaveURL(new RegExp(`/lore/${seeded.id}$`));

    const persisted = await getTemplateById(request, template.id);
    const names = persisted.fields.map((f) => f.name);
    expect(names).toEqual(['Nom', 'Stats']);
  });
});
