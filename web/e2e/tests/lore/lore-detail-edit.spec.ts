import { test, expect } from '@playwright/test';
import { seedLoreWithFolder, deleteLore, getLoreById, type SeededLore } from '../../fixtures/api';

test.describe('Lore inline edit (on detail page)', () => {
  let seeded: SeededLore;

  test.beforeEach(async ({ request }) => {
    seeded = await seedLoreWithFolder(request);
  });

  test.afterEach(async ({ request }) => {
    if (seeded?.id) await deleteLore(request, seeded.id);
  });

  test('renames the lore inline and persists the change', async ({ page, request }) => {
    const newName = `${seeded.name} renamed`;
    const newDescription = 'Nouvelle description éditée via UI';

    await page.goto(`/lore/${seeded.id}`);

    await expect(page.locator('.detail-header h1')).toHaveText(seeded.name);

    await page.getByRole('button', { name: /^Modifier$/i }).click();

    const nameInput = page.getByLabel(/^Nom$/);
    const descInput = page.getByLabel(/^Description$/);
    await expect(nameInput).toHaveValue(seeded.name);

    await nameInput.fill(newName);
    await descInput.fill(newDescription);

    await page.getByRole('button', { name: /^Sauvegarder$/i }).click();

    // Sortie du mode édition : le header bascule en mode lecture avec la nouvelle valeur.
    await expect(page.locator('.detail-header h1')).toHaveText(newName);
    await expect(page.locator('.detail-header .description')).toHaveText(newDescription);

    const persisted = await getLoreById(request, seeded.id);
    expect(persisted.name).toBe(newName);
    expect(persisted.description).toBe(newDescription);
  });

  test('save button is disabled when name is emptied during edit', async ({ page }) => {
    await page.goto(`/lore/${seeded.id}`);
    await page.getByRole('button', { name: /^Modifier$/i }).click();

    const saveBtn = page.getByRole('button', { name: /^Sauvegarder$/i });
    await expect(saveBtn).toBeEnabled();

    await page.getByLabel(/^Nom$/).fill('');
    await expect(saveBtn).toBeDisabled();

    await page.getByLabel(/^Nom$/).fill('   ');
    await expect(saveBtn).toBeDisabled();
  });

  test('cancel discards the in-flight edits', async ({ page, request }) => {
    await page.goto(`/lore/${seeded.id}`);
    await page.getByRole('button', { name: /^Modifier$/i }).click();

    await page.getByLabel(/^Nom$/).fill('Name jamais sauvegardé');
    await page.getByRole('button', { name: /^Annuler$/i }).click();

    // Retour en mode lecture avec le nom d'origine.
    await expect(page.locator('.detail-header h1')).toHaveText(seeded.name);

    const persisted = await getLoreById(request, seeded.id);
    expect(persisted.name).toBe(seeded.name);
  });
});
