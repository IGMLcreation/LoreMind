import { test, expect } from '@playwright/test';
import {
  seedCampaign,
  seedArc,
  seedChapter,
  seedScene,
  deleteCampaign,
  getSceneById,
  type SeededCampaign,
  type SeededArc,
  type SeededChapter,
  type SeededScene,
} from '../../fixtures/api';

test.describe('Scene edit', () => {
  let campaign: SeededCampaign;
  let arc: SeededArc;
  let chapter: SeededChapter;
  let scene: SeededScene;

  test.beforeEach(async ({ request }) => {
    campaign = await seedCampaign(request);
    arc = await seedArc(request, { campaignId: campaign.id });
    chapter = await seedChapter(request, { arcId: arc.id });
    scene = await seedScene(request, { chapterId: chapter.id });
  });

  test.afterEach(async ({ request }) => {
    if (campaign?.id) await deleteCampaign(request, campaign.id);
  });

  test('edits all text fields across sections and persists them to API', async ({ page, request }) => {
    const newName = `${scene.name} renamed`;
    const values = {
      description: "Les PJ arrivent au village à la nuit tombée.",
      location: "Taverne du Dragon d'Or",
      timing: 'Soir, pleine lune',
      atmosphere: 'Silence pesant, regards fuyants des villageois.',
      playerNarration: 'Vous poussez la porte de la taverne…',
      gmSecretNotes: 'Le tavernier est complice des bandits.',
      choicesConsequences: 'Accepter = piégés à l\'étage. Refuser = filature.',
      combatDifficulty: 'Moyenne, 4 bandits niveau 3',
      enemies: 'Bandit chef (feuille jointe) + 3 sbires.',
    };

    await page.goto(
      `/campaigns/${campaign.id}/arcs/${arc.id}/chapters/${chapter.id}/scenes/${scene.id}/edit`,
    );

    await expect(page.getByLabel(/Titre de la scène/i)).toHaveValue(scene.name);

    await page.getByLabel(/Titre de la scène/i).fill(newName);
    await page.getByLabel(/Description courte/i).fill(values.description);
    await page.getByLabel(/^Lieu$/i).fill(values.location);
    await page.getByLabel(/^Moment$/i).fill(values.timing);
    await page.getByLabel(/Ambiance et atmosphère/i).fill(values.atmosphere);

    // Les sections suivantes sont fermées par défaut : on les ouvre avant de taper.
    // Un clic sur le header de la section toggle son état.
    await page.locator('app-expandable-section', { hasText: 'Narration pour les joueurs' }).click();
    await page.getByPlaceholder(/Le texte que vous lirez aux joueurs/i).fill(values.playerNarration);

    await page.locator('app-expandable-section', { hasText: 'Notes et secrets du MJ' }).click();
    await page
      .getByPlaceholder(/Informations cachées, indices, éléments secrets/i)
      .fill(values.gmSecretNotes);

    await page.locator('app-expandable-section', { hasText: 'Choix et conséquences' }).click();
    await page
      .getByPlaceholder(/Décrivez les différentes options/i)
      .fill(values.choicesConsequences);

    await page.locator('app-expandable-section', { hasText: 'Combat ou rencontre' }).click();
    await page.getByLabel(/Difficulté estimée/i).fill(values.combatDifficulty);
    await page.getByLabel(/Ennemis et créatures/i).fill(values.enemies);

    await page.getByRole('button', { name: /^Sauvegarder$/i }).click();

    await expect(page).toHaveURL(
      new RegExp(
        `/campaigns/${campaign.id}/arcs/${arc.id}/chapters/${chapter.id}/scenes/${scene.id}$`,
      ),
    );

    const persisted = await getSceneById(request, scene.id);
    expect(persisted.name).toBe(newName);
    expect(persisted.description).toBe(values.description);
    expect(persisted.location).toBe(values.location);
    expect(persisted.timing).toBe(values.timing);
    expect(persisted.atmosphere).toBe(values.atmosphere);
    expect(persisted.playerNarration).toBe(values.playerNarration);
    expect(persisted.gmSecretNotes).toBe(values.gmSecretNotes);
    expect(persisted.choicesConsequences).toBe(values.choicesConsequences);
    expect(persisted.combatDifficulty).toBe(values.combatDifficulty);
    expect(persisted.enemies).toBe(values.enemies);
  });

  test('adds a narrative branch to a sibling scene and persists it', async ({ page, request }) => {
    const sibling = await seedScene(request, {
      chapterId: chapter.id,
      name: 'Scène alternative',
      order: 2,
    });

    await page.goto(
      `/campaigns/${campaign.id}/arcs/${arc.id}/chapters/${chapter.id}/scenes/${scene.id}/edit`,
    );

    const branchesSection = page.locator('app-expandable-section', { hasText: 'Branches narratives' });
    await branchesSection.click();

    await branchesSection.getByRole('button', { name: /Ajouter une branche/i }).click();

    const branchItem = branchesSection.locator('.branch-item').first();
    await branchItem.getByPlaceholder(/Ex: Si les joueurs attaquent/i).fill('Si les PJ fuient');
    await branchItem.locator('select').selectOption({ label: sibling.name });
    await branchItem.getByPlaceholder(/Jet de Persuasion/i).fill('Sur échec initiative');

    await page.getByRole('button', { name: /^Sauvegarder$/i }).click();

    await expect(page).toHaveURL(
      new RegExp(
        `/campaigns/${campaign.id}/arcs/${arc.id}/chapters/${chapter.id}/scenes/${scene.id}$`,
      ),
    );

    const persisted = await getSceneById(request, scene.id);
    expect(persisted.branches).toHaveLength(1);
    expect(persisted.branches![0].label).toBe('Si les PJ fuient');
    expect(persisted.branches![0].targetSceneId).toBe(sibling.id);
    expect(persisted.branches![0].condition).toBe('Sur échec initiative');
  });

  test('save button is disabled when name is empty', async ({ page }) => {
    await page.goto(
      `/campaigns/${campaign.id}/arcs/${arc.id}/chapters/${chapter.id}/scenes/${scene.id}/edit`,
    );
    const nameField = page.getByLabel(/Titre de la scène/i);
    const saveBtn = page.getByRole('button', { name: /^Sauvegarder$/i });

    await expect(saveBtn).toBeEnabled();
    await nameField.fill('');
    await expect(saveBtn).toBeDisabled();
    await nameField.fill('OK');
    await expect(saveBtn).toBeEnabled();
  });
});
