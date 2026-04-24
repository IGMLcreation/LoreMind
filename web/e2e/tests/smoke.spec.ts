import { test, expect } from '@playwright/test';

test.describe('Smoke', () => {
  test('app loads without uncaught errors', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', (err) => errors.push(err.message));

    await page.goto('/');

    await expect(page.locator('app-sidebar')).toBeVisible();
    await expect(page.locator('main.main-content')).toBeAttached();

    expect(errors, `Page errors:\n${errors.join('\n')}`).toEqual([]);
  });
});
