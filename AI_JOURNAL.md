# AI Working Journal

Running log of how I worked with the AI (Claude Code) on this assessment. One
entry per decision. Not polished on purpose.

Legend: **Accepted** = took the AI's suggestion. **Challenged** = pushed back
and it changed. **Overrode** = AI recommended one thing, I went the other way.

---

## 18 Nov 2026 — Planning session (before any code)

### 1. Which workflow to drive the AI with

**Asked:** Superpowers or OpenSpec for this take-home?
**AI said:** OpenSpec. The panel grades decisions more than code volume, and OpenSpec leaves proposal / design / specs / tasks committed in the repo. Superpowers' thinking mostly stays in chat, and its full loop is heavy for 5 hours.
**What I did:** Accepted.
**Why:** _TODO (mine to write)_

### 2. "As real as possible, all entities with DB tables"

**Asked:** I wanted everything real, all entities, proper production flow.
**AI said:** Pushed back a bit. Brief says prioritise ruthlessly, so design the full model on paper but only build one real slice. Rest goes under "with more time".
**What I did:** Accepted the idea, but later pulled documents and payment back into the build (see 8 and 9).
**Why:** _TODO_

### 3. Backend stack

**Asked:** AI asked me to pick, .NET or Spring Boot, said it has to be the one I can defend line by line.
**AI said:** No preference. Noted neither JDK nor .NET SDK was installed on my machine anyway.
**What I did:** Picked Spring Boot.
**Why:** _TODO_

### 4. One service or split

**Asked:** One backend service or several?
**AI said:** One service lah, with Kafka consumers inside. 5 hours only, fewer things to break in the demo, and "why three services for one team" is a hard question.
**What I did:** Overrode. Went with three services: claims, reporting, notification.
**Why:** _TODO — panel will surely ask this one_

### 5. Grill the design before writing any spec

**Asked:** Told the AI to hold the `openspec init` and run a grilling session first, in case requirements change. Also asked for ER, C4, sequence, state machine and user flow / wireframe diagrams.
**AI said:** Ran 34 questions over a few rounds, each with its own recommended answer.
**What I did:** My call to do this step. Went through every question.
**Why:** _TODO_

### 6. Round 1 answers (scope, roles, auth, policy, tables, markets, assignment, reserve, approval, diagram format)

**AI said:** Ten recommendations, e.g. JWT over a plain role header, claim must link to a policy, separate motor / property detail tables instead of one JSON column, auto-create an initial reserve at submission so unassessed claims don't show zero exposure, manager sign-off stays on paper.
**What I did:** Accepted all ten.
**Why:** _TODO — at least say why the reserve one makes sense to me_

### 7. Login screens and testing approach

**Asked:** Round 2 had one shared login, and tests written after the code.
**AI said:** One login, role decides where you land. Tests: state machine unit tests plus two integration tests.
**What I did:** Challenged both. Wanted `/login` for claimants and `/admin/login` for staff. Wanted proper TDD, red-green-refactor, with DRY and YAGNI.
**AI then:** Agreed. Added that the server must refuse the wrong role at the wrong door, not just the UI. Limited TDD to code with logic (not POMs, config or Angular templates). One commit per green cycle, no deliberately red commits.
**Why:** _TODO_

### 8. `claim_document`

**AI said:** YAGNI, so tables for things we don't build get no migration. That included `claim_document`.
**What I did:** Overrode. Documents are in the build.
**AI then:** Files on a local volume with metadata in the table, PDF / JPEG / PNG up to 10 MB checked on the server, only the claim owner or staff can download, no extra Kafka event.
**Why:** _TODO_

### 9. Payment

**Asked:** Asked the AI to explain what a payment module really involves.
**AI said:** Laid out three options: just stamp a reference on the decision, a thin `payment` table, or a fourth service with a Kafka round-trip to a fake bank. Recommended the thin table; said a fourth service is where 5 hours stops being realistic.
**What I did:** Accepted the thin table.
**Why:** _TODO_

### 10. Manager permissions

