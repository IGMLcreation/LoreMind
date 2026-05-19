import { test, expect } from '@playwright/test';
import {
  seedCampaign,
  seedNpc,
  deleteCampaign,
  getNpcById,
  type SeededCampaign,
  type SeededNpc,
} from '../../fixtures/api';

test.describe('NPC edit', () => {
  let campaign: SeededCampaign;
  let npc: SeededNpc;

  test.beforeEach(async ({ request }) => {
    campaign = await seedCampaign(request);
    npc = await seedNpc(request, { campaignId: campaign.id });
  });

  test.afterEach(async ({ request }) => {
    if (campaign?.id) await deleteCampaign(request, campaign.id);
  });

  test('edits name and persists via API', async ({ page, request }) => {
    // Note : depuis la refonte 2026-04-30 les fiches PNJ utilisent des champs
    // templates dynamiques pilotes par le GameSystem, plus le markdownContent
    // libre. La campagne seedee n'a pas de GameSystem donc pas de champs
    // dynamiques a tester ici — on se contente du nom (champ universel).
    const newName = `${npc.name} (renommé)`;

    await page.goto(`/campaigns/${campaign.id}/npcs/${npc.id}/edit`);

    await expect(page.getByRole('heading', { name: /Éditer le PNJ/i })).toBeVisible();
    await expect(page.getByLabel(/Nom du PNJ/i)).toHaveValue(npc.name);

    await page.getByLabel(/Nom du PNJ/i).fill(newName);

    await page.getByRole('button', { name: /^Enregistrer$/i }).click();

    // Retour à la campagne après save
    await expect(page).toHaveURL(new RegExp(`/campaigns/${campaign.id}$`));

    const persisted = await getNpcById(request, npc.id);
    expect(persisted.name).toBe(newName);
  });

  test('save button is disabled when name is cleared', async ({ page }) => {
    await page.goto(`/campaigns/${campaign.id}/npcs/${npc.id}/edit`);

    const nameField = page.getByLabel(/Nom du PNJ/i);
    const saveBtn = page.getByRole('button', { name: /^Enregistrer$/i });

    await expect(saveBtn).toBeEnabled();
    await nameField.fill('');
    await expect(saveBtn).toBeDisabled();
    await nameField.fill('OK');
    await expect(saveBtn).toBeEnabled();
  });

  test('Assistant IA button is visible in edit mode', async ({ page }) => {
    // Vérifie l'intégration drawer chat IA — symétrique aux PJ.
    // Note : le drawer lui-même nécessite le Brain Python en route, donc
    // on ne teste que la présence du bouton trigger.
    await page.goto(`/campaigns/${campaign.id}/npcs/${npc.id}/edit`);
    await expect(page.getByRole('button', { name: /Assistant IA/i })).toBeVisible();
  });
});
