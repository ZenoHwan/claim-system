import { Locator, Page, expect, test } from '@playwright/test';
import {
  CLAIMANT,
  OFFICER,
  PDF,
  PNG,
  SEEDED,
  badge,
  dayMonthYear,
  freshPolicy,
  personAt,
} from './support';

// Both tests notify the same claimant, so they run one after the other to keep the bell checks exact.
test.describe.configure({ mode: 'serial' });

const next = (page: Page) =>
  page.getByRole('button', { name: 'Next', exact: true }).filter({ visible: true }).click();
const field = (page: Page, label: string) => page.getByLabel(label).filter({ visible: true });
const status = (page: Page) => page.locator('app-status-chip');
const count = async (b: Locator) => ((await b.isVisible()) ? Number(await b.textContent()) : 0);
const expectCount = (b: Locator, n: number) =>
  n ? expect(b).toHaveText(String(n)) : expect(b).toBeHidden();

async function submitClaim(page: Page, policyId: string, details: (page: Page) => Promise<void>) {
  await page.goto(`/claims/new?policy=${policyId}`);
  await next(page);
  await field(page, 'Date of incident').fill(dayMonthYear(2));
  await field(page, 'What happened?').fill(
    'Damage found the morning after heavy rain in the area.',
  );
  await field(page, 'Estimated loss').fill('2500');
  await next(page);
  await details(page);
  await next(page);
  await page.locator('input[type=file]').setInputFiles(PNG);
  await expect(page.getByText(PNG.name)).toBeVisible();
  await next(page);
  await page.getByRole('button', { name: 'Submit claim' }).click();
  await expect(page).toHaveURL(/\/claims\/[0-9a-f-]{36}$/);
  await expect(status(page)).toHaveText('Submitted');
  return (await page.getByRole('heading', { level: 1 }).textContent())!.trim();
}

async function pickUpFromQueue(officer: Page, claimNumber: string) {
  await officer.goto('/admin/queue');
  await officer
    .getByRole('row', { name: new RegExp(claimNumber) })
    .getByRole('button', { name: 'Pick up' })
    .click();
  await expect(officer.getByRole('heading', { name: claimNumber })).toBeVisible();
  await expect(status(officer)).toHaveText('Under review');
}

async function assess(officer: Page, amount: string) {
  await officer.getByLabel('Findings').fill('Damage matches the photos and the quote.');
  await officer.getByLabel(/Recommended amount/).fill(amount);
  await officer.getByRole('button', { name: 'Save assessment' }).click();
  await expect(status(officer)).toHaveText('Assessed');
}

