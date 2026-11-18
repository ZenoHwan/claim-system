# AI Working Journal

This journal records how I worked with the AI (Claude Code) on this assessment,
with one entry per decision.

Legend: **Accepted** means I adopted the AI's suggestion. **Challenged** means I
pushed back and the AI revised its proposal. **Overrode** means the AI
recommended one option and I chose another.

---

## 18 Nov 2026 — Planning session (before any code)

### 1. Workflow: OpenSpec by default

**What I did:** I drove the AI with OpenSpec from the start. It is my default workflow, so the AI had no input on this decision.
**Why:** The panel assesses decisions more than code volume. OpenSpec commits the proposal, design, specifications and tasks to the repository, so the reasoning remains reviewable rather than being lost in chat history.

### 2. Requirements as the starting input

**What I did:** I gave the AI the assessment requirements as the source of truth for the build, so that every design question and specification traced back to the brief rather than to the AI's own assumptions.
**Why:** Grounding the AI in the actual requirements keeps the scope disciplined. Every proposal could be checked against the brief, and anything outside it had to be justified or deferred to "with more time".

### 3. Backend stack

**Raised:** The AI asked me to choose between .NET and Spring Boot, noting that it had to be the one I could defend line by line.
**AI said:** It had no preference, and noted that neither the JDK nor the .NET SDK was installed on my machine yet.
**What I did:** I chose Spring Boot.
**Why:** It is the stack I can defend line by line, and it offers mature, well-established support for everything this design requires: Kafka, Flyway, Spring Security for JWT, and Testcontainers for genuine integration tests.

### 4. Single service or multiple services

**Raised:** Whether the backend should be one service or several.
**AI said:** It recommended a single service with the Kafka consumers inside it. With only five hours available, fewer moving parts would mean less risk during the demo, and "why three services for one team?" would be a difficult question to answer.
**What I did:** Overrode. I chose three services: claims, reporting and notification.
**Why:** The three parts have different responsibilities and different tolerance for failure. Claims is transactional and must never be blocked, reporting performs read-heavy aggregation, and notifications fan out to users. Separating them ensures that a reporting outage or a slow dashboard query cannot prevent a customer from filing a claim. It also demonstrates the event-driven integration (outbox, Kafka, read models) that a real insurance platform requires. I accepted the additional complexity and contained it with a shared event-contract module, a single Compose file, and one database server hosting a separate database per service.

### 5. Stress-testing the design before writing any specification

**Raised:** I instructed the AI to postpone `openspec init` and run a structured questioning session first, in case the requirements changed. I also required ER, C4, sequence, state machine and user flow / wireframe diagrams.
**AI said:** It worked through 34 questions over several rounds, each with a recommended answer.
**What I did:** This step was my decision. I worked through every question.
**Why:** At this stage, changing a requirement costs one answer; after the code exists, it costs a rewrite. Settling the design first kept the specification stable throughout the build, and the diagrams give the panel a map of the system before they read any code.

### 6. Round 1 decisions (scope, roles, authentication, policy, tables, markets, assignment, reserve, approval, diagram format)

**AI said:** It made ten recommendations, including JWT rather than a plain role header, a mandatory link from each claim to a policy, separate motor and property detail tables rather than a single JSON column, an initial reserve created automatically at submission so that unassessed claims do not show zero exposure, and manager sign-off remaining a manual process.
**What I did:** I accepted all ten.
**Why:** They reflect how a real claims system operates. The reserve is the most important: an insurer must carry an estimated liability from the moment a claim exists, so an unassessed claim showing zero would understate exposure on the manager dashboard precisely when it matters. JWT allows every service to trust the caller's identity and role without querying another service. Typed motor and property tables keep the fields validated by the database rather than hidden in a JSON column.

### 7. Login screens and testing approach

