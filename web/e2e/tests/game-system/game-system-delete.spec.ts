import { test, expect } from '@playwright/test';
import {
  seedGameSystem,
  deleteGameSystem,
  type SeededGameSystem,
} from '../../fixtures/api';

test.describe('GameSystem delete', () => {
  let gs: SeededGameSystem;

  test.beforeEach(async ({ request }) => {
    gs = await seedGameSystem(request);
  });

  test.afterEach(async ({ request }) => {
    // Best-effort cleanup — ne fait rien si deja supprime par le test.
    if (gs?.id) await deleteGameSystem(request, gs.id);
  });

  test('deletes a game system after confirming and removes it from the list', async ({
    page,
    request,
  }) => {
    await page.goto('/game-systems');

    const card = page.locator('.gs-card', { hasText: gs.name });
    await expect(card).toBeVisible();

    // Bouton corbeille dans le coin de la carte du systeme seede.
    await card.locator('.icon-btn').click();

    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await expect(dialog).toContainText(gs.name);

    await dialog.getByRole('button', { name: /^Supprimer$/i }).click();

    // La carte disparait apres reload de la liste.
    await expect(page.locator('.gs-card', { hasText: gs.name })).toHaveCount(0);

    const res = await request.get(`/api/game-systems/${gs.id}`);
    expect(res.status()).toBe(404);
  });

  test('keeps the game system when cancel is clicked', async ({ page, request }) => {
    await page.goto('/game-systems');

    const card = page.locator('.gs-card', { hasText: gs.name });
    await expect(card).toBeVisible();
    await card.locator('.icon-btn').click();

    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await dialog.getByRole('button', { name: /^Annuler$/i }).click();

    // La carte est toujours la, le systeme est toujours en base.
    await expect(page.locator('.gs-card', { hasText: gs.name })).toBeVisible();
    const res = await request.get(`/api/game-systems/${gs.id}`);
    expect(res.ok()).toBeTruthy();
  });
});
