import { test, expect } from '@playwright/test';
import {
  seedGameSystem,
  deleteGameSystem,
  type SeededGameSystem,
} from '../../fixtures/api';

test.describe('GameSystem rule sections editor', () => {
  let gs: SeededGameSystem;

  test.beforeEach(async ({ request }) => {
    // On part d'un GameSystem vide (pas de regles seedees) — chaque test gere
    // ses propres ajouts pour eviter les couplages.
    gs = await seedGameSystem(request);
  });

  test.afterEach(async ({ request }) => {
    if (gs?.id) await deleteGameSystem(request, gs.id);
  });

  test('adds a suggested section, fills it, and persists it', async ({ page, request }) => {
    const sectionContent = 'Initiative à d20, action+bonus+mouvement, dégâts par dés.';

    await page.goto(`/game-systems/${gs.id}/edit`);
    // Attendre le chargement du form (nom prerempli).
    await expect(page.getByLabel(/^Nom/i)).toHaveValue(gs.name);

    // Empty state visible tant qu'aucune section n'est ajoutee.
    await expect(page.locator('.section-list .empty-hint')).toBeVisible();

    // Ajout via la chip suggeree "Combat".
    await page.locator('.add-row .chip', { hasText: 'Combat' }).click();

    // Une section-card est apparue avec titre "Combat" prerempli + textarea visible.
    const card = page.locator('.section-card').first();
    await expect(card).toBeVisible();
    await expect(card.locator('.section-title-input')).toHaveValue('Combat');
    await card.locator('.section-content').fill(sectionContent);

    // Save + retour a la liste.
    await page.getByRole('button', { name: /^Enregistrer$/i }).click();
    await expect(page).toHaveURL(/\/game-systems$/);

    // Verification cote API : le markdown contient bien la section + son contenu.
    const persisted = await request.get(`/api/game-systems/${gs.id}`).then((r) => r.json());
    expect(persisted.rulesMarkdown).toContain('## Combat');
    expect(persisted.rulesMarkdown).toContain(sectionContent);
  });

  test('disables a suggested chip after it has been used', async ({ page }) => {
    await page.goto(`/game-systems/${gs.id}/edit`);
    await expect(page.getByLabel(/^Nom/i)).toHaveValue(gs.name);

    const combatChip = page.locator('.add-row .chip', { hasText: 'Combat' });
    await expect(combatChip).toBeEnabled();

    await combatChip.click();

    // Apres ajout, la chip "Combat" est desactivee (suggestion deja utilisee).
    await expect(combatChip).toBeDisabled();
  });

  test('adds a custom blank section via "Autre…" and lets the user name it', async ({ page }) => {
    await page.goto(`/game-systems/${gs.id}/edit`);
    await expect(page.getByLabel(/^Nom/i)).toHaveValue(gs.name);

    await page.locator('.add-row .chip-custom', { hasText: /Autre/i }).click();

    // Section vierge ajoutee : titre vide, prete a remplir.
    const card = page.locator('.section-card').first();
    await expect(card).toBeVisible();
    const titleInput = card.locator('.section-title-input');
    await expect(titleInput).toHaveValue('');
    await titleInput.fill('Sorts');
    await expect(titleInput).toHaveValue('Sorts');
  });

  test('removes a section', async ({ page }) => {
    await page.goto(`/game-systems/${gs.id}/edit`);
    await expect(page.getByLabel(/^Nom/i)).toHaveValue(gs.name);

    await page.locator('.add-row .chip', { hasText: 'Combat' }).click();
    await page.locator('.add-row .chip', { hasText: 'Classes' }).click();

    await expect(page.locator('.section-card')).toHaveCount(2);

    // Supprime la premiere section (Combat).
    await page.locator('.section-card').first().locator('.btn-remove').click();
    await expect(page.locator('.section-card')).toHaveCount(1);
    await expect(page.locator('.section-card').first().locator('.section-title-input')).toHaveValue('Classes');
  });

  test('collapses and expands a section', async ({ page }) => {
    await page.goto(`/game-systems/${gs.id}/edit`);
    await expect(page.getByLabel(/^Nom/i)).toHaveValue(gs.name);

    await page.locator('.add-row .chip', { hasText: 'Combat' }).click();
    const card = page.locator('.section-card').first();

    // Par defaut deployee : textarea visible.
    await expect(card.locator('.section-content')).toBeVisible();

    // Clic sur le bouton collapse → textarea masquee.
    await card.locator('.btn-collapse').click();
    await expect(card.locator('.section-content')).toHaveCount(0);

    // Re-clic → re-deployee.
    await card.locator('.btn-collapse').click();
    await expect(card.locator('.section-content')).toBeVisible();
  });
});
