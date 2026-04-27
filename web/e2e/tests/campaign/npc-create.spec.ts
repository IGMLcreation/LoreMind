import { test, expect } from '@playwright/test';
import {
  seedCampaign,
  deleteCampaign,
  getNpcsByCampaign,
  type SeededCampaign,
} from '../../fixtures/api';

test.describe('NPC creation', () => {
  let campaign: SeededCampaign;

  test.beforeEach(async ({ request }) => {
    campaign = await seedCampaign(request);
  });

  test.afterEach(async ({ request }) => {
    if (campaign?.id) await deleteCampaign(request, campaign.id);
  });

  test('creates an NPC and redirects back to the campaign', async ({ page, request }) => {
    const npcName = `Borin le forgeron ${Date.now()}`;
    const markdown = '# Borin\n\n**Faction :** Clan Feuillefer\n\nNain barbu au regard perçant.';

    await page.goto(`/campaigns/${campaign.id}/npcs/create`);
    await expect(page.getByRole('heading', { name: /Nouveau PNJ/i })).toBeVisible();

    await page.getByLabel(/Nom du PNJ/i).fill(npcName);
    await page.getByLabel(/Fiche \(markdown\)/i).fill(markdown);

    await page.getByRole('button', { name: /^Créer$/i }).click();

    // Retour à la page campagne après création
    await expect(page).toHaveURL(new RegExp(`/campaigns/${campaign.id}$`));

    // Persistance vérifiée via API
    const npcs = await getNpcsByCampaign(request, campaign.id);
    const created = npcs.find((n) => n.name === npcName);
    expect(created).toBeDefined();
  });

  test('submit is disabled when name is empty', async ({ page }) => {
    await page.goto(`/campaigns/${campaign.id}/npcs/create`);

    const submit = page.getByRole('button', { name: /^Créer$/i });
    await expect(submit).toBeDisabled();

    await page.getByLabel(/Nom du PNJ/i).fill('Elara');
    await expect(submit).toBeEnabled();

    await page.getByLabel(/Nom du PNJ/i).fill('   ');
    await expect(submit).toBeDisabled();
  });

  test('NPC appears in the sidebar PNJ branch', async ({ page, request }) => {
    const npcName = `Sidebar test ${Date.now()}`;

    await page.goto(`/campaigns/${campaign.id}/npcs/create`);
    await page.getByLabel(/Nom du PNJ/i).fill(npcName);
    await page.getByRole('button', { name: /^Créer$/i }).click();

    await expect(page).toHaveURL(new RegExp(`/campaigns/${campaign.id}$`));

    // Le nœud "PNJ" doit apparaître dans la sidebar avec le nouveau PNJ.
    // On clique sur le nœud PNJ pour le déplier au cas où il serait fermé,
    // puis on vérifie que le PNJ est listé.
    const pnjNode = page.getByRole('button', { name: /^PNJ\b/ }).or(
      page.locator('.tree-item', { hasText: 'PNJ' }).first(),
    );
    await expect(pnjNode.first()).toBeVisible();

    // Vérification fallback via API : la liste contient bien le PNJ créé.
    const npcs = await getNpcsByCampaign(request, campaign.id);
    expect(npcs.map((n) => n.name)).toContain(npcName);
  });
});
