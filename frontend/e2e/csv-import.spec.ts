import { expect, test } from '@playwright/test';

import { dialogButton, fillField, findRow, navigateTo, selectOption, signIn } from './app';

/**
 * CSV import end to end: an administrator prepares a period, a reporter uploads a file with one
 * good row and two bad ones, and the screen reports exactly what was applied and what was not.
 */

const RUN = Date.now().toString().slice(-6);
const ORG_CODE = `IMP${RUN}`;
const METRIC_CODE = `IMP_METRIC_${RUN}`;
const PERIOD_CODE = `IMP-${RUN}`;
const YEAR = 2400 + (Number(RUN) % 300);

const CSV = [
  'organization_code,metric_code,value,note',
  `${ORG_CODE},${METRIC_CODE},42,from the finance export`,
  `${ORG_CODE},NOT_A_METRIC,7,`,
  `NOWHERE,${METRIC_CODE},9,`,
  '',
].join('\n');

test('a reporter imports a CSV and sees exactly what was applied', async ({ browser }) => {
  // Two sign-ins, a full period set-up and an asynchronous import: slower than the default.
  test.setTimeout(420_000);

  const reporter = await signIn(browser, 'reporter');
  const admin = await signIn(browser, 'admin');

  await test.step('an administrator prepares the period', async () => {
    await navigateTo(admin, 'Organizations');
    await admin.getByRole('button', { name: 'New organization' }).click();
    await fillField(admin, 'Code', ORG_CODE);
    await fillField(admin, 'Name', `Import unit ${RUN}`);
    await dialogButton(admin, 'Save').click();

    await (await findRow(admin, ORG_CODE)).getByRole('link').first().click();
    await admin.getByLabel('Add a member').fill('reporter');
    await admin.getByRole('option', { name: /reporter/ }).click();
    await expect(admin.locator('.members').getByText('Ria Reporter')).toBeVisible();

    await navigateTo(admin, 'Metrics');
    await admin.getByRole('button', { name: 'New metric' }).click();
    await fillField(admin, 'Code', METRIC_CODE);
    await fillField(admin, 'Name', `Imported headcount ${RUN}`);
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
    await admin.getByRole('checkbox', { name: new RegExp(ORG_CODE) }).check();
    await admin.getByRole('button', { name: 'Save organizations' }).click();
    await expect(admin.getByText('Participating organizations saved')).toBeVisible();
    await admin.getByRole('button', { name: 'Open period' }).click();
    await dialogButton(admin, 'Open period').click();
    await expect(admin.getByText('Period opened').first()).toBeVisible();
  });

  await test.step('the reporter uploads the file', async () => {
    await navigateTo(reporter, 'CSV imports');
    await reporter.locator('[data-action="new-import"]').click();
    await selectOption(reporter, 'Reporting period', new RegExp(PERIOD_CODE));
    await selectOption(reporter, 'Organization', new RegExp(ORG_CODE));
    await reporter.locator('[data-testid="import-file"]').setInputFiles({
      name: 'values.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from(CSV, 'utf-8'),
    });
    await reporter.locator('[data-action="upload"]').click();
    await reporter.waitForURL(/\/imports\/[0-9a-f-]+$/, { timeout: 15_000 });
  });

  await test.step('the import reports what it applied and what it refused', async () => {
    await expect(reporter.locator('.status-bar app-status-chip')).toHaveText('COMPLETED', {
      timeout: 30_000,
    });
    await expect(reporter.locator('[data-testid="valid-rows"]')).toHaveText('1');
    await expect(reporter.locator('[data-testid="invalid-rows"]')).toHaveText('2');

    const errors = reporter.locator('[data-testid="import-errors"]');
    await expect(errors).toContainText('METRIC_UNKNOWN');
    await expect(errors).toContainText('ORG_UNKNOWN');
  });

  await test.step('the imported value is on the submission itself', async () => {
    await navigateTo(reporter, 'Submissions');
    await reporter
      .getByRole('row', { name: new RegExp(ORG_CODE) })
      .getByRole('link')
      .click();
    await reporter.waitForURL(/\/submissions\/[0-9a-f-]+$/, { timeout: 15_000 });

    await expect(reporter.locator(`[data-metric="${METRIC_CODE}"]`)).toHaveValue('42');
    await expect(reporter.locator('.status-bar app-status-chip')).toHaveText('DRAFT');
  });

  await test.step('the history lists the import', async () => {
    await navigateTo(reporter, 'CSV imports');
    // Newest first, and earlier runs leave rows of their own behind.
    const latest = reporter.getByRole('row', { name: /values\.csv/ }).first();
    await expect(latest).toBeVisible();
    await expect(latest).toContainText('1 applied · 2 rejected of 3');
    await expect(latest).toContainText('COMPLETED');
  });
});
