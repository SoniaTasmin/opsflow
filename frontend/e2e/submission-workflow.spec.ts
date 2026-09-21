import { Page, expect, test } from '@playwright/test';

import { dialogButton, fillField, navigateTo, signIn } from './app';

/**
 * The milestone's acceptance scenario, driven as a real user: an administrator sets a period
 * up, a reporter fills it in, a reviewer returns it, and the correction is approved.
 *
 * Each role gets its own browser context. Tokens are held in memory, so a shared context would
 * mean signing out and back in between every step.
 */

const RUN = Date.now().toString().slice(-6);
const ORG_CODE = `E2E${RUN}`;
const METRIC_CODE = `E2E_METRIC_${RUN}`;
const PERIOD_CODE = `E2E-${RUN}`;

// A far-future month, unique per run: periods of the same type may not overlap.
const YEAR = 2100 + (Number(RUN) % 300);
const STARTS = `1/1/${YEAR}`;
const ENDS = `1/31/${YEAR}`;
const DEADLINE = `2/15/${YEAR}`;

async function openSubmission(page: Page, navLabel: string, rowPattern: RegExp): Promise<void> {
  await navigateTo(page, navLabel);
  await page.getByRole('row', { name: rowPattern }).getByRole('link').click();
  await page.waitForURL(/\/submissions\/[0-9a-f-]+$/, { timeout: 15_000 });
}

