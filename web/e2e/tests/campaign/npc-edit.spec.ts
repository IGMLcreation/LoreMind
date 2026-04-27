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
    npc = await seedNpc(request, {
      campaignId: campaign.id,
      markdownContent: '# Initial\n\nFiche de départ.',
    });
  });

  test.afterEach(async ({ request }) => {
    if (campaign?.id) await deleteCampaign(request, campaign.id);
  });

  test('edits name + markdown content and persists via API', async ({ page, request }) => {
    const newName = `${npc.name} (renommé)`;
    const newMarkdown = '# Borin réécrit\n\n**Statut :** Disparu\n\nDes traces dans la neige...';

    await page.goto(`/campaigns/${campaign.id}/npcs/${npc.id}/edit`);

    await expect(page.getByRole('heading', { name: /Éditer le PNJ/i })).toBeVisible();
    await expect(page.getByLabel(/Nom du PNJ/i)).toHaveValue(npc.name);

    await page.getByLabel(/Nom du PNJ/i).fill(newName);
    await page.getByLabel(/Fiche \(markdown\)/i).fill(newMarkdown);

    await page.getByRole('button', { name: /^Enregistrer$/i }).click();

    // Retour à la campagne après save
    await expect(page).toHaveURL(new RegExp(`/campaigns/${campaign.id}$`));

    const persisted = await getNpcById(request, npc.id);
    expect(persisted.name).toBe(newName);
    expect(persisted.markdownContent).toBe(newMarkdown);
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
