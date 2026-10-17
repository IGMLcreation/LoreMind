import { test, expect } from '@playwright/test';

// Deterministic read-only API fixtures: no writes to a running LoreMind instance.
const campaign = { id: '1', name: 'Les Ombres', description: '', playerCount: 0 };
const arc = { id: '2', name: 'Le port', campaignId: '1', type: 'LINEAR' };
const chapter = { id: '3', name: 'La nuit', arcId: '2' };
const scene = { id: '4', name: 'La taverne', chapterId: '3', description: 'Un rendez-vous sous la pluie.' };
const scenePath = '/campaigns/1/arcs/2/chapters/3/scenes/4';
const playthrough = { id: '5', name: 'Groupe du vendredi', campaignId: '1' };
const tree = { arcs: [arc, { ...arc, id: '8', name: 'La forêt' }], chaptersByArc: { '2': [chapter] },
  scenesByChapter: { '3': [scene] }, npcs: [], randomTables: [], enemies: [], quests: [],
  playthroughs: [playthrough], readiness: { gaps: [], overallStatus: 'READY', counts: {} } };

test.beforeEach(async ({ page }) => {
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    const responses: Record<string, unknown> = {
      '/api/config': { demoMode: true, updateCheckEnabled: false },
      '/api/campaigns': [campaign, { ...campaign, id: '10', name: 'Autre campagne' }],
      '/api/campaigns/1': campaign,
      '/api/campaigns/1/tree': tree,
      '/api/arcs/2': arc, '/api/chapters/3': chapter, '/api/scenes/4': scene,
      '/api/playthroughs/5': playthrough,
      '/api/playthroughs': [playthrough],
      '/api/campaigns/1/writing-review': { remarks: [] },
      '/api/sessions/6': { id: '6', name: 'Séance du vendredi', playthroughId: '5', active: false,
        startedAt: '2026-10-16T18:00:00', endedAt: '2026-10-16T21:00:00' }
    };
    let json = responses[url.pathname] ?? [];
    if (url.pathname === '/api/campaigns/search-content' && url.searchParams.get('q') === 'taverne') {
      json = [{ kind: 'scene', ...scene, campaignId: '1', campaignName: 'Les Ombres',
        arcId: '2', arcName: 'Le port', chapterName: 'La nuit' }];
    }
    await route.fulfill({ json });
  });
});

test('scene context stays selected and breadcrumbs navigate back to its chapter', async ({ page }) => {
  await page.goto(scenePath);
  const breadcrumb = page.getByRole('navigation', { name: "Fil d'Ariane" });
  await expect(breadcrumb).toContainText('Les Ombres');
  await expect(breadcrumb).toContainText('Le port');
  await expect(breadcrumb.locator('[aria-current="page"]')).toHaveText('La taverne');
  await expect(page.locator('app-sidebar').getByRole('button', { name: 'Les Ombres', exact: true }))
    .toHaveAttribute('aria-current', 'location');
  await expect(page.locator('app-sidebar').getByRole('button', { name: 'Autre campagne', exact: true }))
    .not.toHaveAttribute('aria-current', 'location');
  await breadcrumb.getByRole('link', { name: 'La nuit' }).click();
  await expect(page).toHaveURL(/\/chapters\/3$/);
  await expect(breadcrumb.locator('[aria-current="page"]')).toHaveText('La nuit');
});

test('search opens a scene and can repeat the same query after reopening', async ({ page }) => {
  await page.goto(scenePath);
  await expect(page.getByRole('heading', { name: 'La taverne', exact: true })).toBeVisible();
  for (let attempt = 0; attempt < 2; attempt++) {
    await page.keyboard.press('Control+k');
    const dialog = page.getByRole('dialog', { name: 'Recherche globale' });
    await dialog.getByRole('textbox').fill('taverne');
    const result = dialog.getByRole('button', { name: /^La taverne/ });
    await expect(result).toContainText('Les Ombres › Le port › La nuit');
    await dialog.getByRole('textbox').press('Enter');
    await expect(dialog).toBeHidden();
    await expect(page).toHaveURL(new RegExp(scenePath + '$'));
  }
});

test('session breadcrumb includes the owning playthrough', async ({ page }) => {
  await page.goto('/sessions/6');
  const breadcrumb = page.getByRole('navigation', { name: "Fil d'Ariane" });
  await expect(breadcrumb.getByRole('link', { name: 'Groupe du vendredi' }))
    .toHaveAttribute('href', '/campaigns/1/playthroughs/5');
  await expect(breadcrumb.locator('[aria-current="page"]')).toHaveText('Séance du vendredi');
  await expect(page.locator('app-sidebar .toggle-btn.active')).toHaveText('Campagne');
});

test('breadcrumb and search remain readable on a narrow screen', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.goto(scenePath);
  await expect(page.getByRole('navigation', { name: "Fil d'Ariane" })).toBeVisible();
  const sizes = await page.locator('main').evaluate(el => ({ scroll: el.scrollWidth, client: el.clientWidth }));
  expect(sizes.scroll).toBeLessThanOrEqual(sizes.client);
  await page.keyboard.press('Control+k');
  const dialog = page.getByRole('dialog', { name: 'Recherche globale' });
  await expect(dialog).toBeVisible();
  const searchSizes = await dialog.evaluate(el => ({ scroll: el.scrollWidth, client: el.clientWidth }));
  expect(searchSizes.scroll).toBeLessThanOrEqual(searchSizes.client);
  await page.screenshot({ path: testInfo.outputPath('mobile-shortcuts.png') });
});

test('recent pages and favorites survive reload and stars work with the keyboard', async ({ page }, testInfo) => {
  await page.goto(scenePath);
  await expect(page.getByRole('heading', { name: 'La taverne', exact: true })).toBeVisible();
  await page.keyboard.press('Control+k');
  const dialog = page.getByRole('dialog', { name: 'Recherche globale' });
  await expect(dialog.getByRole('heading', { name: 'Consultés récemment' })).toBeVisible();
  const star = dialog.getByRole('button', { name: 'Ajouter La taverne aux favoris', exact: true });
  await star.focus();
  await star.press('Enter');
  await expect(dialog).toBeVisible();
  await expect(dialog.getByRole('heading', { name: 'Favoris' })).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath('favorites.png') });
  await page.reload();
  await expect(page.getByRole('heading', { name: 'La taverne', exact: true })).toBeVisible();
  await page.keyboard.press('Control+k');
  await expect(dialog.getByRole('heading', { name: 'Favoris' })).toBeVisible();
  await dialog.getByRole('button', { name: 'Retirer La taverne des favoris', exact: true }).click();
  await expect(dialog.getByRole('heading', { name: 'Favoris' })).toHaveCount(0);
  await dialog.getByRole('button', { name: 'Effacer l’historique' }).click();
  await expect(dialog.getByRole('heading', { name: 'Consultés récemment' })).toHaveCount(0);
});

test('campaign management actions stay accessible through the compact menu', async ({ page }, testInfo) => {
  await page.goto('/campaigns/1');
  await expect(page.getByRole('heading', { name: 'Les Ombres', exact: true })).toBeVisible();
  const menu = page.locator('.management-menu');
  await expect(menu.getByRole('button', { name: 'Supprimer', exact: true })).toBeHidden();
  await menu.locator('summary').click();
  await expect(menu.getByRole('button', { name: 'Supprimer', exact: true })).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath('campaign-menu.png') });
  await menu.getByRole('button', { name: 'Modifier', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Sauvegarder', exact: true })).toBeVisible();
  await expect(page.locator('.management-menu')).toHaveCount(0);
});
