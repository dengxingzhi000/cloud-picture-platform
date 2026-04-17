# Cloud Picture Platform Project Plan

## 1. Planning Goal

This document is aligned with the current repository state and is intended to guide continued collaborative delivery.
Compared with the initial high-level roadmap, this version focuses on:

- What has already landed in the backend.
- What is only partially complete and still needs integration work.
- What should be prioritized next to make the platform truly usable.
- How to split work into iterations that can be developed and verified collaboratively.

## 2. Current Implementation Baseline

### 2.1 Architecture and Technical Baseline

The project already has a usable backend layered by DDD:

- `domain`: core entities and enums for users, pictures, spaces, teams, moderation, tags, and search documents.
- `application`: business orchestration for auth, pictures, teams, tags, and search indexing.
- `infrastructure`: JPA repositories, JWT security, search persistence, and storage provider implementations.
- `interfaces`: REST controllers and DTOs for auth, pictures, tags, teams, and admin management.
- `websocket`: STOMP-based real-time collaboration handlers for picture presence, lock management, and notifications.

Supporting capabilities already present:

- Spring Security + JWT authentication.
- Flyway database migrations `V1` to `V9`.
- Local storage and Tencent COS storage abstraction.
- Cache fallback strategy based on Caffeine and Redis.
- Search index persistence via `PictureSearchDocument`.

### 2.2 Features Already Landed

#### Identity and access control

- User registration, login, and current-user profile query are in place.
- Platform roles are implemented with `USER` and `ADMIN`.
- Admin endpoints are isolated under `/api/admin/**`.
- WebSocket handshake authentication is already wired through JWT integration.

#### Picture asset management

- Picture upload pipeline is implemented.
- Upload supports personal space by default and team space as target.
- Metadata such as checksum, size, width, and height is persisted.
- Visibility and review states are enforced.

#### Public gallery and moderation

- Public picture listing is available.
- Admin review flow is implemented for public assets.
- Moderation history query and CSV export are available.
- Review actions are linked to search reindexing.

#### Tags and search

- Global tag catalog is implemented.
- Picture tag add/remove/list flows are implemented.
- Search supports keyword, owner, space, visibility, review status, size range, time range, orientation, and tag filters.
- Search index rebuild hooks already exist at the service layer.

#### Team collaboration domain

- Team creation and team-linked space creation are implemented.
- Invite, accept, reject, cancel, role update, and member removal flows are implemented.
- Team member event logs are implemented.
- Team invite history and event export are implemented.

#### Real-time collaborative editing foundation

- STOMP endpoint and collaboration topic structure are implemented.
- Presence tracking is implemented.
- Edit lock acquire/release is implemented.
- Disconnect cleanup and presence snapshot broadcast are implemented.
- Notification publishing infrastructure already exists for follow-up integration.

## 3. Maturity Assessment

### 3.1 Relatively complete modules

- Authentication and base authorization.
- Core upload and picture persistence.
- Public review workflow.
- Team membership lifecycle.
- Tag catalog and tag association.
- Search filtering backend.
- WebSocket collaboration foundation.

### 3.2 Modules that are usable but not yet complete

#### Search system

The data model and query side are usable, but the current `SearchIndexService` is still a thin abstraction.
Follow-up work should complete:

- clear indexing trigger strategy;
- full rebuild tooling;
- failure retry and observability;
- search relevance tuning and performance verification.

#### Real-time collaboration

The backend already supports join/leave/presence/lock semantics, but product-level collaborative editing is still incomplete.
Missing pieces are mainly:

- React editor micro-app session lifecycle and connection management;
- Vue host to React editor micro-app boundary and route handoff;
- lock timeout and conflict policies;
- collaboration event persistence or audit if required by product;
- notification consumption flow.

#### Team space governance

Team lifecycle is implemented, but asset governance around team space is still only partially formed:

- no team quota and storage policy yet;
- no team album or folder abstraction yet;
- no team-level moderation or approval strategy yet.

### 3.3 Major gaps

- Automated tests are very thin.
- No analytics dashboard or aggregate metrics module yet.
- AI workflow remains a reserved domain, not a finished capability.
- No clear ops-grade observability, job management, or failure dashboards yet.
- Frontend integration is still behind backend capabilities.

## 4. Next Planning Principle

The next phase should not continue expanding breadth first.
The right direction is to move from "many backend capabilities exist" to "one complete usable collaboration flow is closed-loop".

Priority order:

1. Close the end-to-end collaboration flow.
2. Strengthen search and moderation as core daily operations.
3. Add quality gates, tests, and operational visibility.
4. Then extend analytics and AI capabilities.

## 5. Recommended Iteration Roadmap

### Iteration A: Close the Collaboration MVP

Goal:
Make the current system genuinely usable for "team upload + shared browsing + live collaboration".

Scope:

- complete team-space picture list and detail collaboration integration;
- expose collaboration-related state to frontend in a stable contract;
- add notification delivery for upload, review, invite, and collaboration events;
- confirm permission boundaries for personal space, team space, and public assets.

Expected output:

- Users can create a team, invite members, upload into a team space, and jointly view the same picture.
- Multiple users can see presence and edit lock state in real time.
- Admin and team members receive key event notifications.

Acceptance:

- A complete demo path can be executed without manual DB intervention.
- Collaboration state is consistent across reconnect and disconnect.
- Unauthorized users cannot join protected team picture collaboration sessions.

### Iteration B: Harden Search and Governance

Goal:
Turn existing search and moderation capabilities into a stable production-ready module.

Scope:

- implement search index maintenance job and rebuild entry;
- add index consistency checks and failure retry handling;
- complete admin search maintenance endpoints and operational docs;
- refine search sorting, filtering combinations, and cache invalidation rules.

