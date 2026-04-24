import { test, expect } from '@playwright/test';
import {
  seedCampaign,
  deleteCampaign,
  getArcById,
  type SeededCampaign,
} from '../../fixtures/api';

test.describe('Arc creation', () => {
  let campaign: SeededCampaign;

  test.beforeEach(async ({ request }) => {
    campaign = await seedCampaign(request);
  });

  test.afterEach(async ({ request }) => {
    if (campaign?.id) await deleteCampaign(request, campaign.id);
  });

  test('creates an arc and redirects to its view', async ({ page, request }) => {
    const arcName = `Arc ${Date.now()}`;
    const description = 'Synopsis test';

    await page.goto(`/campaigns/${campaign.id}/arcs/create`);
    await expect(page.getByRole('heading', { name: /Créer un nouvel arc/i })).toBeVisible();

    await page.getByLabel(/Nom de l'arc/i).fill(arcName);
    await page.getByLabel(/Description/i).fill(description);

    await page.getByRole('button', { name: /^Créer l'arc$/i }).click();

    await expect(page).toHaveURL(new RegExp(`/campaigns/${campaign.id}/arcs/\\d+$`));

    const createdId = page.url().match(/\/arcs\/(\d+)$/)?.[1];
    expect(createdId).toBeTruthy();
    const persisted = await getArcById(request, createdId!);
    expect(persisted.name).toBe(arcName);
    expect(persisted.description).toBe(description);
  });

  test('submit is disabled when name is empty', async ({ page }) => {
    await page.goto(`/campaigns/${campaign.id}/arcs/create`);

    const submit = page.getByRole('button', { name: /^Créer l'arc$/i });
    await expect(submit).toBeDisabled();

    await page.getByLabel(/Nom de l'arc/i).fill('Quelque chose');
    await expect(submit).toBeEnabled();
  });
});
