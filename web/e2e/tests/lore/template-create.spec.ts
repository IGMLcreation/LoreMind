import { test, expect } from '@playwright/test';
import { seedLoreWithFolder, deleteLore, getTemplatesForLore, type SeededLore } from '../../fixtures/api';

test.describe('Template creation', () => {
  let seeded: SeededLore;

  test.beforeEach(async ({ request }) => {
    seeded = await seedLoreWithFolder(request);
  });

  test.afterEach(async ({ request }) => {
    if (seeded?.id) await deleteLore(request, seeded.id);
  });

  test('creates a template with default fields', async ({ page, request }) => {
    const templateName = `Auberge ${Date.now()}`;

    await page.goto(`/lore/${seeded.id}/templates/create`);

    await expect(page.getByRole('heading', { name: /Créer un nouveau Template/i })).toBeVisible();

    await page.getByLabel(/Nom du template/i).fill(templateName);
    await page.getByLabel('Description').fill('Template créé via E2E');
    await page.getByLabel(/Dossier par défaut/i).selectOption({ label: seeded.rootFolderName });

    await page.getByRole('button', { name: /^Créer le template$/i }).click();

    await expect(page).toHaveURL(new RegExp(`/lore/${seeded.id}$`));

    const templates = await getTemplatesForLore(request, seeded.id);
    expect(templates.map((t) => t.name)).toContain(templateName);
  });

  test('submit is disabled when name is empty', async ({ page }) => {
    await page.goto(`/lore/${seeded.id}/templates/create`);

    const submit = page.getByRole('button', { name: /^Créer le template$/i });
    await expect(submit).toBeDisabled();

    await page.getByLabel(/Nom du template/i).fill('Valid name');
    await page.getByLabel(/Dossier par défaut/i).selectOption({ label: seeded.rootFolderName });
    await expect(submit).toBeEnabled();
  });

  test('can add and remove a custom field before creating', async ({ page, request }) => {
    const templateName = `Artefact ${Date.now()}`;

    await page.goto(`/lore/${seeded.id}/templates/create`);

    await page.getByLabel(/Nom du template/i).fill(templateName);
    await page.getByLabel(/Dossier par défaut/i).selectOption({ label: seeded.rootFolderName });

    const addFieldInput = page.getByPlaceholder('+ Ajouter un champ');
    await addFieldInput.fill('Pouvoir');
    await addFieldInput.press('Enter');
    await expect(page.locator('.fields-list .field-chip', { hasText: 'Pouvoir' })).toBeVisible();

    await addFieldInput.fill('Origine');
    await addFieldInput.press('Enter');
    await expect(page.locator('.fields-list .field-chip', { hasText: 'Origine' })).toBeVisible();

    const origineRow = page.locator('.fields-list .field-row', { hasText: 'Origine' });
    await origineRow.getByRole('button', { name: 'Supprimer' }).click();
    await expect(page.locator('.fields-list .field-chip', { hasText: 'Origine' })).toHaveCount(0);

    await page.getByRole('button', { name: /^Créer le template$/i }).click();
    await expect(page).toHaveURL(new RegExp(`/lore/${seeded.id}$`));

    const templates = await getTemplatesForLore(request, seeded.id);
    expect(templates.find((t) => t.name === templateName)).toBeDefined();
  });
});