Expected output:

- Search indexing becomes explainable and maintainable.
- Admin can rebuild or repair search state through standard APIs or jobs.
- Search behavior matches visibility and review constraints reliably.

Acceptance:

- New upload, review change, and tag change all lead to consistent query results.
- Full rebuild on a migrated database can finish successfully.
- Search API behavior is covered by integration tests.

### Iteration C: Build Team Asset Governance

Goal:
Add the missing management layer around team assets so the collaboration model scales.

Scope:

- quota and storage usage enforcement for team spaces;
- team albums/folders or collection abstraction;
- team-level asset visibility rules;
- optional team moderation or approval policy.

Expected output:

- Team spaces become manageable business units instead of only membership containers.
- Asset ownership and management rules are clearer.

Acceptance:

- Team upload limits are enforceable.
- Team admins can manage team asset organization.
- Permission checks remain coherent across list/detail/upload/delete operations.

### Iteration D: Quality and Delivery Upgrade

Goal:
Raise engineering confidence so new features can keep landing safely.

Scope:

- expand unit tests and Spring Boot integration tests;
- add migration verification and representative API tests;
- define test fixtures for auth, upload, moderation, team, and search;
- improve startup validation, error handling, and structured logging.

Expected output:

- Core service changes become test-protected.
- Flyway migrations and main use cases are verified in CI.

Acceptance:

- Critical application services have meaningful test coverage.
- Main REST flows pass in automated runs.
- Search, team, and moderation regressions are catchable before merge.

### Iteration E: Analytics and AI Extension

Goal:
Expand product differentiation after the core workflow is stable.

Scope:

- daily aggregates for uploads, storage, reviews, and team activity;
- admin analytics endpoints and exports;
- AI job model, provider abstraction, and async execution framework;
- auto-tagging, similarity clustering, and later visual search.

Expected output:

- Admins can understand usage and growth trends.
- AI capabilities become structured workflows instead of ad hoc extensions.

Acceptance:

- Analytics data has a clear source-of-truth model.
- AI tasks are traceable, retryable, and permission-controlled.

## 6. Collaborative Editing Special Plan

Because the project already contains the backend basis for collaborative editing, this part deserves a dedicated sub-plan.

The frontend refactor path for this work is documented in [frontend-refactor-plan.md](frontend-refactor-plan.md).

### 6.1 Current state

Already implemented:

- collaboration topic subscription;
- join and leave events;
- presence snapshots;
- edit lock acquisition and release;
- WebSocket disconnect cleanup.

Still missing for a complete collaborative editing experience:

- client-side session lifecycle design inside the React editor micro-app;
- Vue host to React editor micro-app bootstrap and picture context handoff;
- team picture detail page that consumes collaboration events through the micro-app boundary;
- lock timeout and stale-session recovery strategy;
- conflict policy for simultaneous edit intents;
- collaboration event observability and troubleshooting guide.

### 6.2 Recommended delivery order

1. Define picture collaboration frontend contract.
2. Define the editor micro-frontend contract: Vue host shell plus React editor micro-app ownership.
3. Add REST detail endpoint support if the React editor needs initial hydration beyond current summary payloads.
4. Add lock timeout and stale lock cleanup strategy.
5. Integrate notifications and operation audit if collaboration events should be traceable.
6. Add reconnect, duplicate join, and multi-tab behavior tests.

### 6.3 Acceptance baseline

- Two authenticated users in the same team can subscribe to the same picture session.
- Presence list updates within one collaboration cycle.
- Lock denial is deterministic when another user holds the lock.
- Disconnect releases stale state or makes it recoverable within a bounded time.

## 7. Risks and Constraints

### 7.1 Current risks

- Search indexing is not yet production-grade in orchestration depth.
- WebSocket collaboration state appears memory-oriented and may need persistence or timeout policy.
- Tests are insufficient for the number of implemented business branches.
- Cache invalidation spans multiple modules and can drift without regression tests.

### 7.2 Engineering constraints

- Any new entity or table must come with a Flyway migration.
- Team, search, and collaboration modules now interact and should not evolve in isolation.
- Vue host and React editor micro-app contracts need to be versioned explicitly alongside backend APIs until stronger API governance exists.

## 8. Suggested Work Breakdown

To support collaborative editing across contributors, work can be split by stream:

- Stream 1: collaboration and notification integration.
- Stream 2: search indexing and admin maintenance.
- Stream 3: team asset governance and quota rules.
- Stream 4: automated tests and delivery pipeline.
- Stream 5: analytics and AI pre-research.

Recommended dependency order:

- Complete Stream 1 and Stream 4 first.
- Then push Stream 2 and Stream 3 in parallel.
- Start Stream 5 only after the collaboration MVP is stable.

## 9. Definition of Done

A planned module should be considered complete only if it includes:

- domain model and persistence changes;
- REST or WebSocket contract updates;
- permission checks;
- migration scripts where needed;
- tests for normal path and key failure paths;
- documentation updates for usage and maintenance.

## 10. Immediate Action List

Recommended next concrete tasks for this repository:

1. Treat the current frontend direction as Vue host shell plus React editor micro-app, not frontend microservices.
2. Add a dedicated picture detail or editor-session API for collaboration and management views if the current summary payload is insufficient.
3. Complete collaboration notification flow and lock lifecycle policy.
4. Implement search maintenance and reindex operational capability.
5. Add integration tests for team invite flow, picture search filters, and moderation history export.
6. Sync README and other docs so they reflect the current backend maturity instead of the earlier planning snapshot.
7. Follow [frontend-refactor-plan.md](frontend-refactor-plan.md) for the Vue host to React editor migration sequence.
