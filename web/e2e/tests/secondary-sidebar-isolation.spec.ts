import { test, expect } from '@playwright/test';
import { seedLoreWithFolder, deleteLore, type SeededLore } from '../fixtures/api';

/**
 * Regression : la secondary sidebar fuyait entre sections.
 *
 * Bug initial (2026-05-19) : on est sur /lore/:id (la sidebar affiche l'arbre
 * du Lore), on clique sur "Campagne" dans la sidebar principale → on arrive
 * sur /campaigns, MAIS la sidebar secondaire continuait d'afficher l'arbre
 * du Lore precedent.
 *
 * Cause : les composants top-level (campaigns.component, lore.component,
 * game-systems.component, settings.component) ne nettoyaient pas la sidebar
 * heritee d'une section precedente. Fix : appel a layoutService.hide() dans
 * leur ngOnInit.
 */
test.describe('Secondary sidebar — isolation entre sections', () => {
  let seededLore: SeededLore;

  test.beforeEach(async ({ request }) => {
    seededLore = await seedLoreWithFolder(request);
  });

  test.afterEach(async ({ request }) => {
    if (seededLore?.id) await deleteLore(request, seededLore.id);
  });

  test('Lore detail → /campaigns : la sidebar secondaire disparait', async ({ page }) => {
    // 1. Sur le detail d'un Lore, la sidebar secondaire est affichee avec
    //    le nom du Lore comme titre.
    await page.goto(`/lore/${seededLore.id}`);
    await expect(page.locator('app-secondary-sidebar')).toBeVisible();
    await expect(page.locator('app-secondary-sidebar')).toContainText(seededLore.name);

    // 2. Navigation vers la liste des campagnes (top-level).
    await page.goto('/campaigns');
    await expect(page.getByRole('heading', { name: /Vos Campagnes/i })).toBeVisible();

    // 3. La sidebar secondaire ne doit PAS persister (sinon elle afficherait
    //    encore l'arbre du Lore precedent). Le *ngIf au niveau d'AppComponent
    //    la retire completement du DOM quand layoutService est en etat hidden.
    await expect(page.locator('app-secondary-sidebar')).toHaveCount(0);
  });

  test('Lore detail → /game-systems : la sidebar secondaire disparait', async ({ page }) => {
    await page.goto(`/lore/${seededLore.id}`);
    await expect(page.locator('app-secondary-sidebar')).toBeVisible();

    await page.goto('/game-systems');
    await expect(page.getByRole('heading', { name: /Systèmes de JDR/i })).toBeVisible();
    await expect(page.locator('app-secondary-sidebar')).toHaveCount(0);
  });

  test('Lore detail → /settings : la sidebar secondaire disparait', async ({ page }) => {
    await page.goto(`/lore/${seededLore.id}`);
    await expect(page.locator('app-secondary-sidebar')).toBeVisible();

    await page.goto('/settings');
    // Settings n'a pas de h1 forcement evident, on se base sur l'URL + l'absence
    // de sidebar secondaire (objet du test).
    await expect(page).toHaveURL(/\/settings$/);
    await expect(page.locator('app-secondary-sidebar')).toHaveCount(0);
  });

  test('Lore detail → /lore (liste racine) : la sidebar secondaire disparait', async ({ page }) => {
    // Sur le detail, la sidebar est visible
    await page.goto(`/lore/${seededLore.id}`);
    await expect(page.locator('app-secondary-sidebar')).toBeVisible();

    // Retour a la liste racine du Lore
    await page.goto('/lore');
    await expect(page.getByRole('heading', { name: /Vos Univers/i })).toBeVisible();

    // La sidebar ne doit plus apparaitre sur la liste racine.
    await expect(page.locator('app-secondary-sidebar')).toHaveCount(0);
  });
});
