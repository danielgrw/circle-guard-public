import { defineConfig, devices } from '@playwright/test';

/** Base URL hint for `request` context; specs use deriveServiceOrigin in utils for per-service calls. */

function resolveBaseUrl(): string {
  const stage = process.env.STAGE_BASE_URL?.trim();
  const dev = process.env.DEV_BASE_URL?.trim();
  if (process.env.CI && !stage && !dev) {
    throw new Error(
      'Playwright E2E in CI requires STAGE_BASE_URL or DEV_BASE_URL (inject from Jenkins when the Stage gate runs tests).'
    );
  }
  return stage || dev || 'http://localhost:8087';
}

const baseURL = resolveBaseUrl();

export default defineConfig({
  testDir: './specs',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,
  reporter: [['html', { outputFolder: 'reports', open: 'never' }]],
  use: {
    baseURL,
    trace: 'on-first-retry',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
