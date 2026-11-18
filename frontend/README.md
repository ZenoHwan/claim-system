# Claims frontend

One Angular application for both user types.

| Area | Sign in | Routes | Who |
|---|---|---|---|
| Claimant | `/login` | `/claims`, `/claims/new`, `/claims/:id`, `/claims/policies`, `/claims/policies/:id`, `/claims/follow-ups` | Claimants |
| Staff | `/admin/login` | `/admin/queue`, `/admin/my-claims`, `/admin/follow-ups`, `/admin/claims/:id`, `/admin/dashboard` (managers) | Officers, managers |

Each area is a lazy-loaded route file (`src/app/claimant`, `src/app/staff`). Both use the same
shell, claim table and claim detail core from `src/app/shared`.

## Run

In Docker, `docker compose up -d --build` (root of the repository) builds the app and serves it from
nginx on http://localhost:4200. nginx routes `/api` by path to the three services (looking each one up
again through Docker's DNS, so a restarted service is found), caches the hashed
JS and CSS bundles for a year and revalidates `index.html` on every load (`nginx.conf`).

For the dev server you need Node 22 and the three backend services running (see the root README).

```bash
npm install
npm start
```

Open http://localhost:4200. The dev server proxies `/api/reports` to reporting-service (8081),
`/api/notifications` to notification-service (8082) and the rest of `/api` to claims-service (8080).

The session is kept in `sessionStorage`, so two browser tabs can be signed in as different users.

## API contract

The backend owns the contract. Each service publishes OpenAPI at `/v3/api-docs`; the documents are
committed under `openapi/` and TypeScript types are generated from them into `src/app/api/generated`.
`src/app/api/types.ts` derives the application's types from the generated ones, so a renamed or
removed field fails `ng build`.

```bash
npm run api:fetch      # with the three services running: refresh openapi/*.json
npm run api:generate   # regenerate src/app/api/generated/*.ts
```

## Form validation

`src/app/shared/validation.ts` mirrors the claims-service rules so each field shows its own message
before submission: real dd/mm/yyyy dates within cover (`DayMonthYearAdapter` parses day first and
refuses impossible dates), plate format with clean-up on blur, names, address lines, and a postal
code of 5 digits for Malaysia or 6 for Singapore. The server checks the same rules again.

## End-to-end tests

Playwright tests in `e2e/` run against the Docker stack, with a claimant and an officer in separate
browsers. See the root README for what they cover.

```bash
npx playwright install chromium   # once
npm run e2e
```

`E2E_BASE_URL` (default `http://localhost:4200`) and `E2E_DATABASE_URL` (default
`postgres://claims:claims@localhost:5433/claims_db`) point the tests elsewhere.
