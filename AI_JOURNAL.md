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
