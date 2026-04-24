import { test, expect } from '@playwright/test';
import {
  seedCampaign,
  seedArc,
  seedChapter,
  deleteCampaign,
  getChapterById,
  type SeededCampaign,
  type SeededArc,
  type SeededChapter,
} from '../../fixtures/api';

test.describe('Chapter edit', () => {
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

  test('edits all chapter fields and persists them to API', async ({ page, request }) => {
    const newName = `${chapter.name} renamed`;
    const values = {
      description: 'Le chapitre ouvre sur un village en proie à la peur.',
      gmNotes: 'Le maire cache un pacte avec les gobelins.',
      playerObjectives: "Découvrir la source des disparitions.",
      narrativeStakes: "La confiance du village est en jeu.",
    };

    await page.goto(`/campaigns/${campaign.id}/arcs/${arc.id}/chapters/${chapter.id}/edit`);

    await expect(page.getByLabel(/Titre du chapitre/i)).toHaveValue(chapter.name);

    await page.getByLabel(/Titre du chapitre/i).fill(newName);
    await page.getByLabel(/Synopsis du chapitre/i).fill(values.description);
    await page.getByLabel(/Notes du Maître de Jeu/i).fill(values.gmNotes);
    await page.getByLabel(/Objectifs des joueurs/i).fill(values.playerObjectives);
    await page.getByLabel(/Enjeux narratifs/i).fill(values.narrativeStakes);

    await page.getByRole('button', { name: /^Sauvegarder$/i }).click();

    await expect(page).toHaveURL(
      new RegExp(`/campaigns/${campaign.id}/arcs/${arc.id}/chapters/${chapter.id}$`),
    );

    const persisted = await getChapterById(request, chapter.id);
    expect(persisted.name).toBe(newName);
    expect(persisted.description).toBe(values.description);
    expect(persisted.gmNotes).toBe(values.gmNotes);
    expect(persisted.playerObjectives).toBe(values.playerObjectives);
    expect(persisted.narrativeStakes).toBe(values.narrativeStakes);
  });

  test('save button is disabled when name is empty', async ({ page }) => {
    await page.goto(`/campaigns/${campaign.id}/arcs/${arc.id}/chapters/${chapter.id}/edit`);
    const nameField = page.getByLabel(/Titre du chapitre/i);
    const saveBtn = page.getByRole('button', { name: /^Sauvegarder$/i });

    await expect(saveBtn).toBeEnabled();
    await nameField.fill('');
    await expect(saveBtn).toBeDisabled();
    await nameField.fill('OK');
    await expect(saveBtn).toBeEnabled();
  });
});
