import { expect, test } from '@playwright/test';

import { dialogButton, fillField, findRow, navigateTo, selectOption, signIn } from './app';

/**
 * The summary screen against a period the test creates and fills in, so the figures it asserts
 * are ones it caused rather than whatever the database happened to hold.
 */

const RUN = Date.now().toString().slice(-6);
const ORG_CODE = `SUM${RUN}`;
const METRIC_CODE = `SUM_METRIC_${RUN}`;
const PERIOD_CODE = `SUM-${RUN}`;
const YEAR = 2700 + (Number(RUN) % 200);

test('a period summary counts the organizations frozen into the period', async ({ browser }) => {
  test.setTimeout(420_000);

  const reporter = await signIn(browser, 'reporter');
  const admin = await signIn(browser, 'admin');

  await test.step('an administrator opens a period with two participants', async () => {
    await navigateTo(admin, 'Organizations');
    for (const suffix of ['A', 'B']) {
      await admin.getByRole('button', { name: 'New organization' }).click();
      await fillField(admin, 'Code', `${ORG_CODE}${suffix}`);
      await fillField(admin, 'Name', `Summary unit ${suffix} ${RUN}`);
      await dialogButton(admin, 'Save').click();
      await findRow(admin, `${ORG_CODE}${suffix}`);
    }

    await (await findRow(admin, `${ORG_CODE}A`)).getByRole('link').first().click();
    await admin.getByLabel('Add a member').fill('reporter');
    await admin.getByRole('option', { name: /reporter/ }).click();
    await expect(admin.locator('.members').getByText('Ria Reporter')).toBeVisible();

    await navigateTo(admin, 'Metrics');
    await admin.getByRole('button', { name: 'New metric' }).click();
    await fillField(admin, 'Code', METRIC_CODE);
    await fillField(admin, 'Name', `Summary headcount ${RUN}`);
    await fillField(admin, 'Unit', 'people');
    await dialogButton(admin, 'Save').click();
    await findRow(admin, METRIC_CODE);

    await navigateTo(admin, 'Reporting periods');
    await admin.getByRole('button', { name: 'New period' }).click();
    await fillField(admin, 'Code', PERIOD_CODE);
    await fillField(admin, 'Starts on', `1/1/${YEAR}`);
    await fillField(admin, 'Ends on', `1/31/${YEAR}`);
    await fillField(admin, 'Submission deadline', `2/15/${YEAR}`);
    await dialogButton(admin, 'Create').click();

    await admin
      .getByRole('row', { name: new RegExp(PERIOD_CODE) })
      .getByRole('link')
      .click();
    await admin.getByRole('checkbox', { name: new RegExp(METRIC_CODE) }).check();
    await admin.getByRole('button', { name: 'Save metric scope' }).click();
    await expect(admin.getByText('Metric scope saved')).toBeVisible();
    await admin.getByRole('checkbox', { name: new RegExp(`${ORG_CODE}A`) }).check();
    await admin.getByRole('checkbox', { name: new RegExp(`${ORG_CODE}B`) }).check();
    await admin.getByRole('button', { name: 'Save organizations' }).click();
    await expect(admin.getByText('Participating organizations saved')).toBeVisible();
    await admin.getByRole('button', { name: 'Open period' }).click();
    await dialogButton(admin, 'Open period').click();
    await expect(admin.getByText('Period opened').first()).toBeVisible();
  });

  await test.step('with nothing reported, both participants have not started', async () => {
    await navigateTo(admin, 'Period summary');
    await selectPeriod(admin);

    await expect(admin.locator('[data-testid="participants"]')).toContainText(
      '0 of 2 participating organizations',
    );
    await expect(admin.locator('[data-status="Not started"]')).toContainText('2');
    await expect(admin.locator('[data-testid="completion"]')).toContainText('0%');
  });

  await test.step('a draft moves one participant out of not started', async () => {
    await navigateTo(reporter, 'Submissions');
    await reporter.getByRole('button', { name: 'Start submission' }).click();
    await selectOption(reporter, 'Organization', new RegExp(`${ORG_CODE}A`));
    await selectOption(reporter, 'Reporting period', new RegExp(PERIOD_CODE));
    await dialogButton(reporter, 'Start').click();
    await expect(reporter.locator('.status-bar app-status-chip')).toHaveText('DRAFT');

    // Already on the summary with this period selected, so the figures need asking for again.
    await admin.locator('[data-action="refresh"]').click();

    await expect(admin.locator('[data-status="Draft"]')).toContainText('1');
    await expect(admin.locator('[data-status="Not started"]')).toContainText('1');
  });

  await test.step('the reporter sees only their own organization, denominator included', async () => {
    await navigateTo(reporter, 'Period summary');
    await selectPeriod(reporter);

    await expect(reporter.locator('[data-testid="scope"]')).toContainText('Your organizations');
    await expect(reporter.locator('[data-testid="participants"]')).toContainText(
      '0 of 1 participating organizations',
    );
  });
});

async function selectPeriod(page: import('@playwright/test').Page): Promise<void> {
  await page.locator('[data-testid="period-picker"]').click();
  await page.getByRole('option', { name: new RegExp(PERIOD_CODE) }).click();
  await expect(page.locator('[data-testid="summary"]')).toContainText(PERIOD_CODE);
}
