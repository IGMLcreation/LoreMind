import { test, expect } from '@playwright/test';
import { seedLoreWithFolder, deleteLore, type SeededLore } from '../../fixtures/api';

test.describe('Lore delete', () => {
  let seeded: SeededLore;

  test.beforeEach(async ({ request }) => {
    seeded = await seedLoreWithFolder(request);
  });

  test.afterEach(async ({ request }) => {
    // Best-effort cleanup — ne fait rien si déjà supprimé par le test.
    if (seeded?.id) await deleteLore(request, seeded.id);
  });

  test('deletes a lore after accepting the confirm and redirects to the list', async ({
    page,
    request,
  }) => {
    await page.goto(`/lore/${seeded.id}`);
    await page.getByRole('button', { name: /^Supprimer$/i }).first().click();

    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await expect(dialog).toContainText(seeded.name);
    // Lore contient un dossier seedé : le récapitulatif doit l'indiquer.
    await expect(dialog).toContainText(/1 dossier/i);

    await dialog.getByRole('button', { name: /^Supprimer$/i }).click();

    // Attente du retour sur la liste des lores.
    await expect(page).toHaveURL(/\/lore$/);

    const res = await request.get(`/api/lores/${seeded.id}`);
    expect(res.status()).toBe(404);
  });

  test('keeps the lore when the confirm is dismissed', async ({ page, request }) => {
    await page.goto(`/lore/${seeded.id}`);
    await page.getByRole('button', { name: /^Supprimer$/i }).first().click();

    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await dialog.getByRole('button', { name: /^Annuler$/i }).click();

    // On reste sur le détail, le titre du lore est toujours visible.
    await expect(page.locator('.detail-header h1')).toHaveText(seeded.name);

    const res = await request.get(`/api/lores/${seeded.id}`);
    expect(res.ok()).toBeTruthy();
  });
});
