import { test, expect } from '@playwright/test';
import { deleteGameSystem } from '../../fixtures/api';

test.describe('GameSystem creation', () => {
  // Les game systems crees par les tests sont nettoyes via cet array — chaque
  // test pousse les IDs qu'il a crees pour qu'on les supprime en afterEach.
  const createdIds: string[] = [];

  test.afterEach(async ({ request }) => {
    while (createdIds.length) {
      const id = createdIds.pop()!;
      await deleteGameSystem(request, id);
    }
  });

  test('creates a game system and redirects to the list', async ({ page, request }) => {
    const gsName = `Système E2E ${Date.now()}`;
    const description = 'Système créé par les tests automatisés.';
    const author = 'Playwright';

    await page.goto('/game-systems');
    await expect(page.getByRole('heading', { name: /Systèmes de JDR/i })).toBeVisible();

    // Carte "Nouveau système" → ouvre l'editeur en mode creation.
    await page.locator('.gs-card.card-new').click();
    await expect(page).toHaveURL(/\/game-systems\/create$/);
    await expect(page.getByRole('heading', { name: /Nouveau système de JDR/i })).toBeVisible();

    await page.getByLabel(/^Nom/i).fill(gsName);
    await page.getByLabel(/Description courte/i).fill(description);
    await page.getByLabel(/Auteur/i).fill(author);

    await page.getByRole('button', { name: /^Créer$/i }).click();

    // Redirection vers la liste apres creation.
    await expect(page).toHaveURL(/\/game-systems$/);
    // Et la carte du nouveau systeme est visible dans la grille.
    await expect(page.locator('.gs-card', { hasText: gsName })).toBeVisible();

    // Verification API : le systeme est bien persistant.
    const all = await request.get('/api/game-systems').then((r) => r.json());
    const created = all.find((gs: { id: string; name: string }) => gs.name === gsName);
    expect(created).toBeDefined();
    expect(created.author).toBe(author);
    createdIds.push(created.id);
  });

  test('submit button is disabled when name is empty', async ({ page }) => {
    await page.goto('/game-systems/create');

    const submit = page.getByRole('button', { name: /^Créer$/i });
    await expect(submit).toBeDisabled();

    await page.getByLabel(/^Nom/i).fill('Quelque chose');
    await expect(submit).toBeEnabled();

    await page.getByLabel(/^Nom/i).fill('   ');
    await expect(submit).toBeDisabled();
  });

  test('cancel returns to the list without creating', async ({ page, request }) => {
    const abandoned = `Système abandonné ${Date.now()}`;

    await page.goto('/game-systems/create');
    await page.getByLabel(/^Nom/i).fill(abandoned);

    await page.getByRole('button', { name: /^Annuler$/i }).click();
    await expect(page).toHaveURL(/\/game-systems$/);

    // Rien n'a ete cree cote API.
    const all = await request.get('/api/game-systems').then((r) => r.json());
    expect(all.find((gs: { name: string }) => gs.name === abandoned)).toBeUndefined();
  });
});
