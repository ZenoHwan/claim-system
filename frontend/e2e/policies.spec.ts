import { expect, test } from '@playwright/test';
import { CLAIMANT, SEEDED, freshPolicy, signIn } from './support';

test('a claimant sees a policy, its details, and can start a claim on it', async ({ page }) => {
  const policy = await freshPolicy(SEEDED.myMotor);
  await signIn(page, CLAIMANT.email, 'customer');

  await page.getByRole('link', { name: 'My policies' }).click();
  const row = page.getByRole('row', { name: new RegExp(policy.policyNumber) });
  await expect(row.getByRole('link', { name: 'Make a claim' })).toBeVisible();

  await row.getByRole('link', { name: policy.policyNumber }).click();
  await expect(page.getByRole('heading', { name: policy.policyNumber })).toBeVisible();
  const details = page.locator('dl');
  for (const text of ['Motor', 'Active', 'Malaysia', '1 Jan 2026 to 31 Dec 2027']) {
    await expect(details).toContainText(text);
  }
  await expect(page.getByText('No claims have been made on this policy.')).toBeVisible();

  await page.getByRole('link', { name: 'Make a claim' }).click();
  await expect(page).toHaveURL(new RegExp(`/claims/new\\?policy=${policy.id}$`));
  await expect(page.getByRole('combobox', { name: /Which policy/ })).toContainText(
    policy.policyNumber,
  );
});

test("another holder's policy is not shown", async ({ page }) => {
  await signIn(page, CLAIMANT.email, 'customer');
  await page.goto(`/claims/policies/${SEEDED.myMotor}`); // Aisha's
  await expect(page.getByRole('alert')).toBeVisible();
  await expect(page.locator('dl')).toHaveCount(0);
});
