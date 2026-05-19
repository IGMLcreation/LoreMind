import { defineConfig, devices } from '@playwright/test';

// Par defaut on cible le serveur de dev Angular (ng serve) sur :4200 pour les
// runs locaux — c'est ce qu'on veut quand on bosse en TDD/dev sur le front.
// La CI (.gitea/workflows/e2e.yml) override avec `E2E_BASE_URL=http://web`
// pour cibler l'instance Docker dans le reseau du runner. Pour tester
// localement contre le container docker-compose, lancer :
//   E2E_BASE_URL=http://localhost:8081 npm run e2e
const baseURL = process.env['E2E_BASE_URL'] || 'http://localhost:4200';

export default defineConfig({
  testDir: './e2e/tests',
  fullyParallel: true,
  forbidOnly: !!process.env['CI'],
  retries: process.env['CI'] ? 2 : 0,
  workers: process.env['CI'] ? 1 : undefined,
  reporter: process.env['CI'] ? [['html', { open: 'never' }], ['list']] : 'html',
  use: {
    baseURL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