**Raised:** The Round 2 proposal used a single shared login and wrote tests after the code.
**AI said:** It recommended one login, with the user's role determining the landing page, and a test suite of state machine unit tests plus two integration tests.
**What I did:** Challenged both. I required `/login` for claimants and `/admin/login` for staff, and strict test-driven development (red-green-refactor) following DRY and YAGNI.
**AI then:** It agreed, and added that the server, not only the UI, must reject a user whose role does not match the login page. It limited TDD to code containing logic (excluding page objects, configuration and Angular templates), with one commit per green cycle and no deliberately failing commits.
**Why:** Insurers separate the customer portal from the staff back office, so staff access can later receive stricter controls (such as SSO or network restrictions) without affecting the customer journey. The server must enforce the separation, because a check that exists only in the UI provides no protection. I chose TDD because the state machine and monetary rules are where defects are most costly, and writing the test first requires each rule to be stated before it is implemented.

### 8. `claim_document`

**AI said:** Following YAGNI, tables for features outside the build should have no migration, and that included `claim_document`.
**What I did:** Overrode. Document upload is part of the build.
**AI then:** It stored files on a local volume with their metadata in the table, validated PDF, JPEG and PNG files up to 10 MB on the server, restricted downloads to the claim owner and staff, and added no new Kafka event.
**Why:** An officer cannot assess a claim without supporting evidence, and an information request is meaningless if the claimant cannot upload a response. YAGNI applies to features nobody needs; documents are needed from day one.

### 9. Payment

**Raised:** I had the AI set out what a payment module would realistically involve.
**AI said:** It presented three options: recording a reference on the decision, a lightweight `payment` table, or a fourth service with a Kafka round trip to a simulated bank. It recommended the lightweight table, noting that a fourth service would not be realistic within five hours.
**What I did:** I accepted the lightweight table.
**Why:** It completes the lifecycle (approved, paid, settled) with an auditable record, and a unique constraint on `claim_id` makes a duplicate payment impossible at the database level. A simulated bank integration would spend the available time on infrastructure that demonstrates nothing about claims handling.

### 10. Manager permissions

**Raised:** The diagrams referred only to "officer" throughout.
**What I did:** I specified that a manager must be able to perform every officer action, in addition to accessing the dashboard.
**AI then:** It confirmed this was the intent and made it explicit in the documentation and the specification.
**Why:** A manager covers for an absent officer. If a manager can see a stalled claim but cannot act on it, that is an operational gap, and documentation that mentions only "officer" is exactly the kind of ambiguity that later becomes a defect.

### 11. Kafka outage handling

**Raised:** What protects a claim change when the message broker is unavailable.
**AI said:** Rollback is not required. The `outbox_event` table holds the events and a relay retries until delivery succeeds, so claims continue to be processed and the dashboards and notifications catch up afterwards. It also identified two gaps: dead-lettered messages go to a Kafka topic that nothing monitors, and published outbox rows are never purged.
**What I did:** I accepted this approach and recorded both gaps under "with more time".
**Why:** A Kafka outage must never prevent a customer from filing a claim. The outbox writes each event in the same transaction as the claim, so no event is lost and none is published for a change that was rolled back. The two gaps concern monitoring and housekeeping rather than correctness, so documenting them was the appropriate decision within the time available.

### 12. Commit messages

**What I did:** I instructed the AI to follow the Conventional Commits specification.
**AI then:** It aligned the convention with the TDD workflow: `feat(scope)` for a green cycle together with its test, `refactor(scope)` as a separate commit, and `chore` or `build` for scaffolding.

---

## AI decisions on my review list

- One assessment per claim, enforced by a unique constraint.
- Approval is rejected if the amount exceeds the policy's sum insured.
- JWTs are signed with a key shared across the three services; the AI flagged this as a deliberate shortcut.
- The Mermaid diagrams in `docs/` have not yet been rendered.
- All specification files under `openspec/changes/claims-platform-mvp/` should be reviewed in full before the build.

---

## 18 Nov 2026 — Build session

With the specification settled, I delegated the task list to the AI. The entries
below record the decisions it made during the build, so that I can take
ownership of each one or change it.

