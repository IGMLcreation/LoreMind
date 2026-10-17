import { test, expect, type APIRequestContext, type Page } from '@playwright/test';
import {
  seedCampaign,
  seedArc,
  seedChapter,
  seedScene,
  deleteCampaign,
  type SeededCampaign,
  type SeededArc,
  type SeededChapter,
  type SeededScene,
} from '../../fixtures/api';

/**
 * Shell responsive au viewport téléphone (projet Playwright « mobile », 375 px).
 *
 * Sous 1024 px, le menu principal et l'arborescence deviennent des tiroirs
 * off-canvas ouverts depuis une barre haute. On vérifie :
 *  - qu'aucun écran clé ne défile horizontalement — ni le document, ni
 *    `main.main-content` (le conteneur qui défile réellement : un débordement
 *    y produirait une barre horizontale sans toucher au document) ;
 *  - que la navigation reste accessible au doigt (ouverture, navigation,
 *    fermeture au changement de route / Échap / clic extérieur).
 */

async function expectNoHorizontalScroll(page: Page): Promise<void> {
  const m = await page.evaluate(() => {
    const main = document.querySelector('main.main-content');
    return {
      doc: document.documentElement.scrollWidth,
      vw: window.innerWidth,
      mainScroll: main?.scrollWidth ?? 0,
      mainClient: main?.clientWidth ?? 0,
    };
  });
  expect(m.doc, 'scroll horizontal du document').toBeLessThanOrEqual(m.vw);
  expect(m.mainScroll, 'scroll horizontal de main.main-content').toBeLessThanOrEqual(m.mainClient);
}

async function seedPlaythroughWithSession(
  request: APIRequestContext,
  campaignId: string,
): Promise<{ playthroughId: string; sessionId: string }> {
  const ptRes = await request.post('/api/playthroughs', {
    data: { campaignId, name: `E2E Partie ${Date.now()}`, description: 'Seeded by Playwright' },
  });
  expect(ptRes.ok(), `POST /api/playthroughs -> ${ptRes.status()}`).toBeTruthy();
  const pt = await ptRes.json();

  const clockRes = await request.post(`/api/playthroughs/${pt.id}/clocks`, {
    data: { name: 'Le rituel du nécromancien', description: '', segments: 6 },
  });
  expect(clockRes.ok(), `POST clocks -> ${clockRes.status()}`).toBeTruthy();

  const sRes = await request.post('/api/sessions', { data: { playthroughId: pt.id } });
  expect(sRes.ok(), `POST /api/sessions -> ${sRes.status()}`).toBeTruthy();
  const session = await sRes.json();

  const eRes = await request.post(`/api/sessions/${session.id}/entries`, {
    data: { type: 'NOTE', content: 'Les PJ arrivent à Brumecombe sous la pluie.', occurredAt: null },
  });
  expect(eRes.ok(), `POST entries -> ${eRes.status()}`).toBeTruthy();

  return { playthroughId: pt.id, sessionId: session.id };
}