test('a motor claim goes from submission, through a question, to settlement', async ({
  browser,
}) => {
  test.slow();
  const policy = await freshPolicy(SEEDED.myMotor);
  const claimant = await personAt(browser, CLAIMANT.email, 'customer');

  // Start with a clear bell so the new notification is the one we count.
  await claimant.getByRole('button', { name: 'Notifications' }).click();
  await claimant.keyboard.press('Escape');
  await expect(badge(claimant, 'Notifications')).toBeHidden();

  const claimNumber = await submitClaim(claimant, policy.id, async (page) => {
    await field(page, 'Vehicle registration').fill('wxy1234');
    await field(page, 'Driver name').fill('Aminah binti Ali');
    await field(page, 'Address line 1').fill('Jalan Ampang');
    await field(page, 'Postal code').fill('50450');
  });
  await expect(claimant.locator('app-claim-core')).toContainText('WXY 1234');

  // One claim at a time per policy.
  await claimant.getByRole('link', { name: 'My policies' }).click();
  const row = claimant.getByRole('row', { name: new RegExp(policy.policyNumber) });
  await expect(row.getByRole('link', { name: claimNumber })).toBeVisible();
  await expect(row.getByRole('link', { name: 'Make a claim' })).toHaveCount(0);
  const waitingBefore = await count(badge(claimant, 'Follow-ups'));

  // The officer picks it up and asks a question.
  const officer = await personAt(browser, OFFICER, 'staff');
  const repliesBefore = await count(badge(officer, 'Follow-ups'));
  await pickUpFromQueue(officer, claimNumber);
  await officer.getByLabel('Question for the claimant').fill('Please send the repair quote.');
  await officer.getByRole('button', { name: 'Send request' }).click();
  await expect(status(officer)).toHaveText('Information requested');

  // The claimant hears about it without a refresh.
  await expectCount(badge(claimant, 'Follow-ups'), waitingBefore + 1);
  await expect(badge(claimant, 'Notifications')).toBeVisible();
  await claimant.getByRole('button', { name: 'Notifications' }).click();
  await expect(claimant.getByRole('menuitem').first()).toBeVisible();
  await claimant.keyboard.press('Escape');
  await expect(badge(claimant, 'Notifications')).toBeHidden(); // opening the bell clears it

  // The claimant replies from Follow-ups with a file, after removing one attached by mistake.
  await claimant.getByRole('link', { name: 'Follow-ups' }).click();
  const card = claimant.locator('mat-card').filter({ hasText: claimNumber });
  await expect(card).toContainText('Please send the repair quote.');
  await card.getByRole('link', { name: 'Reply' }).click();
  const reply = claimant.locator('mat-card.reply');
  await reply.getByLabel('Your reply').fill('Quote from the workshop attached.');
  await reply.locator('input[type=file]').setInputFiles([PDF, PNG]);
  await reply
    .locator('.row')
    .filter({ hasText: PNG.name })
    .getByRole('button', { name: 'Remove' })
    .click();
  await expect(reply.getByText(PNG.name)).toHaveCount(0);
  await reply.getByRole('button', { name: 'Send reply' }).click();
  await expect(status(claimant)).toHaveText('Under review');
  await expectCount(badge(claimant, 'Follow-ups'), waitingBefore); // the reply clears it

  // Both sides see the question and reply trail, with the reply's file.
  const trail = claimant.locator('mat-card').filter({ hasText: 'Questions and replies (1)' });
  await expect(trail).toContainText('Please send the repair quote.');
  await expect(trail).toContainText('Quote from the workshop attached.');
  await expect(trail).toContainText(PDF.name);
  await expect(trail).not.toContainText(PNG.name);

  // The officer's Follow-ups tab counts the reply to review.
  await expect(status(officer)).toHaveText('Under review'); // the workbench updates live
  await expectCount(badge(officer, 'Follow-ups'), repliesBefore + 1);
  await officer.getByRole('link', { name: 'Follow-ups' }).click();
  const replyCard = officer.locator('mat-card').filter({ hasText: claimNumber });
  await expect(replyCard).toContainText('Quote from the workshop attached.');
  await replyCard.getByRole('link', { name: 'Open claim' }).click();
  await expect(
    officer.locator('mat-card').filter({ hasText: 'Questions and replies (1)' }),
  ).toContainText(PDF.name);

  // Assess, approve and pay.
  await assess(officer, '2400');
  await expectCount(badge(officer, 'Follow-ups'), repliesBefore); // assessing clears it
  await officer.getByLabel(/Approved amount/).fill('2400');
  await officer.getByRole('button', { name: 'Approve' }).click();
  await expect(status(officer)).toHaveText('Approved');
  await officer.getByRole('combobox', { name: 'Method' }).click();
  await officer.getByRole('option', { name: 'Bank transfer' }).click();
  await officer.getByLabel('Payment reference').fill(`E2E-${claimNumber}`);
  await officer.getByRole('button', { name: 'Record payment and settle' }).click();
  await expect(status(officer)).toHaveText('Settled');

  // The claimant's page follows along, and the policy is free for a new claim.
  await expect(status(claimant)).toHaveText('Settled');
  await expect(claimant.locator('app-claim-core')).toContainText(
    /Approved amount\s*MYR\s?2,400\.00/,
  );
  await claimant.getByRole('link', { name: 'My policies' }).click();
  await expect(row.getByRole('link', { name: 'Make a claim' })).toBeVisible();
});

test('a property claim can be rejected with a reason the claimant sees', async ({ browser }) => {
  test.slow();
  const policy = await freshPolicy(SEEDED.myProperty);
  const claimant = await personAt(browser, CLAIMANT.email, 'customer');
  const claimNumber = await submitClaim(claimant, policy.id, async (page) => {
    await field(page, 'Address line 1').fill('12 Jalan Bukit Bintang');
    await field(page, 'Address line 2').fill('Taman   Maju');
    await field(page, 'Postal code').fill('55100');
    await page.getByRole('combobox', { name: 'Cause of loss' }).click();
    await page.getByRole('option', { name: 'WATER' }).click();
  });
  await expect(claimant.locator('app-claim-core')).toContainText(
    '12 Jalan Bukit Bintang, Taman Maju, 55100',
  );

  const officer = await personAt(browser, OFFICER, 'staff');
  await pickUpFromQueue(officer, claimNumber);
  await assess(officer, '0.01');
  await officer
    .getByLabel('Reason (shown to the claimant)')
    .fill('The leak was gradual, which the policy excludes.');
  await officer.getByRole('button', { name: 'Reject' }).click();
  await expect(status(officer)).toHaveText('Rejected');

  await expect(status(claimant)).toHaveText('Rejected');
  const decision = claimant.locator('mat-card').filter({ hasText: 'Decision' });
  await expect(decision).toContainText('The leak was gradual, which the policy excludes.');
});
