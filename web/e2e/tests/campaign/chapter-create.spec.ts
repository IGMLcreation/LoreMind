import { test, expect } from '@playwright/test';
import {
  seedCampaign,
  seedArc,
  deleteCampaign,
  getChapterById,
  type SeededCampaign,
  type SeededArc,
} from '../../fixtures/api';

test.describe('Chapter creation', () => {
  let campaign: SeededCampaign;
  let arc: SeededArc;

  test.beforeEach(async ({ request }) => {
    campaign = await seedCampaign(request);
    arc = await seedArc(request, { campaignId: campaign.id });
  });

  test.afterEach(async ({ request }) => {
    if (campaign?.id) await deleteCampaign(request, campaign.id);
  });

  test('creates a chapter and redirects to its view', async ({ page, request }) => {
    const chapterName = `Chapitre ${Date.now()}`;

    await page.goto(`/campaigns/${campaign.id}/arcs/${arc.id}/chapters/create`);
    await expect(page.getByRole('heading', { name: /Créer un nouveau chapitre/i })).toBeVisible();

    await page.getByLabel(/Nom du chapitre/i).fill(chapterName);
    await page.getByLabel(/Description/i).fill('Synopsis du chapitre');

    await page.getByRole('button', { name: /^Créer le chapitre$/i }).click();

    await expect(page).toHaveURL(
      new RegExp(`/campaigns/${campaign.id}/arcs/${arc.id}/chapters/\\d+$`),
    );

    const createdId = page.url().match(/\/chapters\/(\d+)$/)?.[1];
    expect(createdId).toBeTruthy();
    const persisted = await getChapterById(request, createdId!);
    expect(persisted.name).toBe(chapterName);
  });

  test('submit is disabled when name is empty', async ({ page }) => {
    await page.goto(`/campaigns/${campaign.id}/arcs/${arc.id}/chapters/create`);

    const submit = page.getByRole('button', { name: /^Créer le chapitre$/i });
    await expect(submit).toBeDisabled();

    await page.getByLabel(/Nom du chapitre/i).fill('OK');
    await expect(submit).toBeEnabled();
  });
});
