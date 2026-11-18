import { Page, expect, test } from '@playwright/test';
import { CLAIMANT, SEEDED, dayMonthYear, freshPolicy, signIn } from './support';

const next = (page: Page) =>
  page.getByRole('button', { name: 'Next', exact: true }).filter({ visible: true }).click();
const field = (page: Page, label: string) => page.getByLabel(label).filter({ visible: true });
const error = (page: Page, label: string) =>
  page
    .locator('mat-form-field')
    .filter({ visible: true })
    .filter({ hasText: label })
    .locator('mat-error');

/** Opens the form on a new policy and fills the incident step correctly. */
async function startClaim(page: Page, source: string) {
  const policy = await freshPolicy(source);
  await signIn(page, CLAIMANT.email, 'customer');
  await page.goto(`/claims/new?policy=${policy.id}`);
  await next(page);
  await field(page, 'Date of incident').fill(dayMonthYear(2));
  await field(page, 'What happened?').fill('A car reversed into mine in a car park.');
  return policy;
}

test('the incident step checks the date, description and amount', async ({ page }) => {
  await startClaim(page, SEEDED.myMotor);
  const date = field(page, 'Date of incident');
  await date.fill('');
  await field(page, 'What happened?').fill('');
  await next(page);
  await expect(error(page, 'Date of incident')).toHaveText('Required');
  await expect(error(page, 'What happened?')).toHaveText('Required');

  await date.pressSequentially('ab'); // letters are not accepted
  await expect(date).toHaveValue('');

  await date.fill('31/02/2026');
  await date.blur();
  await expect(error(page, 'Date of incident')).toHaveText('Enter a valid date as dd/mm/yyyy');
  await date.fill(dayMonthYear(-3));
  await date.blur();
  await expect(error(page, 'Date of incident')).toHaveText(
    'The date cannot be in the future or after your cover ended',
  );
  await date.fill('15/12/2025');
  await date.blur();
  await expect(error(page, 'Date of incident')).toHaveText(
    'This is before your policy cover started',
  );
  await date.fill(dayMonthYear(2));
  await date.blur();
  await expect(error(page, 'Date of incident')).toHaveCount(0);

  await field(page, 'What happened?').fill('   too    short   ');
  await expect(error(page, 'What happened?')).toHaveText('At least 20 characters');

  const amount = field(page, 'Estimated loss');
  for (const bad of ['12.345', '0']) {
    await amount.fill(bad);
    await amount.blur();
    await expect(error(page, 'Estimated loss')).toHaveText(
      'Enter an amount above 0 with at most 2 decimal places',
    );
  }
  await amount.fill('1200.50');
  await expect(error(page, 'Estimated loss')).toHaveCount(0);
});

test('the motor details step checks the plate, name, address and postal code', async ({ page }) => {
  await startClaim(page, SEEDED.myMotor);
  await next(page);
  await expect(field(page, 'Vehicle registration')).toBeVisible();
  await next(page);
  for (const label of ['Vehicle registration', 'Driver name', 'Address line 1', 'Postal code']) {
    await expect(error(page, label)).toHaveText('Required');
  }

  const plate = field(page, 'Vehicle registration');
  await plate.fill('dua1234');
  await plate.blur();
  await expect(plate).toHaveValue('DUA 1234'); // written the standard way
  await plate.fill('W1234XYZ');
  await plate.blur();
  await expect(error(page, 'Vehicle registration')).toHaveText(
    'Use the plate format, e.g. WXY 1234 or SBA 1234 A',
  );

  await field(page, 'Driver name').fill('R2D2');
  await expect(error(page, 'Driver name')).toHaveText("Use letters, spaces and . ' / @ - only");

  await field(page, 'Address line 1').fill('Lot 5; Jalan *');
  await expect(error(page, 'Address line 1')).toHaveText(
    "Use letters, numbers, spaces and , . ' / # ( ) & - only",
  );

  const postal = field(page, 'Postal code');
  await postal.pressSequentially('12a3');
  await expect(postal).toHaveValue('123'); // only digits can be typed
  await postal.blur();
  await expect(error(page, 'Postal code')).toHaveText('Enter the 5-digit postal code');
  await postal.fill('50450');
  await expect(error(page, 'Postal code')).toHaveCount(0);
});

test('a Singapore policy needs a 6-digit postal code', async ({ page }) => {
  await startClaim(page, SEEDED.sgMotor);
  await next(page);
  const postal = field(page, 'Postal code');
  await postal.fill('50450');
  await postal.blur();
  await expect(error(page, 'Postal code')).toHaveText('Enter the 6-digit postal code');
  await postal.fill('018956');
  await expect(error(page, 'Postal code')).toHaveCount(0);
});

test('a draft survives a refresh', async ({ page }) => {
  await startClaim(page, SEEDED.myMotor);
  await page.reload();
  await expect(page.getByRole('status')).toContainText('Your draft was restored');
  await expect(field(page, 'What happened?')).toHaveValue(
    'A car reversed into mine in a car park.',
  );
});
