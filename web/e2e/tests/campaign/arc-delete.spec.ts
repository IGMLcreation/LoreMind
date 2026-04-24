import { test, expect } from '@playwright/test';
import {
  seedCampaign,
  seedArc,
  deleteCampaign,
  type SeededCampaign,
  type SeededArc,
} from '../../fixtures/api';

test.describe('Arc delete', () => {
  let campaign: SeededCampaign;
  let arc: SeededArc;

  test.beforeEach(async ({ request }) => {
    campaign = await seedCampaign(request);
    arc = await seedArc(request, { campaignId: campaign.id });
  });

  test.afterEach(async ({ request }) => {
    if (campaign?.id) await deleteCampaign(request, campaign.id);
  });

  test('deletes an arc after accepting confirm and redirects to the campaign', async ({
    page,
    request,
  }) => {
    page.on('dialog', (dialog) => dialog.accept());

    await page.goto(`/campaigns/${campaign.id}/arcs/${arc.id}/edit`);
    await page.getByRole('button', { name: /^Supprimer$/i }).click();

    await expect(page).toHaveURL(new RegExp(`/campaigns/${campaign.id}$`));

    const res = await request.get(`/api/arcs/${arc.id}`);
    expect(res.status()).toBe(404);
  });

  test('keeps the arc when confirm is dismissed', async ({ page, request }) => {
    page.on('dialog', (dialog) => dialog.dismiss());

    await page.goto(`/campaigns/${campaign.id}/arcs/${arc.id}/edit`);
    await page.getByRole('button', { name: /^Supprimer$/i }).click();

    await expect(page).toHaveURL(new RegExp(`/campaigns/${campaign.id}/arcs/${arc.id}/edit$`));

    const res = await request.get(`/api/arcs/${arc.id}`);
    expect(res.ok()).toBeTruthy();
  });
});