### 13. Java and Spring Boot versions

**AI did:** My machine had JDK 17, 25 and 27 installed through Homebrew, but not JDK 21. The AI installed JDK 21, as agreed in the design, rather than silently using version 25. For Spring Boot, it queried start.spring.io for the current version (4.1.2) and the correct starter names instead of relying on memory. Because Boot 4 relocated many packages and adopted Jackson 3, it inspected the JAR contents whenever an import failed.
**What I did:** I accepted these decisions.
**Why:** Keeping to the agreed LTS release (21), rather than whichever version happened to be installed, keeps the build reproducible. Verifying versions against the source rather than memory is the correct practice for a major release such as Boot 4.

### 14. Parallel development

**AI did:** Once the event contract (`claim-events`) was finalised, the AI started separate agents for reporting-service, notification-service and later the Angular application, while building claims-service itself. Each agent received a written brief containing the fixed conventions (ports, JWT claims, topic and endpoints) and was instructed not to commit. The main session re-ran each agent's tests and reviewed the code before committing.
**What I did:** I accepted this approach. It was safe because the event contract was frozen first, so the services could interact only through it. Only the main session committed, and only after re-running each agent's tests.
**Follow-up:** Review reporting-service and notification-service line by line, since I did not observe them being written.

### 15. How TDD was applied in practice

I required strict red-green-refactor. The table below shows what actually happened.

| Slice | Failing test observed first? |
|---|---|
| Event contract | Yes, but only as a compilation failure (the classes did not yet exist) |
| Sign-in and roles | Yes: the application started, 10 of 12 tests failed on behaviour, and all then passed |
| State machine | Compilation failure, then passing |
| Claim lifecycle (submit, pick up, assess, decide) | Yes: 30 HTTP tests failed with 404 responses before the code existed |
| Information requests, documents, settlement | Yes: 32 tests failed, then passed group by group |
| Outbox relay | Compilation failure, then passing (including the broker-outage test) |
| reporting-service (agent) | One compilation failure for the whole group, then passing on the first run |
| notification-service (agent) | Tests were written first, but the initial run failed for a configuration reason, so no genuine behavioural failure was observed |

In summary, tests were always written before the code, but commits were made per
feature slice (one failing, one passing) rather than per individual cycle, and
there were very few separate refactoring commits. If the panel asks whether this
was strict TDD, the accurate answer is "test-first, at a coarse granularity".

### 16. Changes the AI made to the design during the build

- **Additional columns on `claim`:** `current_reserve`, `decided_at` and `settled_at`. The ER diagram originally treated the latest reserve row as the current reserve. The AI retained the reserve history table but also copied the current figure onto the claim, so that reads and events do not require a subquery. The ER diagram was updated accordingly.
- `**CHAR(2)` and `CHAR(3)` were changed to `VARCHAR**` for market and currency, because Hibernate schema validation rejected `CHAR`.
- **PostgreSQL runs on host port 5433**, because port 5432 was already in use by a local PostgreSQL installation.
- **The seeded policy period extends to the end of 2027**, so that the tests do not begin failing in January.
- **reporting-service and notification-service use plain JDBC rather than JPA**, because their writes consist of upserts and aggregates.
- **claims-service places information requests, documents and payments in the same Java package as claims** rather than in separate packages, to avoid a package dependency cycle.

### 17. Behaviour the AI introduced beyond my specification

- Requests for another user's claim, policy or document return **404 rather than 403**, so that identifiers cannot be probed.
- Sign-in returns the **same response** for an unknown account, an incorrect password and the wrong login page.
- **The reserve and assessment are hidden from the claimant** as internal figures, as is the payment reference.
- Approval and rejection check the **claim status first**, and the amount second.
- The outbox relay **stops at the first failed send**, so that later events cannot overtake an unpublished one.
- The type of an uploaded file is determined from **its first bytes** rather than the declared content type, and downloads are always served as attachments.
- `/v3/api-docs` is **public** on all three services, so that the frontend can generate its types.
- Staff actions return **200 with the full updated claim**, rather than 201 or 204.

