import { randomUUID } from 'node:crypto';
import { Browser, Page, expect } from '@playwright/test';
import pg from 'pg';

/** A claimant that only these tests use, so the seeded demo accounts stay as they are. */
export const CLAIMANT = {
  id: '30000000-0000-0000-0000-0000000000e2',
  email: 'e2e.claimant@claims.test',
  name: 'E2E Claimant',
};
export const OFFICER = 'officer1.my@claims.test';
export const MANAGER = 'manager.my@claims.test';
export const PASSWORD = 'password'; // demo seed password, local only

/** Seeded policies the fresh ones are copied from. */
export const SEEDED = {
  myMotor: '40000000-0000-0000-0000-000000000001',
  myProperty: '40000000-0000-0000-0000-000000000002',
  sgMotor: '40000000-0000-0000-0000-000000000004',
};

export async function sql(text: string, values: unknown[] = []) {
  const client = new pg.Client({
    connectionString:
      process.env['E2E_DATABASE_URL'] ?? 'postgres://claims:claims@localhost:5433/claims_db',
  });
  await client.connect();
  try {
    return (await client.query(text, values)).rows;
  } finally {
    await client.end();
  }
}

/**
 * A new policy for the e2e claimant, copied from a seeded one. Each test gets its own, so the
 * one-open-claim-per-policy rule never makes tests depend on each other or on earlier runs.
 */
export async function freshPolicy(source: string) {
  const id = randomUUID();
  const policyNumber = `E2E-${id.slice(0, 13)}`;
  await sql(
    `insert into policy (id, policy_number, holder_id, line_of_business, market_code, currency, sum_insured, effective_from, effective_to)
     select $1::uuid, $2, $3::uuid, line_of_business, market_code, currency, sum_insured, effective_from, effective_to
     from policy where id = $4::uuid`,
    [id, policyNumber, CLAIMANT.id, source],
  );
  return { id, policyNumber };
}

export async function signIn(page: Page, email: string, door: 'customer' | 'staff') {
  await page.goto(door === 'staff' ? '/admin/login' : '/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(PASSWORD);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page).toHaveURL(door === 'staff' ? /\/admin\/queue$/ : /\/claims$/);
}

/** A separate browser context per person, as if each used their own browser. */
export async function personAt(browser: Browser, email: string, door: 'customer' | 'staff') {
  const page = await (await browser.newContext()).newPage();
  await signIn(page, email, door);
  return page;
}

/** dd/mm/yyyy, `daysAgo` days before today. */
export function dayMonthYear(daysAgo: number) {
  const d = new Date(Date.now() - daysAgo * 86_400_000);
  return [d.getDate(), d.getMonth() + 1, d.getFullYear()]
    .map((n) => String(n).padStart(2, '0'))
    .join('/');
}

export const PNG = {
  name: 'photo.png',
  mimeType: 'image/png',
  buffer: Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=',
    'base64',
  ),
};
export const PDF = {
  name: 'quote.pdf',
  mimeType: 'application/pdf',
  buffer: Buffer.from('%PDF-1.4\n%%EOF\n'),
};

/** The count on a nav link or the bell; hidden when there is nothing to count. */
export const badge = (page: Page, name: string) =>
  page
    .getByRole(name === 'Notifications' ? 'button' : 'link', { name })
    .locator('.mat-badge-content');
