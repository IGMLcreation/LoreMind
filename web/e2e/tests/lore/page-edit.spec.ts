import { test, expect } from '@playwright/test';
import {
  seedLoreWithFolder,
  seedTemplate,
  seedPage,
  deleteLore,
  getPageById,
  type SeededLore,
  type SeededTemplate,
  type SeededPage,
} from '../../fixtures/api';

test.describe('Page edit', () => {
  let seeded: SeededLore;
  let template: SeededTemplate;
  let pageEntity: SeededPage;

  test.beforeEach(async ({ request }) => {
    seeded = await seedLoreWithFolder(request);
    template = await seedTemplate(request, {
      loreId: seeded.id,
      defaultNodeId: seeded.rootFolderId,
      fieldNames: ['Apparence', 'Motivation'],
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

  test('edits title, dynamic fields, notes and persists to API', async ({ page, request }) => {
    const newTitle = `Maître Eldrin ${Date.now()}`;
    const apparence = 'Vieil homme au regard perçant.';
    const motivation = 'Protéger la cité à tout prix.';
    const notes = 'MJ : il connaît le secret du roi.';

    await page.goto(`/lore/${seeded.id}/pages/${pageEntity.id}/edit`);

    await expect(page.locator('input[name="title"]')).toHaveValue(pageEntity.title);

    await page.locator('input[name="title"]').fill(newTitle);
    await page.getByPlaceholder('Valeur pour Apparence...').fill(apparence);
    await page.getByPlaceholder('Valeur pour Motivation...').fill(motivation);
    await page.locator('textarea[name="notes"]').fill(notes);

    await page.getByRole('button', { name: /^Sauvegarder$/i }).click();

    await expect(page).toHaveURL(new RegExp(`/lore/${seeded.id}/pages/${pageEntity.id}$`));

    const persisted = await getPageById(request, pageEntity.id);
    expect(persisted.title).toBe(newTitle);
    expect(persisted.values?.['Apparence']).toBe(apparence);
    expect(persisted.values?.['Motivation']).toBe(motivation);
    expect(persisted.notes).toBe(notes);
  });

  test('adds a tag via chips input and persists it', async ({ page, request }) => {
    await page.goto(`/lore/${seeded.id}/pages/${pageEntity.id}/edit`);

    const chipsInput = page.locator('app-chips-input input');
    await chipsInput.fill('dangereux');
    await chipsInput.press('Enter');
    await chipsInput.fill('ancien');
    await chipsInput.press('Enter');

    await expect(page.locator('app-chips-input').getByText('dangereux')).toBeVisible();
    await expect(page.locator('app-chips-input').getByText('ancien')).toBeVisible();

    await page.getByRole('button', { name: /^Sauvegarder$/i }).click();
    await expect(page).toHaveURL(new RegExp(`/lore/${seeded.id}/pages/${pageEntity.id}$`));

    const persisted = await getPageById(request, pageEntity.id);
    expect(persisted.tags).toEqual(expect.arrayContaining(['dangereux', 'ancien']));
  });

  test('save button is disabled when title is empty', async ({ page }) => {
    await page.goto(`/lore/${seeded.id}/pages/${pageEntity.id}/edit`);

    const titleInput = page.locator('input[name="title"]');
    const saveBtn = page.getByRole('button', { name: /^Sauvegarder$/i });

    await expect(saveBtn).toBeEnabled();
    await titleInput.fill('');
    await expect(saveBtn).toBeDisabled();
    await titleInput.fill('   ');
    await expect(saveBtn).toBeDisabled();
    await titleInput.fill('OK');
    await expect(saveBtn).toBeEnabled();
  });
});
