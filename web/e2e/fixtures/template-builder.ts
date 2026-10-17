import { Page, expect } from '@playwright/test';

/**
 * Ajoute un bloc Texte au builder de template (grille de blocs) et le renomme.
 *
 * Le builder n'a plus de champ « + Ajouter un champ » : un clic sur un type de
 * la palette ajoute en bas de la grille un bloc au nom par défaut (« Texte »,
 * « Texte 2 »…), qu'on renomme dans son en-tête. Le nom est validé au blur.
 */
export async function addTextBlock(page: Page, name: string): Promise<void> {
  const blocks = page.locator('.grid-block');
  const before = await blocks.count();

  await page.locator('.palette-item', { hasText: /^\s*Texte\s*$/ }).click();
  await expect(blocks).toHaveCount(before + 1);

  const nameInput = blocks.last().locator('.block-name-input');
  await nameInput.fill(name);
  await nameInput.blur();
  await expect(page.locator(`.grid-block[data-block-name="${name}"]`)).toBeVisible();
}
