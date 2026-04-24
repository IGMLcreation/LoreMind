import { test, expect } from '@playwright/test';
import { deleteLore } from '../../fixtures/api';

test.describe('Lore creation', () => {
  const createdLoreIds: string[] = [];

  test.afterEach(async ({ request }) => {
    while (createdLoreIds.length) {
      const id = createdLoreIds.pop()!;
      await deleteLore(request, id);
    }
  });

  test('opens the modal, creates a lore, and shows it in the grid', async ({ page, request }) => {
    const loreName = `Univers E2E ${Date.now()}`;
    const description = "Un univers créé par les tests automatisés.";

    await page.goto('/lore');

    await expect(page.getByRole('heading', { name: /Vos Univers/i })).toBeVisible();

    await page.locator('.lore-card.card-new').click();

    const modal = page.locator('.modal');
    await expect(modal).toBeVisible();
    await expect(modal.getByRole('heading', { name: /Créer un nouveau Lore/i })).toBeVisible();

    await modal.getByLabel(/Nom de l'univers/i).fill(loreName);
    await modal.getByLabel('Description').fill(description);

    await modal.getByRole('button', { name: /^Créer le lore$/i }).click();

    await expect(modal).not.toBeVisible();

    const newCard = page.locator('.lore-card', { hasText: loreName });
    await expect(newCard).toBeVisible();
    await expect(newCard).toContainText(description);

    const allLores = await request.get('/api/lores').then((r) => r.json());
    const created = allLores.find((l: { name: string; id: string }) => l.name === loreName);
    expect(created).toBeDefined();
    createdLoreIds.push(created.id);
  });

  test('submit button is disabled when name is empty', async ({ page }) => {
    await page.goto('/lore');
    await page.locator('.lore-card.card-new').click();

    const modal = page.locator('.modal');
    const submit = modal.getByRole('button', { name: /^Créer le lore$/i });

    await expect(submit).toBeDisabled();

    await modal.getByLabel(/Nom de l'univers/i).fill('Quelque chose');
    await expect(submit).toBeEnabled();

    await modal.getByLabel(/Nom de l'univers/i).fill('');
    await expect(submit).toBeDisabled();
  });

  test('clicking the backdrop closes the modal without creating', async ({ page }) => {
    const typedButAbandoned = `Univers abandonné ${Date.now()}`;

    await page.goto('/lore');
    await page.locator('.lore-card.card-new').click();

    const modal = page.locator('.modal');
    await expect(modal).toBeVisible();

    await modal.getByLabel(/Nom de l'univers/i).fill(typedButAbandoned);

    await page.locator('.modal-backdrop').click({ position: { x: 5, y: 5 } });
    await expect(modal).not.toBeVisible();

    await expect(page.locator('.lore-card', { hasText: typedButAbandoned })).toHaveCount(0);
  });
});