**Why (for all of 16 and 17):** I retained all of them. The changes in 16 are pragmatic, and the ER diagram was updated to match. The behaviours in 17 are security and integrity defaults I would want regardless: returning 404 rather than 403 and using identical sign-in errors prevent account and identifier probing, hiding the reserve keeps internal figures internal, stopping at the first failure preserves event ordering, and inspecting the file's actual bytes prevents disguised uploads.

---

## 18 Nov 2026 — Hands-on testing

In this phase I tested the application myself, and it is where I challenged the
AI's work most often.

### 18. Demo password

**What I did:** I set all seeded accounts to share one simple password.
**AI then:** It updated the seed data, tests, README and running database, and made re-seeding refresh the password hash. It also added a note to the README that the seed is demonstration data only.

### 19. Sign-in rejected at the claimant login

**Raised:** An officer account was rejected at the claimant login page.
**AI said:** This was the intended behaviour, because the account was used at the wrong login page. The server deliberately returns the same error for an incorrect password, an unknown email address and the wrong login page, so the form cannot be used to discover which accounts exist. It offered to add a more helpful hint.
**What I did:** I accepted the explanation and kept the generic error, because resistance to account enumeration is worth more than the convenience of a hint.

### 20. Calendar on the date field

**What I did:** I had a calendar picker added to the date field.
**AI first:** It added a button that opens the browser's built-in date picker, avoiding a new library. This did not work correctly in my browser.
**AI then:** It switched to the Angular Material datepicker, with day, month and year selection and future dates disabled.
**Lesson:** My hands-on testing showed that the minimal native option was not sufficient here. The AI had verified only that the button invoked the picker, not that a calendar appeared, and it stated this limitation.

### 21. Whether the claimed amount is necessary

**Raised:** I challenged the assumption that a claimant must state an amount, which is not how first notification works in insurance practice.
**AI said:** No. At first notification of loss, most claimants do not yet have a quote, and the figure that matters is the insurer's reserve. However, the design used the claimed amount as the initial reserve, so a blank value would require a substitute or exposure would again be understated. It recommended an optional "estimated loss" field, with a default reserve applied when it is left blank.
**What I did:** I challenged the original design and accepted the revised approach.
**AI then revised its own proposal:** It initially suggested a fixed default figure per line of business, then changed to a **percentage of the sum insured** (10% for motor, 5% for property), because a fixed figure is meaningless across currencies.
**Why:** This reflects how first notification of loss works in practice: customers report a claim before they have a quote. A percentage of the sum insured scales across currencies and policy sizes, whereas a fixed figure does not. The 10% and 5% rates are starting assumptions; in production they would be derived from historical claims data.

### 22. Form progress lost on refresh

**What I did:** I reported that refreshing the New Claim page returned me to step 1.
**AI then:** It saved the draft and the current step in sessionStorage, restored both on page load, and cleared them on submission or cancellation. Attached files cannot be persisted this way and must be re-added; the page informs the user of this.

### 23. One open claim per policy

**What I did:** I specified that a policy may have only one claim in progress at a time.
**AI then:** It added a service-level check returning a clear 409 response, together with a partial unique index so that two simultaneous submissions cannot both succeed. The policy dropdown disables any policy that already has a claim in progress.
**AI's note:** In practice, a policy can have several open claims for separate incidents. The AI implemented my rule and documented the alternative in the design (D10a).
**Why:** The stricter rule prevents duplicate claims for the same incident and keeps the MVP straightforward to reason about. It is a deliberate and reversible choice: supporting several open claims later requires removing one partial index and the service check, and the alternative is already documented in D10a.

### 24. Running all services in Docker

