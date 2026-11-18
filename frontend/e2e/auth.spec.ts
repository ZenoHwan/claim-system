import { expect, test } from '@playwright/test';
import { CLAIMANT, MANAGER, OFFICER, PASSWORD, signIn } from './support';

test('a claimant signs in at the customer door and signs out', async ({ page }) => {
  await signIn(page, CLAIMANT.email, 'customer');
  await expect(page.getByRole('heading', { name: 'My claims' })).toBeVisible();
  await page.getByRole('button', { name: CLAIMANT.name }).click();
  await page.getByRole('menuitem', { name: 'Sign out' }).click();
  await expect(page).toHaveURL(/\/login$/);
});

test('each door turns away the other kind of account', async ({ page }) => {
  for (const [door, email] of [
    ['/login', OFFICER],
    ['/admin/login', CLAIMANT.email],
  ]) {
    await page.goto(door);
    await page.getByLabel('Email').fill(email);
    await page.getByLabel('Password', { exact: true }).fill(PASSWORD);
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page.getByRole('alert')).toHaveText(
      'Email or password is incorrect for this sign-in page.',
    );
    await expect(page).toHaveURL(new RegExp(`${door}$`));
  }
});

test('a wrong password is rejected', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Email').fill(CLAIMANT.email);
  await page.getByLabel('Password', { exact: true }).fill('not-the-password');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('alert')).toBeVisible();
});

test('the eye button shows and hides the password', async ({ page }) => {
  await page.goto('/login');
  const password = page.getByLabel('Password', { exact: true });
  await password.fill('secret');
  await expect(password).toHaveAttribute('type', 'password');
  await page.getByRole('button', { name: 'Show password' }).click();
  await expect(password).toHaveAttribute('type', 'text');
  await expect(page.getByRole('button', { name: 'Hide password' })).toHaveAttribute(
    'aria-pressed',
    'true',
  );
  await page.getByRole('button', { name: 'Hide password' }).click();
  await expect(password).toHaveAttribute('type', 'password');
});

test('signed-out visitors are sent to the right door', async ({ page }) => {
  await page.goto('/claims/policies');
  await expect(page).toHaveURL(/\/login$/);
  await page.goto('/admin/queue');
  await expect(page).toHaveURL(/\/admin\/login$/);
});

test('a claimant cannot open the staff area', async ({ page }) => {
  await signIn(page, CLAIMANT.email, 'customer');
  await page.goto('/admin/queue');
  await expect(page).toHaveURL(/\/admin\/login$/);
});

test('only a manager sees the dashboard', async ({ page }) => {
  await signIn(page, OFFICER, 'staff');
  await expect(page.getByRole('link', { name: 'Dashboard' })).toHaveCount(0);
  await page.goto('/admin/dashboard');
  await expect(page).toHaveURL(/\/admin\/queue$/);

  await page.evaluate(() => sessionStorage.clear());
  await signIn(page, MANAGER, 'staff');
  await page.getByRole('link', { name: 'Dashboard' }).click();
  await expect(page.getByRole('heading', { name: /Dashboard · MY/ })).toBeVisible();
  await expect(page.getByText('Outstanding exposure')).toBeVisible();
  await expect(page.getByText('Workload by officer')).toBeVisible();
});
