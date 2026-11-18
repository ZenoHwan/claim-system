# Claims frontend

One Angular application for both user types.

| Area | Sign in | Routes | Who |
|---|---|---|---|
| Claimant | `/login` | `/claims`, `/claims/new`, `/claims/:id` | Claimants |
| Staff | `/admin/login` | `/admin/queue`, `/admin/my-claims`, `/admin/claims/:id`, `/admin/dashboard` (managers) | Officers, managers |

Each area is a lazy-loaded route file (`src/app/claimant`, `src/app/staff`). Both use the same
shell, claim table and claim detail core from `src/app/shared`.

## Run

Needs Node 22 and the three backend services running (see the root README).

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
