import { test, expect } from '@playwright/test';
import {
  seedGameSystem,
  deleteGameSystem,
  getGameSystemById,
  type SeededGameSystem,
} from '../../fixtures/api';

test.describe('GameSystem edit', () => {
  let gs: SeededGameSystem;

  test.beforeEach(async ({ request }) => {
    gs = await seedGameSystem(request, {
      description: 'Description initiale.',
      author: 'Auteur initial',
    });
  });

  test.afterEach(async ({ request }) => {
    if (gs?.id) await deleteGameSystem(request, gs.id);
  });

  test('form is prefilled with the game system data', async ({ page }) => {
    await page.goto(`/game-systems/${gs.id}/edit`);

    await expect(page.getByRole('heading', { name: /Éditer le système/i })).toBeVisible();
    await expect(page.getByLabel(/^Nom/i)).toHaveValue(gs.name);
    await expect(page.getByLabel(/Description courte/i)).toHaveValue('Description initiale.');
    await expect(page.getByLabel(/Auteur/i)).toHaveValue('Auteur initial');
  });

  test('edits name and description and persists them to API', async ({ page, request }) => {
    const newName = `${gs.name} renamed`;
    const newDescription = 'Description mise à jour par le test.';

    await page.goto(`/game-systems/${gs.id}/edit`);

    // Attente que le formulaire soit prerempli avant de fill — sinon le load
    // async ecrase les valeurs filled (cf. bug arc-edit corrige).
    await expect(page.getByLabel(/^Nom/i)).toHaveValue(gs.name);

    await page.getByLabel(/^Nom/i).fill(newName);
    await page.getByLabel(/Description courte/i).fill(newDescription);

    await page.getByRole('button', { name: /^Sauvegarder$/i }).click();

    // Retour a la liste apres save.
    await expect(page).toHaveURL(/\/game-systems$/);

    const persisted = await getGameSystemById(request, gs.id);
    expect(persisted.name).toBe(newName);
    expect(persisted.description).toBe(newDescription);
  });

  test('save button is disabled when name is cleared', async ({ page }) => {
    await page.goto(`/game-systems/${gs.id}/edit`);
    await expect(page.getByLabel(/^Nom/i)).toHaveValue(gs.name);

    const nameField = page.getByLabel(/^Nom/i);
    const saveBtn = page.getByRole('button', { name: /^Sauvegarder$/i });

    await expect(saveBtn).toBeEnabled();
    await nameField.fill('');
    await expect(saveBtn).toBeDisabled();
    await nameField.fill('OK');
    await expect(saveBtn).toBeEnabled();
  });
});