test.describe('Shell mobile (375 px)', () => {
  test('liste des campagnes : pas de scroll horizontal, menu en tiroir', async ({ page }) => {
    await page.goto('/campaigns');
    await expect(page.getByRole('heading', { name: /Vos Campagnes/i })).toBeVisible();
    await expectNoHorizontalScroll(page);

    // Le menu principal est hors écran tant que le tiroir est fermé.
    const menu = page.locator('app-sidebar');
    await expect(menu).toBeHidden();

    // Le libellé du burger alterne Ouvrir / Fermer : on le cible par aria-controls.
    const burger = page.locator('button[aria-controls="app-main-nav"]');
    await expect(burger).toHaveAccessibleName('Ouvrir le menu');
    await burger.tap();
    await expect(menu).toBeVisible();
    await expect(burger).toHaveAttribute('aria-expanded', 'true');
    await expect(burger).toHaveAccessibleName('Fermer le menu');

    // Échap referme et rend le focus au bouton burger.
    await page.keyboard.press('Escape');
    await expect(menu).toBeHidden();
    await expect(burger).toBeFocused();

    // Clic hors du tiroir (voile) : referme aussi.
    await burger.tap();
    await expect(menu).toBeVisible();
    await page.mouse.click(360, 400);
    await expect(menu).toBeHidden();

    // Navigation depuis le tiroir : la page change et le tiroir se ferme.
    await burger.tap();
    await menu.getByRole('button', { name: 'Lore', exact: true }).tap();
    await expect(page).toHaveURL(/\/lore$/);
    await expect(page.getByRole('heading', { name: /Vos Univers/i })).toBeVisible();
    await expect(menu).toBeHidden();
    await expectNoHorizontalScroll(page);
  });

  test.describe('avec une campagne', () => {
    let campaign: SeededCampaign;
    let arc: SeededArc;
    let chapter: SeededChapter;
    let scene: SeededScene;

    test.beforeEach(async ({ request }) => {
      // Nom long volontaire : c'est lui qui faisait déborder l'en-tête du hub.
      campaign = await seedCampaign(request, {
        name: `E2E La Chute de la Maison Valcourt — campagne longue ${Date.now()}`,
      });
      arc = await seedArc(request, { campaignId: campaign.id });
      chapter = await seedChapter(request, { arcId: arc.id });
      scene = await seedScene(request, { chapterId: chapter.id, name: 'La taverne du Corbeau Borgne' });
    });

    test.afterEach(async ({ request }) => {
      if (campaign?.id) await deleteCampaign(request, campaign.id);
    });

    test('hub campagne → scène via l’arborescence en tiroir', async ({ page }) => {
      await page.goto(`/campaigns/${campaign.id}`);
      await expect(page.getByRole('heading', { level: 1, name: campaign.name })).toBeVisible();
      await expectNoHorizontalScroll(page);

      // L'arborescence s'ouvre depuis la barre haute (bouton titré par la campagne).
      const tree = page.locator('app-secondary-sidebar');
      await expect(tree).toBeHidden();
      await page.getByRole('button', { name: "Ouvrir l'arborescence" }).tap();
      await expect(tree).toBeVisible();

      // Navigation par niveaux : toucher une ligne qui a des enfants « entre » dedans
      // (sans quitter le volet), jusqu'à la scène. Mode plat ou arc/chapitre selon le seed.
      const sceneRow = tree.locator('.drill-row', { hasText: scene.name });
      for (const label of ['Scènes', arc.name, chapter.name]) {
        if (await sceneRow.isVisible()) break;
        const row = tree.locator('.drill-row', { hasText: label }).first();
        if (await row.count()) {
          await row.tap();
          await expect(tree.locator('.drill-back')).toBeVisible();
          await expect(tree).toBeVisible();
        }
      }
      await sceneRow.tap();

      await expect(page).toHaveURL(new RegExp(`/scenes/${scene.id}$`));
      await expect(page.getByRole('heading', { level: 1, name: scene.name })).toBeVisible();
      await expect(tree).toBeHidden();
      await expectNoHorizontalScroll(page);

      // En rouvrant le volet, on retrouve le niveau de la scène, surlignée ; « retour » remonte.
      await page.getByRole('button', { name: "Ouvrir l'arborescence" }).tap();
      await expect(tree.locator('.drill-row.active', { hasText: scene.name })).toBeVisible();
      await tree.locator('.drill-back').tap();
      await expect(tree.locator('.drill-row.active', { hasText: scene.name })).toHaveCount(0);
    });

    test('écran de séance : journal et horloges lisibles sans scroll horizontal', async ({ page, request }) => {
      const { sessionId } = await seedPlaythroughWithSession(request, campaign.id);

      await page.goto(`/sessions/${sessionId}`);
      await expect(page.getByText('Les PJ arrivent à Brumecombe sous la pluie.')).toBeVisible();
      await expect(page.getByText('Le rituel du nécromancien')).toBeVisible();
      await expectNoHorizontalScroll(page);

      // Le menu reste accessible depuis la séance.
      await page.getByRole('button', { name: 'Ouvrir le menu' }).tap();
      await expect(page.locator('app-sidebar')).toBeVisible();
    });
  });
});
