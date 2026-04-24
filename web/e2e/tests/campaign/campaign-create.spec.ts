import { test, expect } from '@playwright/test';
import {
  seedLoreWithFolder,
  deleteLore,
  deleteCampaign,
  getCampaigns,
  type SeededLore,
} from '../../fixtures/api';

test.describe('Campaign creation', () => {
  const createdCampaignIds: string[] = [];
  let linkedLore: SeededLore;

  test.beforeEach(async ({ request }) => {
    linkedLore = await seedLoreWithFolder(request);
  });

  test.afterEach(async ({ request }) => {
    while (createdCampaignIds.length) {
      await deleteCampaign(request, createdCampaignIds.pop()!);
    }
    if (linkedLore?.id) await deleteLore(request, linkedLore.id);
  });

  test('creates a standalone campaign (no lore, no system) and shows it in the grid', async ({
    page,
    request,
  }) => {
    const campaignName = `Campagne E2E ${Date.now()}`;
    const description = 'Une campagne créée par les tests automatisés.';

    await page.goto('/campaigns');
    await expect(page.getByRole('heading', { name: /Vos Campagnes|Campagnes/i })).toBeVisible();

    await page.locator('.campaign-card.card-new').click();

    const modal = page.locator('.modal');
    await expect(modal).toBeVisible();

    await modal.getByLabel(/Nom de la campagne/i).fill(campaignName);
    await modal.getByLabel(/Description/i).fill(description);
    await modal.getByLabel(/Nombre de joueurs/i).fill('5');

    await modal.getByRole('button', { name: /^Créer la campagne$/i }).click();

    await expect(modal).not.toBeVisible();

    const newCard = page.locator('.campaign-card', { hasText: campaignName });
    await expect(newCard).toBeVisible();

    const campaigns = await getCampaigns(request);
    const created = campaigns.find((c) => c.name === campaignName);
    expect(created).toBeDefined();
    expect(created!.loreId).toBeNull();
    createdCampaignIds.push(created!.id);
  });

  test('creates a campaign linked to an existing lore', async ({ page, request }) => {
    const campaignName = `Campagne liée ${Date.now()}`;

    await page.goto('/campaigns');
    await page.locator('.campaign-card.card-new').click();

    const modal = page.locator('.modal');
    await modal.getByLabel(/Nom de la campagne/i).fill(campaignName);
    await modal.getByLabel(/Univers associé/i).selectOption({ label: linkedLore.name });

    await modal.getByRole('button', { name: /^Créer la campagne$/i }).click();
    await expect(modal).not.toBeVisible();

    const campaigns = await getCampaigns(request);
    const created = campaigns.find((c) => c.name === campaignName);
    expect(created).toBeDefined();
    expect(created!.loreId).toBe(linkedLore.id);
    createdCampaignIds.push(created!.id);
  });

  test('submit is disabled without a name and when player count is invalid', async ({ page }) => {
    await page.goto('/campaigns');
    await page.locator('.campaign-card.card-new').click();

    const modal = page.locator('.modal');
    const submit = modal.getByRole('button', { name: /^Créer la campagne$/i });

    await expect(submit).toBeDisabled();

    await modal.getByLabel(/Nom de la campagne/i).fill('Valid name');
    await expect(submit).toBeEnabled();

    await modal.getByLabel(/Nombre de joueurs/i).fill('0');
    await expect(submit).toBeDisabled();

    await modal.getByLabel(/Nombre de joueurs/i).fill('3');
    await expect(submit).toBeEnabled();
  });
});
