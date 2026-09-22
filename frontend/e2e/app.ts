import { Browser, Locator, Page, expect } from '@playwright/test';

/** Shared driving helpers: both specs sign in for real and navigate through the shell. */

export const NAV: Record<string, string> = {
  Home: '/home',
  'Period summary': '/summary',
  Organizations: '/admin/organizations',
  Metrics: '/admin/metrics',
  'Reporting periods': '/admin/periods',
  Submissions: '/submissions',
  'Review queue': '/review',
  'CSV imports': '/imports',
};

export async function signIn(browser: Browser, username: string): Promise<Page> {
  const page = await (await browser.newContext()).newPage();
  await page.goto('/');
  await page.waitForURL(/realms\/opsflow/, { timeout: 30_000 });
  await page.locator('#username').fill(username);
  await page.locator('#password').fill('password');
  await page.locator('#kc-login').click();
  await page.waitForURL((url) => !url.pathname.includes('/realms/'), { timeout: 30_000 });
  await expect(page.getByRole('heading', { name: 'Welcome' })).toBeVisible();
  return page;
}

/**
 * Tokens live in memory, so a full page load lands the user back on the home screen after a
 * silent re-authentication. Every navigation therefore goes through the shell's own links.
 */
export async function navigateTo(page: Page, label: string): Promise<void> {
  // The nav link's accessible name carries the Material icon ligature as well as the label.
  await page.getByRole('link', { name: new RegExp(`${label}$`) }).click();
  await page.waitForURL(new RegExp(`${NAV[label]}$`), { timeout: 15_000 });
}

/** Dialog fields are scoped to the open dialog so a closing one cannot be matched instead. */
export async function fillField(page: Page, label: string, value: string): Promise<void> {
  await page.getByRole('dialog').getByLabel(label, { exact: true }).fill(value);
}

export function dialogButton(page: Page, name: string): Locator {
  return page.getByRole('dialog').getByRole('button', { name, exact: true });
}

/**
 * Admin lists are paginated and ordered by code, so a freshly created row is not necessarily on
 * the first page once a database has some history. Search for it rather than assuming.
 */
export async function findRow(page: Page, code: string): Promise<Locator> {
  // The label is shared with the search button beside the field, and the search runs on Enter.
  await page.getByPlaceholder('Code or name').fill(code);
  await page.getByPlaceholder('Code or name').press('Enter');
  const row = page.getByRole('row', { name: new RegExp(code) });
  await expect(row).toBeVisible();
  return row;
}

export async function selectOption(page: Page, label: string, option: RegExp): Promise<void> {
  await page.getByRole('dialog').getByLabel(label).click();
  await page.getByRole('option', { name: option }).click();
}