**Raised:** My Docker container list showed only PostgreSQL and Kafka.
**AI said:** This was by design. Only the infrastructure was containerised, while the services ran with `java -jar` on the host so that a code change required only a quick restart rather than an image rebuild. The drawback was five commands across four terminals. It offered to containerise everything.
**What I did:** I required the entire system to run under Docker Compose.
**AI then:** It created a single Dockerfile for the three services (a build argument selects the module), an nginx image for the frontend that also routes `/api` requests to the services, and Compose entries for every component. Kafka required two listeners, one for containers and one for the host, because a Kafka client reconnects to whichever address the broker advertises. The services run as a non-root user. The AI verified the setup by taking a claim through its full lifecycle in the containers.
**Follow-up:** I should be able to explain the two Kafka listeners myself.

### 25. Database per service, and removing the unused database

**Raised:** Connecting to PostgreSQL from a database client, I reviewed why the data is split across several databases, whether foreign keys are used, and whether the extra `claims` database was still needed.
**AI said:** Each service owns its own database (`claims_db`, `reporting_db`, `notification_db`), as decided in design D7, so that no service can couple to another's tables; data is shared only through Kafka events. Foreign keys are used throughout `claims_db` but cannot cross databases. The `claims` database was never used: PostgreSQL creates it automatically because the user is named `claims`.
**What I did:** I had the unused database removed.
**AI then:** It dropped the empty `claims` database and set `POSTGRES_DB: claims_db` in Docker Compose, so a fresh volume no longer recreates it, and removed the now-duplicate `CREATE DATABASE claims_db` from the init script.
**Why:** A clean environment is easier to reason about and to demonstrate. Understanding the isolation also prepared me to defend the trade-off: independent schemas and contained failures, at the cost of no cross-service joins and eventually consistent read models.

### 26. A "My policies" page for claimants

**Raised:** Apart from making a new claim, a customer had no way to see their policies.
**AI said:** Only inside the New Claim dropdown. The existing `GET /api/policies` endpoint already returned everything a policy page needs, so the page would be a frontend-only change.
**What I did:** I had the page built.
**AI then:** It added a My Policies page listing each policy's number, type, cover period, sum insured and any claim in progress, with a link to that claim. Policies without an open claim offer a "Make a claim" button that opens New Claim with the policy preselected. Claimants also gained a navigation bar (My claims, My policies). It verified the page against the running application.
**Why:** Customers expect to see what they are covered for before they report a loss. Reusing the existing endpoint delivered this with no backend or schema change.

### 27. Clearing the notification badge when the bell is opened

**What I did:** I specified that opening the notification bell should clear the unread count, for both customers and staff.
**AI then:** It added `POST /api/notifications/read-all`, test-first (the new test failed with a 404 before the endpoint existed, and all 25 notification-service tests then passed). The badge clears as soon as the bell opens and the server records the notifications as read, so the count stays cleared after a reload and on other devices. New items remain highlighted while the menu is open, so the user can still see what has just arrived.
**Why:** This matches how users expect notification inboxes to behave. Persisting the change on the server, rather than only hiding the badge in the browser, keeps the count consistent across sessions.

### 28. Structured addresses with a postal code

**What I did:** I required the motor accident location and the property address to be captured as structured address fields with a postal code, instead of a single free-text field. When the AI asked whether to use three address lines or street, city and state, I chose three address lines, the common layout on Malaysian forms, which also suits Singapore.
**AI then:** It introduced one shared `Address` (address lines 1 to 3 and postal code) for both claim types, used by the request, the database columns and the response. A Flyway migration (V7) renamed the existing column to `address_line1` and added the new columns, so existing claims keep their address as line 1. Line 1 and a 5 or 6 digit postal code are required and validated on both the server and the form. The tests were updated first and failed before the change, and all 94 claims-service tests then passed. The ER diagram and the generated frontend API types were updated.
**Why:** Structured addresses with postal codes are what insurers need for assessing a location, detecting fraud and reporting by area. Migrating in place rather than recreating the tables preserved existing claims.

### 29. Validating every field on the claim form

