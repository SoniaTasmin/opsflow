import { defineConfig } from '@playwright/test';

/**
 * Smoke coverage only, against a stack that is already running:
 * `make up`, the backend, and `npm run build && npm run e2e:serve`.
 * The dev server is deliberately not used — its watcher is unreliable on
 * Windows-mounted volumes and silently serves stale bundles.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 15_000 },
  use: {
    baseURL: process.env.OPSFLOW_UI_URL ?? 'http://localhost:4200',
    trace: 'retain-on-failure',
  },
  // One worker: every spec drives the same stack, so parallel runs contend for the same
  // Keycloak login flow and create data the others can trip over.
  workers: 1,
  reporter: [['list']],
});
