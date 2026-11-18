import { CLAIMANT, sql } from './support';

/** Creates the e2e claimant once; it reuses the seeded demo password hash. */
export default async function globalSetup() {
  await sql(
    `insert into app_user (id, email, full_name, password_hash, role, market_code, team_id)
     select $1::uuid, $2, $3, password_hash, 'CLAIMANT', 'MY', null from app_user where email = 'aisha@claims.test'
     on conflict (id) do nothing`,
    [CLAIMANT.id, CLAIMANT.email, CLAIMANT.name],
  );
}