test('a reporter submits, a reviewer returns it, and the correction is approved', async ({
  browser,
}) => {
  test.slow();

  // The member picker only offers users the backend has seen, so the reporter signs in first.
  const reporter = await signIn(browser, 'reporter');
  const admin = await signIn(browser, 'admin');

  await test.step('the administrator creates an organization and adds the reporter', async () => {
    await navigateTo(admin, 'Organizations');
    await admin.getByRole('button', { name: 'New organization' }).click();
    await fillField(admin, 'Code', ORG_CODE);
    await fillField(admin, 'Name', `End-to-end unit ${RUN}`);
    await dialogButton(admin, 'Save').click();

    const row = admin.getByRole('row', { name: new RegExp(ORG_CODE) });
    await expect(row).toBeVisible();
    await row.getByRole('link').first().click();

    await admin.getByLabel('Add a member').fill('reporter');
    await admin.getByRole('option', { name: /reporter/ }).click();
    // Assert on the member list, not the search box: the typed text would satisfy that on its own.
    await expect(admin.locator('.members').getByText('Ria Reporter')).toBeVisible();
  });

  await test.step('the administrator defines a metric', async () => {
    await navigateTo(admin, 'Metrics');
    await admin.getByRole('button', { name: 'New metric' }).click();
    await fillField(admin, 'Code', METRIC_CODE);
    await fillField(admin, 'Name', `Headcount ${RUN}`);
    await fillField(admin, 'Unit', 'people');
    await dialogButton(admin, 'Save').click();
    await expect(admin.getByText(METRIC_CODE)).toBeVisible();
  });

  await test.step('the administrator opens a period scoped to that organization', async () => {
    await navigateTo(admin, 'Reporting periods');
    await admin.getByRole('button', { name: 'New period' }).click();
    await fillField(admin, 'Code', PERIOD_CODE);
    await fillField(admin, 'Starts on', STARTS);
    await fillField(admin, 'Ends on', ENDS);
    await fillField(admin, 'Submission deadline', DEADLINE);
    await dialogButton(admin, 'Create').click();

    await admin
      .getByRole('row', { name: new RegExp(PERIOD_CODE) })
      .getByRole('link')
      .click();

    // Each save reloads the screen from the server, so the next edit waits for it to land --
    // otherwise the reload discards the tick just made.
    await admin.getByRole('checkbox', { name: new RegExp(METRIC_CODE) }).check();
    await admin.getByRole('button', { name: 'Save metric scope' }).click();
    await expect(admin.getByText('Metric scope saved')).toBeVisible();

    await admin.getByRole('checkbox', { name: new RegExp(ORG_CODE) }).check();
    await admin.getByRole('button', { name: 'Save organizations' }).click();
    await expect(admin.getByText('Participating organizations saved')).toBeVisible();

    await admin.getByRole('button', { name: 'Open period' }).click();
    await dialogButton(admin, 'Open period').click();
    await expect(admin.getByText('Period opened').first()).toBeVisible();
  });

  await test.step('the reporter starts a submission and sends it for review', async () => {
    await navigateTo(reporter, 'Submissions');
    await reporter.getByRole('button', { name: 'Start submission' }).click();
    await reporter.getByRole('dialog').getByLabel('Organization').click();
    await reporter.getByRole('option', { name: new RegExp(ORG_CODE) }).click();
    await reporter.getByRole('dialog').getByLabel('Reporting period').click();
    await reporter.getByRole('option', { name: new RegExp(PERIOD_CODE) }).click();
    await dialogButton(reporter, 'Start').click();

    await expect(reporter.locator('.status-bar app-status-chip')).toHaveText('DRAFT');
    await reporter.locator(`[data-metric="${METRIC_CODE}"]`).fill('42');
    await reporter.locator('[data-action="save"]').click();
    await expect(reporter.getByText('Values saved').first()).toBeVisible();

    await reporter.locator('[data-action="submit"]').click();
    await expect(reporter.locator('.status-bar app-status-chip')).toHaveText('SUBMITTED');
  });

  const reviewer = await signIn(browser, 'reviewer');

  await test.step('the reviewer claims it and returns it for correction', async () => {
    await navigateTo(reviewer, 'Review queue');
    await reviewer
      .getByRole('row', { name: new RegExp(ORG_CODE) })
      .getByRole('link')
      .click();

    await reviewer.locator('[data-action="claim"]').click();
    await expect(reviewer.locator('.status-bar app-status-chip')).toHaveText('UNDER_REVIEW');

    await reviewer.locator('[data-action="reject"]').click();
    await reviewer
      .getByRole('dialog')
      .getByLabel('Reason')
      .fill('Headcount does not match the payroll export');
    await dialogButton(reviewer, 'Reject').click();
    await expect(reviewer.locator('.status-bar app-status-chip')).toHaveText('REJECTED');
  });

  await test.step('the reporter sees why and resubmits', async () => {
    await openSubmission(reporter, 'Submissions', new RegExp(ORG_CODE));
    // The reason appears twice: the banner at the top and the timeline entry that produced it.
    await expect(reporter.getByText('Headcount does not match the payroll export')).toHaveCount(2);

    await reporter.locator(`[data-metric="${METRIC_CODE}"]`).fill('44');
    await reporter.locator('[data-action="save"]').click();
    await expect(reporter.getByText('Values saved').first()).toBeVisible();
    await reporter.locator('[data-action="submit"]').click();
    await expect(reporter.locator('.status-bar app-status-chip')).toHaveText('SUBMITTED');
  });

  await test.step('the reviewer approves the correction', async () => {
    await openSubmission(reviewer, 'Review queue', new RegExp(ORG_CODE));
    await reviewer.locator('[data-action="claim"]').click();
    await reviewer.locator('[data-action="approve"]').click();
    await dialogButton(reviewer, 'Approve').click();

    await expect(reviewer.locator('.status-bar app-status-chip')).toHaveText('APPROVED');
    // The history is the workflow record: one entry per real transition, creation included.
    await expect(reviewer.getByText('UNDER_REVIEW → APPROVED')).toBeVisible();
    await expect(reviewer.getByText('UNDER_REVIEW → REJECTED')).toBeVisible();
  });

  await test.step('the approved submission can no longer be edited', async () => {
    await openSubmission(reporter, 'Submissions', new RegExp(ORG_CODE));
    await expect(reporter.locator('.status-bar app-status-chip')).toHaveText('APPROVED');
    await expect(reporter.locator('[data-action="save"]')).toHaveCount(0);
  });
});
