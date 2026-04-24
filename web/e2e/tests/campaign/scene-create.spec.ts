import { test, expect } from '@playwright/test';
import {
  seedCampaign,
  seedArc,
  seedChapter,
  deleteCampaign,
  getSceneById,
  type SeededCampaign,
  type SeededArc,
  type SeededChapter,
} from '../../fixtures/api';

test.describe('Scene creation', () => {
  let campaign: SeededCampaign;
  let arc: SeededArc;
  let chapter: SeededChapter;

  test.beforeEach(async ({ request }) => {
    campaign = await seedCampaign(request);
    arc = await seedArc(request, { campaignId: campaign.id });
    chapter = await seedChapter(request, { arcId: arc.id });
  });

  test.afterEach(async ({ request }) => {
    if (campaign?.id) await deleteCampaign(request, campaign.id);
  });

  test('creates a scene and redirects to its view', async ({ page, request }) => {
    const sceneName = `Scène ${Date.now()}`;

    await page.goto(
      `/campaigns/${campaign.id}/arcs/${arc.id}/chapters/${chapter.id}/scenes/create`,
    );
    await expect(page.getByRole('heading', { name: /Créer une nouvelle scène/i })).toBeVisible();

    await page.getByLabel(/Nom de la scène/i).fill(sceneName);
    await page.getByLabel(/Description/i).fill('Résumé rapide de la scène.');

    await page.getByRole('button', { name: /^Créer la scène$/i }).click();

    await expect(page).toHaveURL(
      new RegExp(
        `/campaigns/${campaign.id}/arcs/${arc.id}/chapters/${chapter.id}/scenes/\\d+$`,
      ),
    );

    const createdId = page.url().match(/\/scenes\/(\d+)$/)?.[1];
    expect(createdId).toBeTruthy();
    const persisted = await getSceneById(request, createdId!);
    expect(persisted.name).toBe(sceneName);
  });

  test('submit is disabled when name is empty', async ({ page }) => {
    await page.goto(
      `/campaigns/${campaign.id}/arcs/${arc.id}/chapters/${chapter.id}/scenes/create`,
    );

    const submit = page.getByRole('button', { name: /^Créer la scène$/i });
    await expect(submit).toBeDisabled();

    await page.getByLabel(/Nom de la scène/i).fill('OK');
    await expect(submit).toBeEnabled();
  });
});
