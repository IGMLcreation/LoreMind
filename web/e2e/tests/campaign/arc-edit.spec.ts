import { test, expect } from '@playwright/test';
import {
  seedCampaign,
  seedArc,
  deleteCampaign,
  getArcById,
  type SeededCampaign,
  type SeededArc,
} from '../../fixtures/api';

test.describe('Arc edit', () => {
  let campaign: SeededCampaign;
  let arc: SeededArc;

  test.beforeEach(async ({ request }) => {
    campaign = await seedCampaign(request);
    arc = await seedArc(request, { campaignId: campaign.id });
  });

  test.afterEach(async ({ request }) => {
    if (campaign?.id) await deleteCampaign(request, campaign.id);
  });

  test('form is prefilled with the arc name', async ({ page }) => {
    await page.goto(`/campaigns/${campaign.id}/arcs/${arc.id}/edit`);
    await expect(page.getByLabel(/Titre de l'arc/i)).toHaveValue(arc.name);
  });

  test('edits all narrative fields and persists them to API', async ({ page, request }) => {
    const newName = `${arc.name} renamed`;
    const values = {
      description: "Un arc sombre où la trahison s'installe.",
      themes: 'Trahison, rédemption, dette de sang.',
      stakes: 'La survie du royaume est en jeu.',
      gmNotes: 'Révéler le traître en scène 3.',
      rewards: 'Relique ancienne + alliance avec le clan nordique.',
      resolution: 'Le héros pardonne au traître ou le tue.',
    };

    await page.goto(`/campaigns/${campaign.id}/arcs/${arc.id}/edit`);

    await page.getByLabel(/Titre de l'arc/i).fill(newName);
    await page.getByLabel(/Synopsis de l'arc/i).fill(values.description);
    await page.getByLabel(/Thèmes principaux/i).fill(values.themes);
    await page.getByLabel(/Enjeux globaux/i).fill(values.stakes);
    await page.getByLabel(/Notes et planification du MJ/i).fill(values.gmNotes);
    await page.getByLabel(/Récompenses et progression/i).fill(values.rewards);
    await page.getByLabel(/Dénouement prévu/i).fill(values.resolution);

    await page.getByRole('button', { name: /^Sauvegarder$/i }).click();

    await expect(page).toHaveURL(new RegExp(`/campaigns/${campaign.id}/arcs/${arc.id}$`));

    const persisted = await getArcById(request, arc.id);
    expect(persisted.name).toBe(newName);
    expect(persisted.description).toBe(values.description);
    expect(persisted.themes).toBe(values.themes);
    expect(persisted.stakes).toBe(values.stakes);
    expect(persisted.gmNotes).toBe(values.gmNotes);
    expect(persisted.rewards).toBe(values.rewards);
    expect(persisted.resolution).toBe(values.resolution);
  });

  test('save button is disabled when name is empty', async ({ page }) => {
    await page.goto(`/campaigns/${campaign.id}/arcs/${arc.id}/edit`);

    const nameField = page.getByLabel(/Titre de l'arc/i);
    const saveBtn = page.getByRole('button', { name: /^Sauvegarder$/i });

    await expect(saveBtn).toBeEnabled();
    await nameField.fill('');
    await expect(saveBtn).toBeDisabled();
    await nameField.fill('Valid');
    await expect(saveBtn).toBeEnabled();
  });
});
