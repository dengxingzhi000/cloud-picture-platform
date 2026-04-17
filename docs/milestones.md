# Collaboration Milestones

## Purpose

This document turns the project plan into an execution-oriented milestone board so multiple contributors can work against the same target without drifting on scope.

For the frontend migration sequence behind collaboration work, see [frontend-refactor-plan.md](frontend-refactor-plan.md).

## Milestone M1: Collaboration MVP Closed Loop

### Goal

Deliver a complete user path for:

- create team;
- invite member;
- upload picture into team space;
- open same picture from multiple clients;
- observe presence and edit lock changes in real time.

### Backend tasks

- add or confirm picture detail API for collaboration page hydration;
- expose a signed collaboration room bootstrap and token refresh path for the React editor micro-app;
- ensure team-space picture access control is complete for list and detail flows;
- connect upload, review, invite, and collaboration events to notification publisher;
- define lock timeout and stale lock cleanup strategy;
- verify WebSocket handshake and session auth behavior for team users.

### Frontend or integration tasks

- keep Vue as host shell and route container for now;
- build the collaboration page and editing session UI inside the React editor micro-app;
- connect STOMP client lifecycle inside the React app: connect, subscribe, join, leave, reconnect;
- render presence list and lock state from the React micro-app;
- handle lock denial and stale reconnect state at the micro-app boundary.

### Acceptance

- two team members can see the same collaboration session;
- presence updates are visible to both clients;
- only one user can hold the edit lock at a time;
- unauthorized users cannot subscribe to protected team picture sessions.

## Milestone M2: Search Governance

### Goal

Turn current search capability into an operationally maintainable module.

### Tasks

- implement concrete search indexing orchestration behind `SearchIndexService`;
- add full rebuild / reindex flow;
- add admin maintenance endpoint or command entry;
- define retry and failure logging for indexing;
- verify cache invalidation and query consistency after upload, review, and tag changes.

### Acceptance

- newly uploaded or reviewed data becomes searchable predictably;
- full reindex can rebuild all search documents from the source tables;
- admin can trigger maintenance without manual database edits.

## Milestone M3: Team Asset Governance

### Goal

Move team space from membership model to manageable asset domain.

### Tasks

- enforce team quota and storage policies;
- add collection, album, or folder abstraction;
- define team asset management rules for delete, move, and visibility changes;
- evaluate whether team-level review rules are needed.

### Acceptance

- team storage usage is enforceable;
- team admins can manage asset organization;
- permissions are consistent across team asset operations.

## Milestone M4: Quality Gate Upgrade

### Goal

Protect the existing business logic with repeatable automated verification.

### Priority test areas

- auth flow;
- picture upload and visibility behavior;
- public review and moderation history export;
- team invite lifecycle and member role changes;
- search filter combinations and visibility constraints;
- collaboration lock and session behavior where practical.

### Tasks

- add service-layer tests;
- add Spring Boot integration tests for main REST flows;
- verify Flyway startup against migrated schema;
- add seed fixtures or builder utilities for common test data.

### Acceptance

- main flows run in automated test suite;
- regressions in moderation, team, and search are catchable before merge;
- migration-related failures are detected early.

## Milestone M5: Analytics and AI Foundation

### Goal

Prepare differentiated features after the core workflow is stable.

### Tasks

- define daily aggregate tables and jobs;
- add admin metrics APIs and exports;
- define AI job table, status machine, and provider abstraction;
- prepare auto-tagging and similarity clustering workflow.

### Acceptance

- analytics has a clear source-of-truth data model;
- AI jobs are traceable, retryable, and permission-controlled.

## Suggested Parallel Ownership

### Track A: Collaboration

- Vue host to React editor micro-app contract
- WebSocket session flow
- notifications
- permission verification

### Track B: Search

- indexing orchestration
- rebuild tools
- admin maintenance
- consistency verification

### Track C: Team governance

- quota
- asset organization
- team policies

### Track D: Quality

- integration tests
- test fixtures
- migration verification

## Current Recommended Sequence

1. Finish M1 and M4 first.
2. Run M2 in parallel once M1 contracts are stable.
3. Start M3 after collaboration MVP is usable.
4. Reserve M5 for the next product-expansion phase.

## Definition of Ready

Before a task enters development, it should have:

- clear owner;
- affected modules identified;
- API or schema impact identified;
- acceptance criteria written;
- dependency on other milestones stated.

## Definition of Done

A milestone task is only done when it includes:

- code changes;
- permissions and data constraints handled;
- migration or config updates if needed;
- tests or explicit test rationale;
- documentation updates.