**What I did:** After testing the New Claim form with deliberately poor input, I required proper validation and clean-up on every field, including the date, the vehicle registration and the postal code.
**AI then:** It added the rules to the server first, test-first (three new tests failed before the change, and all 97 claims-service tests then passed), and mirrored them in the form so each field shows a specific message:
- Incident date: must be a real dd/mm/yyyy date, within the policy's cover and not in the future. Letters cannot be typed into the field.
- Description: at least 20 characters. Estimated loss: above zero with at most two decimal places.
- Vehicle registration: Malaysian and Singaporean plate formats, stored in a standard form ("dua1234" becomes "DUA 1234").
- Driver name and address lines: letters, digits and the punctuation names and addresses genuinely use.
- Postal code: five digits for a Malaysian policy and six for a Singaporean one, checked against the policy's market on the server.
- Free text is trimmed and repeated spaces are collapsed before it is validated and stored.
**AI found while testing:** The date picker read typed dates month-first, so a valid date such as 16/11/2026 was rejected and 01/12/2026 was read as 12 January. It replaced the parser with a strict day/month/year one that also refuses impossible dates such as 31/02/2026.
**Why:** Validation at the server protects data quality whatever the client sends; mirroring it in the form tells the customer exactly what to fix before they submit. Normalising plates and text keeps stored data consistent for search and fraud checks. Vanity plates are a known gap, recorded in the code.

### 30. Failed reply with an attachment, and removing attached files

**Raised:** Replying to an information request with a photo attached showed "Something went wrong". I also required attached files to be removable before sending.
**AI said:** The photo was a valid, small PNG and the upload route worked, so the failure lined up with the moment it restarted claims-service for a deployment; the generic message hid that. It also found that a reply failing part-way would upload already-sent files again on retry.
**AI then:** Each attached file now has a Remove button. Files leave the list as soon as they upload, so a retry never duplicates them. When the service cannot be reached, the message now says so and asks the user to try again, instead of "Something went wrong".
**Why:** An error message should tell the user what to do next. Preventing duplicate uploads keeps the claim's documents clean for the officer reviewing them.

### 31. Policy details and a Follow-ups tab for customers and staff

**What I did:** I specified a policy details page for customers, and a Follow-ups tab in both the customer portal and the staff system, with a count, so that staff can see at a glance which follow-ups they have sent and which need their attention.
**AI then:** It built both, test-first (five new tests failed before the change, and all 102 claims-service tests then passed):
- **Policy details:** a new `GET /api/policies/{id}` returns the policy with every claim made on it. Customers open it from My Policies to see the type, status, cover period, sum insured, market and claim history, with "Make a claim" offered when no claim is in progress. Another customer's policy returns 404.
- **Follow-ups:** a customer's tab lists the questions waiting for their reply. A staff member's tab lists replies they have not yet acted on, and the questions they sent that are still waiting for the claimant. The number on the tab counts what that person must act on: unanswered questions for a customer, replies to review for staff. A follow-up clears itself when the claim moves on (the customer replies, or the officer assesses or asks again), so there is no separate status to maintain. The count refreshes on navigation and when a live notification arrives.
**Why:** Information requests are where claims stall. Making them visible, with a count, means neither side has to remember to check each claim, which shortens the time a claim waits. Deriving the follow-up state from the claim and its latest question keeps it accurate without adding data that could drift.

### 32. End-to-end tests for the frontend