**Asked:** Diagrams only said "officer" everywhere.
**What I did:** Told it a manager must be able to do everything an officer can, plus the dashboard.
**AI then:** Said that was the intent, made it explicit in the docs and the spec.
**Why:** _TODO_

### 11. What if Kafka is down

**Asked:** If the queue is down, do we have a table to roll back?
**AI said:** Not rollback. The `outbox_event` table holds the events and a relay keeps retrying, so claims still go through and dashboards / notifications just catch up later. Also pointed out two gaps: dead letters sit in a Kafka topic nobody is watching, and published outbox rows never get purged.
**What I did:** Accepted. Both gaps go under "with more time".
**Why:** _TODO_

### 12. Commit messages

**What I did:** Told the AI to follow Conventional Commits.
**AI then:** Mapped it to the TDD flow: `feat(scope)` for a green cycle with its test, `refactor(scope)` on its own, `chore` / `build` for scaffolding.

---

## Things the AI decided that I still need to check

- One assessment per claim (unique constraint).
- Approval is refused if the amount is above the policy's sum insured.
- JWT signed with a shared key across the three services (it flagged this as a shortcut).
- The Mermaid diagrams in `docs/` have not been rendered yet.
- All the spec files under `openspec/changes/claims-platform-mvp/` — read them properly before building.

---

## 18 Nov 2026 — Build session

Said "start building" and let the AI run through the task list. Entries below are
what it did and decided along the way. Most of these were the AI's calls, not
mine, so I need to go through them properly.

### 13. Java and Spring Boot versions

**AI did:** My machine had JDK 17, 25 and 27 from Homebrew but not 21. It installed 21 because that is what we agreed, instead of quietly using 25. For Spring Boot it asked start.spring.io for the current version (4.1.2) and the real starter names rather than trusting memory. Boot 4 moved a lot of packages and uses Jackson 3, so it looked inside the jars when an import failed.
**What I did:** Accepted.
**Why:** _TODO_

### 14. Building three things at once

**AI did:** Once the event contract (`claim-events`) was fixed, it started separate AI agents for reporting-service, notification-service and later the Angular app, while it built claims-service itself. Each agent got a written brief with the fixed conventions (ports, JWT claims, topic, endpoints) and was told not to commit. The main AI re-ran each agent's tests itself and read the code before committing.
**What I did:** AI's call. Not something I asked for.
**To check:** read reporting-service and notification-service line by line, since I did not watch those being written.

### 15. How TDD actually went (being honest here)

Asked for strict red-green-refactor. What really happened:

| Slice | Red step seen? |
|---|---|
| Event contract | Yes, but only a compile failure (classes did not exist yet) |
| Sign-in and roles | Yes, properly: app booted, 10 of 12 tests failed on behaviour, then green |
| State machine | Compile failure, then green |
| Claim lifecycle (submit, pick up, assess, decide) | Yes: 30 HTTP tests failed with 404s before the code existed |
| Info requests, documents, settlement | Yes: 32 tests red, then turned green group by group |
| Outbox relay | Compile failure, then green (incl. the broker-outage test) |
| reporting-service (agent) | One compile-failure red for the whole group, green on first run after |
| notification-service (agent) | Tests written first, but the red run failed for a config reason, so no real behavioural red |

So: tests always came before the code, but the commits are per feature slice
(one red, one green), not one commit per tiny cycle, and there was almost no
separate refactor commit. If the panel asks "was this strict TDD?", the honest
answer is "test-first, coarse-grained".

### 16. Things the AI changed from the design while building

- **Extra columns on `claim`:** `current_reserve`, `decided_at`, `settled_at`. The ER diagram said "latest reserve row is current"; AI kept the reserve history table but also copies the current figure onto the claim so reads and events do not need a subquery. ER diagram updated.
- **`CHAR(2)`/`CHAR(3)` became `VARCHAR`** for market and currency, because Hibernate schema validation rejected `CHAR`.
- **PostgreSQL is on host port 5433**, since 5432 was already used by a local Postgres.
- **Policy seed period runs to end of 2027**, so tests do not start failing in January.
- **reporting and notification use plain JDBC, not JPA.** Their writes are upserts and aggregates.
- **Claims-service puts information requests, documents and payment in the same Java package as claims** instead of separate packages, to avoid a package cycle.

