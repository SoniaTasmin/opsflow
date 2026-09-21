import { expect, test } from '@playwright/test';

/** Drives the real Keycloak login page rather than stubbing the token. */
async function signIn(page: import('@playwright/test').Page, username: string) {
  await page.goto('/');
  await page.waitForURL(/realms\/opsflow/, { timeout: 30_000 });
  // Keycloak's login form ids, which are stable across its themes.
  await page.locator('#username').fill(username);
  await page.locator('#password').fill('password');
  await page.locator('#kc-login').click();
  await page.waitForURL((url) => !url.pathname.includes('/realms/'), { timeout: 30_000 });
  // The callback is still being exchanged at this point; wait for the app to settle so a
  // later navigation does not race it into a second authorize redirect.
  await expect(page.getByRole('heading', { name: 'Welcome' })).toBeVisible();
}

test('an administrator signs in and reaches the admin screens', async ({ page }) => {
  await signIn(page, 'admin');

  await expect(page.getByRole('heading', { name: 'Welcome' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Organizations' })).toBeVisible();

  await page.getByRole('link', { name: 'Organizations' }).click();
  await expect(page.getByRole('heading', { name: 'Organizations' })).toBeVisible();
  await expect(page.getByText('BERLIN_OPS')).toBeVisible();

  await page.getByRole('link', { name: 'Metrics' }).click();
  await expect(page.getByRole('heading', { name: 'Metrics' })).toBeVisible();

  await page.getByRole('link', { name: 'Reporting periods' }).click();
  await expect(page.getByRole('heading', { name: 'Reporting periods' })).toBeVisible();
});

test('a reporter sees no administration navigation', async ({ page }) => {
  await signIn(page, 'reporter');

  await expect(page.getByRole('heading', { name: 'Welcome' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Organizations' })).toHaveCount(0);
  await expect(page.getByRole('link', { name: 'Metrics' })).toHaveCount(0);
});

test('a reporter never reaches an admin screen', async ({ page }) => {
  await signIn(page, 'reporter');

  // A deep link is a full page load, which discards the in-memory tokens and sends the
  // user back through the identity provider; they land on home rather than the requested
  // route. Either way the admin screen must not render.
  await page.goto('/admin/organizations');
  await page.waitForTimeout(3_000);

  await expect(page.getByRole('heading', { name: 'Organizations' })).toHaveCount(0);
  await expect(page.getByRole('columnheader', { name: 'Code' })).toHaveCount(0);
});

test('tokens are never written to browser storage', async ({ page }) => {
  await signIn(page, 'admin');
  // Without this the assertions below would also hold for a login that never completed.
  await expect(page.getByRole('link', { name: 'Organizations' })).toBeVisible();

  const stored = await page.evaluate(() => ({
    local: Object.entries(localStorage).map(([k, v]) => `${k}=${v}`),
    session: Object.entries(sessionStorage).map(([k, v]) => `${k}=${v}`),
  }));

  // An XSS bug must not be able to read an access token back out of the browser.
  expect(stored.local.join(' ')).not.toContain('eyJ');
  expect(stored.session.join(' ')).not.toContain('eyJ');
});
