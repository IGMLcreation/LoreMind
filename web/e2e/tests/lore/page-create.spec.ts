import { test, expect } from '@playwright/test';
import {
  seedLoreWithFolder,
  seedTemplate,
  deleteLore,
  getPagesForLore,
  type SeededLore,
  type SeededTemplate,
} from '../../fixtures/api';

test.describe('Page creation', () => {
  let seeded: SeededLore;
  let template: SeededTemplate;

  test.beforeEach(async ({ request }) => {
    seeded = await seedLoreWithFolder(request);
    template = await seedTemplate(request, {
      loreId: seeded.id,
      defaultNodeId: seeded.rootFolderId,
      fieldNames: ['Apparence', 'Motivation'],
    });
  });

  test.afterEach(async ({ request }) => {
    if (seeded?.id) await deleteLore(request, seeded.id);
  });

  test('creates an empty page from a template and redirects to edit', async ({ page, request }) => {
    const pageTitle = `Maître Eldrin ${Date.now()}`;

    await page.goto(`/lore/${seeded.id}/pages/create`);

    await expect(page.getByRole('heading', { name: /Créer une nouvelle Page/i })).toBeVisible();

    await page.getByLabel(/Titre de la page/i).fill(pageTitle);

    await page.locator('.template-card', { hasText: template.name }).click();
    await expect(page.locator('.template-card.selected', { hasText: template.name })).toBeVisible();

    await expect(page.locator('#page-node')).toHaveValue(seeded.rootFolderId);

    await page.getByRole('button', { name: /^Créer la page$/i }).click();

    await expect(page).toHaveURL(new RegExp(`/lore/${seeded.id}/pages/[^/]+/edit$`));

    const pages = await getPagesForLore(request, seeded.id);
    const created = pages.find((p) => p.title === pageTitle);
    expect(created).toBeDefined();
    expect(created?.templateId).toBe(template.id);
    expect(created?.nodeId).toBe(seeded.rootFolderId);
  });

  test('submit is disabled until title, template and folder are set', async ({ page, request }) => {
    // On seed un 2ᵉ template pour empêcher l'auto-sélection (qui se déclenche
    // quand un seul template a un defaultNodeId valide). Avec deux candidats,
    // l'utilisateur doit choisir explicitement → on retrouve le comportement
    // initial du test : submit disabled tant qu'un template n'est pas cliqué.
    const secondFolderRes = await request.post('/api/lore-nodes', {
      data: { loreId: seeded.id, name: 'Autre dossier', icon: 'folder', description: '' },
    });
    const secondFolderId = (await secondFolderRes.json()).id;
    await seedTemplate(request, {
      loreId: seeded.id,
      defaultNodeId: secondFolderId,
      name: `Second template ${Date.now()}`,
    });

    await page.goto(`/lore/${seeded.id}/pages/create`);

    const submit = page.getByRole('button', { name: /^Créer la page$/i });
    await expect(submit).toBeDisabled();

    await page.getByLabel(/Titre de la page/i).fill('Un titre');
    await expect(submit).toBeDisabled();

    await page.locator('.template-card', { hasText: template.name }).click();
    await expect(submit).toBeEnabled();
  });

  test('entering on a folder-scoped route preselects that folder', async ({ page, request }) => {
    const pageTitle = `Page scoped ${Date.now()}`;

    // Dossier sans template par défaut → pas d'auto-sélection de template,
    // l'utilisateur clique manuellement (ce qu'on veut tester ici).
    const secondFolderRes = await request.post('/api/lore-nodes', {
      data: { loreId: seeded.id, name: 'Autre dossier', icon: 'folder', description: '' },
    });
    expect(secondFolderRes.ok()).toBeTruthy();
    const secondFolderId = (await secondFolderRes.json()).id;

    await page.goto(`/lore/${seeded.id}/nodes/${secondFolderId}/pages/create`);

    const nodeSelect = page.locator('#page-node');
    await expect(nodeSelect).toHaveValue(secondFolderId);
    await expect(nodeSelect).toBeDisabled();

    await page.getByLabel(/Titre de la page/i).fill(pageTitle);
    await page.locator('.template-card', { hasText: template.name }).click();
    await page.getByRole('button', { name: /^Créer la page$/i }).click();

    await expect(page).toHaveURL(new RegExp(`/lore/${seeded.id}/pages/[^/]+/edit$`));

    const pages = await getPagesForLore(request, seeded.id);
    expect(pages.find((p) => p.title === pageTitle)?.nodeId).toBe(secondFolderId);
  });

  test('auto-selects the template on free route when it is the only candidate', async ({ page }) => {
    // Le seed donne EXACTEMENT 1 template avec defaultNodeId valide → la
    // logique d'auto-sélection doit s'enclencher au chargement.
    await page.goto(`/lore/${seeded.id}/pages/create`);

    await expect(page.locator('.template-card.selected', { hasText: template.name })).toBeVisible();
    await expect(page.locator('#page-node')).toHaveValue(seeded.rootFolderId);

    // Conséquence : juste taper un titre suffit pour activer le submit.
    const submit = page.getByRole('button', { name: /^Créer la page$/i });
    await expect(submit).toBeDisabled();
    await page.getByLabel(/Titre de la page/i).fill('Auto');
    await expect(submit).toBeEnabled();
  });

  test('auto-selects the template on folder-scoped route when its defaultNodeId matches', async ({
    page,
  }) => {
    // Le template seedé pointe sur seeded.rootFolderId — entrer sur la route
    // folder-scoped de ce dossier doit auto-sélectionner ce template.
    await page.goto(`/lore/${seeded.id}/nodes/${seeded.rootFolderId}/pages/create`);

    await expect(page.locator('.template-card.selected', { hasText: template.name })).toBeVisible();
    await expect(page.locator('#page-node')).toHaveValue(seeded.rootFolderId);
    await expect(page.locator('#page-node')).toBeDisabled();
  });
});