### 17. Behaviour the AI chose that I did not specify

- Someone else's claim, policy or document returns **404, not 403**, so ids cannot be probed.
- Sign-in fails with the **same response** for unknown account, wrong password and wrong door.
- **Reserve and assessment are hidden from the claimant** (internal figures). Payment reference too.
- Rejecting or approving checks the **status first**, then the amount.
- The relay **stops at the first failed send** so later events cannot overtake an unpublished one.
- Uploaded file type is read from the **first bytes of the file**, not the declared content type. Downloads are forced as attachments.
- `/v3/api-docs` is **public** on all three services so the frontend can generate types.
- Staff actions return **200 with the whole updated claim**, not 201 or 204.

**Why (for all of 16 and 17):** _TODO — go through each, keep or change_

---

## 18 Nov 2026 — Hands-on testing (me clicking through the app)

This part is where I pushed back the most, after actually using what the AI built.

### 18. Demo password

**What I did:** Asked for all seeded accounts to use one simple password.
**AI then:** Changed the seed, tests, README and the running database, and made re-seeding refresh the hash. Added a line in the README that the seed is demo data only.

### 19. "Why does my password not work?"

**Asked:** Officer account refused at the claimant login.
**AI said:** Wrong door, working as designed. The server gives the same error for wrong password, unknown email and wrong door on purpose, so the form cannot be used to find out which accounts exist. Offered a friendlier hint.
**What I did:** Accepted the explanation. _TODO: decide if I want the hint._

### 20. Calendar on the date field

**What I did:** Asked for a calendar dropdown.
**AI first:** Added a button that opens the browser's built-in date picker (no new library). It did not work properly in my browser.
**AI then:** Switched to the Angular Material datepicker, day/month/year, future dates disabled.
**Lesson:** The "lazy" native option was not good enough here. The AI only confirmed the button called the picker, not that a calendar appeared, and said so.

### 21. Is "amount claimed" really necessary?

**Asked:** In insurance, does the claimant need to state the amount?
**AI said:** No. At first notification most people have no quote, and the figure that matters is the insurer's reserve. But our design used the claimed amount as the initial reserve, so a blank needs a stand-in or exposure goes blind again. Recommended: optional "estimated loss", with a default reserve when blank.
**What I did:** Challenged the original design; accepted the fix.
**AI then changed its own proposal:** it first said "a fixed default figure per line", then used a **percentage of the sum insured** (10% motor, 5% property) because a fixed number means nothing across currencies.
**Why:** _TODO — and check I am happy with 10% / 5%_

### 22. Refresh loses the form

**What I did:** Reported that refreshing the New claim page sent me back to step 1.
**AI then:** Saves the draft and the current step in sessionStorage, restores both on load, clears on submit or cancel. Attached files cannot be saved and have to be re-added; the page says so.

### 23. One claim per policy at a time

**What I did:** Told the AI a policy can only have one claim until that flow is done.
**AI then:** Service check with a clear 409, plus a partial unique index so two submissions at once cannot both pass. The policy dropdown greys out a policy with a claim in progress.
**AI's note:** In real practice one policy can have several open claims for separate incidents. It built my rule and recorded the alternative in the design (D10a).
**Why:** _TODO — the panel may ask why I chose the stricter rule_

### 24. "Why are the other services not in Docker?"

**Asked:** My Docker list only showed Postgres and Kafka.
**AI said:** By design: only infrastructure was containerised, services ran with `java -jar` on the host so a code change needs a quick restart, not an image rebuild. Downside is five commands in four terminals. Offered to containerise everything.
**What I did:** Asked for all of it in Docker Compose.
**AI then:** One Dockerfile for the three services (build argument picks the module), nginx for the frontend that also routes `/api` to the services, and compose entries for all. It had to give Kafka two listeners, one for containers and one for the host, because a Kafka client reconnects to whatever address the broker advertises. Services run as a non-root user. Verified by walking a claim through the containers.
**To check:** I should be able to explain the two Kafka listeners myself.
