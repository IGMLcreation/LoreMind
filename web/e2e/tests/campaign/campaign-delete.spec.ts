import { test, expect } from '@playwright/test';
import { seedCampaign, deleteCampaign, type SeededCampaign } from '../../fixtures/api';

test.describe('Campaign delete', () => {
  let campaign: SeededCampaign;

  test.beforeEach(async ({ request }) => {
    campaign = await seedCampaign(request);
  });

  test.afterEach(async ({ request }) => {
    if (campaign?.id) await deleteCampaign(request, campaign.id);
  });

  test('deletes a campaign after accepting confirm and redirects to the list', async ({
    page,
    request,
  }) => {
    await page.goto(`/campaigns/${campaign.id}`);
    await page.getByRole('button', { name: /^Supprimer$/i }).first().click();

    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await dialog.getByRole('button', { name: /^Supprimer$/i }).click();

    await expect(page).toHaveURL(/\/campaigns$/);

    const res = await request.get(`/api/campaigns/${campaign.id}`);
    expect(res.status()).toBe(404);
  });

  test('keeps the campaign when confirm is dismissed', async ({ page, request }) => {
    await page.goto(`/campaigns/${campaign.id}`);
    await page.getByRole('button', { name: /^Supprimer$/i }).first().click();

    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await dialog.getByRole('button', { name: /^Annuler$/i }).click();

    await expect(page).toHaveURL(new RegExp(`/campaigns/${campaign.id}$`));

    const res = await request.get(`/api/campaigns/${campaign.id}`);
    expect(res.ok()).toBeTruthy();
  });
});
