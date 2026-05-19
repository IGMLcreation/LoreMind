import { test, expect, Page } from '@playwright/test';
import {
  seedGameSystem,
  deleteGameSystem,
  type SeededGameSystem,
} from '../../fixtures/api';

/**
 * Tests du composant <app-template-fields-editor> dans le contexte GameSystem.
 *
 * Le composant est instancie DEUX fois sur la page d'edition d'un GameSystem
 * (une fois pour PJ "characterTemplate", une fois pour PNJ "npcTemplate"), donc
 * les selecteurs doivent etre scopes a l'instance ciblee. On utilise un helper
 * `tfe(label)` qui renvoie le locator de l'editeur correspondant au titre.
 */
test.describe('GameSystem template fields editor (PJ / PNJ)', () => {
  let gs: SeededGameSystem;

  test.beforeEach(async ({ request }) => {
    gs = await seedGameSystem(request);
  });

  test.afterEach(async ({ request }) => {
    if (gs?.id) await deleteGameSystem(request, gs.id);
  });

  /** Helper : retourne le locator de l'editeur de templates par son label. */
  const tfe = (page: Page, label: 'PJ' | 'PNJ') =>
    page.locator('.tfe').filter({ hasText: `Champs de la fiche ${label}` });

  test('adds a suggested field to the PJ template and persists it', async ({ page, request }) => {
    await page.goto(`/game-systems/${gs.id}/edit`);
    await expect(page.getByLabel(/^Nom/i)).toHaveValue(gs.name);

    const pjEditor = tfe(page, 'PJ');
    await expect(pjEditor).toBeVisible();

    // Ajout de "Histoire" via la chip suggeree.
    await pjEditor.locator('.tfe-add .chip', { hasText: 'Histoire' }).click();

    // Une row apparait avec le nom prerempli.
    const row = pjEditor.locator('.tfe-item').first();
    await expect(row).toBeVisible();
    await expect(row.locator('.tfe-name')).toHaveValue('Histoire');

    // Save → retour a la liste.
    await page.getByRole('button', { name: /^Enregistrer$/i }).click();
    await expect(page).toHaveURL(/\/game-systems$/);

    // Verification API : le champ est bien dans characterTemplate.
    const persisted = await request.get(`/api/game-systems/${gs.id}`).then((r) => r.json());
    expect(persisted.characterTemplate).toEqual(
      expect.arrayContaining([expect.objectContaining({ name: 'Histoire' })]),
    );
    // npcTemplate non touche (toujours vide).
    expect(persisted.npcTemplate ?? []).toHaveLength(0);
  });

  test('adds a custom NUMBER field via "Nombre" chip', async ({ page }) => {
    await page.goto(`/game-systems/${gs.id}/edit`);
    await expect(page.getByLabel(/^Nom/i)).toHaveValue(gs.name);

    const pjEditor = tfe(page, 'PJ');
    await pjEditor.locator('.tfe-add .chip-custom', { hasText: 'Nombre' }).click();

    const row = pjEditor.locator('.tfe-item').first();
    await expect(row).toBeVisible();
    // Champ vide, nom a remplir, type "NUMBER" pre-selectionne dans le select.
    await expect(row.locator('.tfe-name')).toHaveValue('');
    await expect(row.locator('.tfe-type')).toHaveValue('NUMBER');

    await row.locator('.tfe-name').fill('Points de vie');
    await expect(row.locator('.tfe-name')).toHaveValue('Points de vie');
  });

  test('PJ and PNJ editors are independent (adding to one does not affect the other)', async ({
    page,
    request,
  }) => {
    await page.goto(`/game-systems/${gs.id}/edit`);
    await expect(page.getByLabel(/^Nom/i)).toHaveValue(gs.name);

    await tfe(page, 'PJ').locator('.tfe-add .chip', { hasText: 'Histoire' }).click();
    await tfe(page, 'PNJ').locator('.tfe-add .chip', { hasText: 'Motivation' }).click();

    await expect(tfe(page, 'PJ').locator('.tfe-item')).toHaveCount(1);
    await expect(tfe(page, 'PNJ').locator('.tfe-item')).toHaveCount(1);
    await expect(tfe(page, 'PJ').locator('.tfe-item').first().locator('.tfe-name')).toHaveValue('Histoire');
    await expect(tfe(page, 'PNJ').locator('.tfe-item').first().locator('.tfe-name')).toHaveValue('Motivation');

    await page.getByRole('button', { name: /^Enregistrer$/i }).click();
    await expect(page).toHaveURL(/\/game-systems$/);

    const persisted = await request.get(`/api/game-systems/${gs.id}`).then((r) => r.json());
    expect(persisted.characterTemplate).toEqual(
      expect.arrayContaining([expect.objectContaining({ name: 'Histoire' })]),
    );
    expect(persisted.npcTemplate).toEqual(
      expect.arrayContaining([expect.objectContaining({ name: 'Motivation' })]),
    );
  });

  test('removes a field from the template', async ({ page }) => {
    await page.goto(`/game-systems/${gs.id}/edit`);
    await expect(page.getByLabel(/^Nom/i)).toHaveValue(gs.name);

    const pjEditor = tfe(page, 'PJ');
    await pjEditor.locator('.tfe-add .chip', { hasText: 'Histoire' }).click();
    await pjEditor.locator('.tfe-add .chip', { hasText: 'Apparence' }).click();

    await expect(pjEditor.locator('.tfe-item')).toHaveCount(2);

    // Supprime le premier champ (Histoire) via son btn-remove.
    await pjEditor.locator('.tfe-item').first().locator('.btn-remove').click();
    await expect(pjEditor.locator('.tfe-item')).toHaveCount(1);
    await expect(pjEditor.locator('.tfe-item').first().locator('.tfe-name')).toHaveValue('Apparence');
  });

  test('reorders fields with the up arrow button', async ({ page }) => {
    await page.goto(`/game-systems/${gs.id}/edit`);
    await expect(page.getByLabel(/^Nom/i)).toHaveValue(gs.name);

    const pjEditor = tfe(page, 'PJ');
    await pjEditor.locator('.tfe-add .chip', { hasText: 'Histoire' }).click();
    await pjEditor.locator('.tfe-add .chip', { hasText: 'Apparence' }).click();

    // Ordre initial : Histoire, Apparence.
    let rows = pjEditor.locator('.tfe-item');
    await expect(rows.nth(0).locator('.tfe-name')).toHaveValue('Histoire');
    await expect(rows.nth(1).locator('.tfe-name')).toHaveValue('Apparence');

    // Monte Apparence d'un cran.
    await rows.nth(1).locator('.btn-arrow').first().click();

    rows = pjEditor.locator('.tfe-item');
    await expect(rows.nth(0).locator('.tfe-name')).toHaveValue('Apparence');
    await expect(rows.nth(1).locator('.tfe-name')).toHaveValue('Histoire');
  });

  test('disables a suggested chip after the field has been added', async ({ page }) => {
    await page.goto(`/game-systems/${gs.id}/edit`);
    await expect(page.getByLabel(/^Nom/i)).toHaveValue(gs.name);

    const pjEditor = tfe(page, 'PJ');
    const histoireChip = pjEditor.locator('.tfe-add .chip', { hasText: 'Histoire' });

    await expect(histoireChip).toBeEnabled();
    await histoireChip.click();
    await expect(histoireChip).toBeDisabled();
  });
});