**What I did:** I required a comprehensive Playwright suite, so the frontend is covered by automated tests as well as the services.
**AI then:** It wrote 15 browser tests that run against the full Docker stack:
- Sign-in through both doors, each door turning away the other kind of account, the password toggle, and the route guards for signed-out visitors, claimants and officers.
- My Policies and the policy page, including "Make a claim" preselecting the policy and another customer's policy staying hidden.
- Every rule on the claim form, for Malaysian and Singaporean policies, and the draft surviving a refresh.
- A motor claim taken by two people in separate browsers from submission, through an officer's question and the customer's reply with an attachment, to approval and payment, checking the live badges, the bell and the question-and-reply trail on both sides. A property claim is taken through assessment to rejection, with the reason shown to the customer.
The tests sign in as a dedicated claimant account and each gets a fresh policy written directly to the database, so tests run in parallel and do not depend on earlier runs or on the demo data. The suite passed three runs in a row with no flaky failures.
**AI found while testing:** After a customer replied, their Follow-ups count stayed at one until they moved to another page, and the officer's count behaved the same way after assessing. The count refreshed only on navigation or on a live notification, and nobody is notified about their own action. Both pages now refresh the count after the action succeeds.
**Why:** The integration tests prove the API; these prove that a customer and an officer can actually complete the journey in the browser. Running two users side by side is what exposed the stale count, which a single-user check by hand had missed.

### 33. A Redis cache sized for many users

**What I did:** Planning for a large user base, I directed that Redis be added to cache the platform's queries.
**AI then:**
- Identified the two reads that grow with usage and cached only those in Redis: the Follow-ups list, which every user fetches on every page change at a cost of one query per claim, and the dashboard reports, which aggregate over every claim. Claim details, lists and the queue were left uncached because they change on almost every action.
- Kept the cache correct by eviction rather than expiry: every claim change already passes through one method, which now clears the claimant's and officer's Follow-ups after the change commits; reporting clears a market's reports when it applies an event for it. Entries are typed JSON with a 60-second backstop expiry. If Redis is unavailable the database answers within the 500 ms timeout.
- Wrote the tests first: four new tests prove that entries are served from Redis, cleared on change, and that both services keep working with the Redis container paused. With caching disabled, the cache tests failed for the expected reason. All 166 service tests and the 15 Playwright tests passed.
**AI found while testing:** After the services were rebuilt, every sign-in returned 502. nginx resolved each service's address once at start-up and kept sending traffic to the old container. It now resolves the services through Docker's DNS on each request, so a restarted or rescaled service is found without restarting nginx. This was very likely also the cause of the earlier failed reply.
**Why:** Caching only the hot, expensive reads, and clearing them on the one write path every change already uses, gives the speed benefit at scale without the main risk of caching in a claims system: showing someone a stale status or payment. Planning Redis pub/sub for live push across several notification instances is the next step for a large user base.

### 34. More reads in Redis, and live notifications across instances

**What I did:** I reviewed what else Redis should carry beyond the first two caches and accepted the AI's recommendation: cache the claim lists and the queue, add pub/sub for live notifications, and leave the claim detail page uncached.
**AI then:**
- Cached each claimant's and each officer's claim list and each market's unassigned queue. They are cleared on the same single write path as the Follow-ups, so no new eviction point was needed.
- Put a Redis channel behind live notifications. Kafka gives each event to one instance of the notification service; that instance stores the notification and publishes it, and every instance pushes it to its own connected users. Live push previously worked with one instance only.
- Left the claim detail page reading the database directly, because it is where the current status matters most and document uploads change it outside the single write path.
- Wrote the tests first: three new tests cover the cached lists and queue, a notification handled by another instance reaching a user connected here, and live push continuing while Redis is paused. All 169 service tests and the 15 Playwright tests passed, including a run after restarting Redis, which confirmed the subscription is restored.
**AI found while testing:** A publish that times out is not necessarily lost. Once the Redis connection was already open, a notification pushed directly during an outage arrived a second time when Redis resumed, and the browser would have shown it twice. The service now remembers what it pushed directly and skips the late copy. A second check, starting the notification service with Redis stopped, showed that it would not start at all, and that a simple retry never re-subscribed. It now starts regardless and builds a fresh subscription every five seconds until Redis is available; this was confirmed by hand in Docker rather than by an automated test. The outage tests also had to wait for Redis to answer again before the next test started.
**Why:** With many users, the lists and the queue are read far more often than they change, and they can be invalidated precisely, so they are safe to cache. Live push across instances removes the one feature that would have stopped working as soon as the notification service was scaled out.

