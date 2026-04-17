# Frontend Refactor Plan

## Goal

Refactor the frontend toward a stable micro-frontend boundary without attempting a risky full rewrite.

The target architecture is:

- Vue remains the host shell for routing, navigation, authentication wiring, and legacy pages.
- React becomes the owner of the editor domain as an embedded micro-app.
- Shared editor concerns move into React-owned modules first.
- Additional Vue surfaces move only when product value is clear.

This plan is aligned with the backend contracts already introduced for:

- editor session bootstrap;
- versioned editor document snapshots;
- realtime collaboration event contracts;
- signed collaboration room bootstrap for a Yjs-compatible transport.

## Architecture Target

### Vue host responsibilities

- top-level routing and route guards;
- app shell layout, navigation, and page mounting;
- non-editor legacy pages;
- host-to-micro-app bootstrap data handoff;
- cross-app auth token provisioning.

### React editor micro-app responsibilities

- editor document model;
- toolbar and editing controls;
- save flow and draft/version state;
- collaboration session UI;
- presence, cursor, selection, and annotation rendering;
- realtime subscription lifecycle and conflict UX.

### Shared boundary rules

- Vue does not directly manage editor-internal state.
- React does not own global site navigation or unrelated pages.
- Host and micro-app communicate through explicit bootstrap props and events.
- Backend APIs must be consumed through versioned contracts, not ad hoc payload assumptions.

## Refactor Principles

- Keep migration incremental and reversible.
- Move ownership by domain, not by component count.
- Avoid duplicating editor state in Vue and React.
- Prefer contract-first integration over cross-framework shared mutable state.
- Migrate only the highest-value editing surfaces first.

## Phase Plan

## Phase 1: Establish the Micro-App Boundary

### Outcome

The React editor can be mounted by the Vue host with a stable initialization contract.

### Tasks

- define the mount contract from Vue to React;
- include picture id, authenticated user identity, auth mechanism, feature flags if needed, and route context;
- define the unmount and cleanup contract;
- standardize host callbacks for save success, save failure, session disconnected, and unsaved changes state;
- treat `/api/pictures/{id}/editor-session` as the primary bootstrap source for the editor.

### Acceptance

- Vue can mount the React editor for a picture detail editing route.
- React can bootstrap itself from one session payload instead of multiple inferred calls.
- Navigating away fully cleans up subscriptions and editor session state.

## Phase 2: Move Editor State Ownership into React

### Outcome

React becomes the source of truth for editor state.

### Tasks

- centralize document state in React-owned modules;
- centralize command handling for add, update, remove, and undo-ready actions;
- move toolbar state and selected tool state into React;
- remove any Vue-managed duplicate editor state;
- standardize dirty-state tracking and save eligibility inside React.

### Acceptance

- Vue no longer stores editor document or selection state.
- Editor interactions can be understood entirely from the React boundary.
- Dirty-state and save-state are emitted outward as host-facing signals only.

## Phase 3: Move Collaboration into React

### Outcome

Realtime editing behavior is fully owned by the React micro-app.

### Tasks

- connect STOMP lifecycle inside React;
- prefer the signed collaboration room bootstrap returned by `/api/pictures/{id}/editor-session` for Yjs-compatible providers;
- handle connect, subscribe, join, leave, and reconnect flows there;
- consume versioned collaboration events from the backend;
- render presence list, active lock state, cursors, selections, and annotations in React;
- handle reconnect, duplicate session, stale lock, and lock denial UX in React;
- isolate transport handling from presentational editor components.

### Acceptance

- Vue does not interpret editor collaboration events.
- React owns all session and collaboration rendering logic.
- Collaboration state can recover after reconnect without host-page refresh.

## Phase 4: Introduce Versioned Persistence

### Outcome

The editor saves durable versions instead of relying on session-only preview handoff.

### Tasks

- define backend save and version-history contracts for edited results;
- persist editor save actions as versioned artifacts;
- expose save status, last saved version, and conflict state in React;
- allow React to restore from latest version during bootstrap;
- keep the host informed only of high-level save outcomes.

### Acceptance

- Edited results survive reload and session restart.
- React can distinguish local unsaved state from persisted version state.
- Save flow no longer depends on temporary preview-only transfer.

## Phase 5: Expand the React Boundary Selectively

### Outcome

High-value picture-detail editing surfaces move from Vue into the React micro-app.

### Candidate migrations

- picture detail editing controls;
- annotation management panels;
- collaboration side panels;
- editor-specific history/version panels;
- editor-related review aids.

### Decision rule

Only migrate a Vue surface when at least one of these is true:

- it duplicates editor state already owned by React;
- it requires realtime collaboration context;
- it is blocked by the host-to-micro-app boundary;
- it substantially improves product flow by living with the editor.

### Acceptance

- Migrated surfaces do not reintroduce split ownership.
- The host shell remains smaller and simpler over time.

## Non-Goals

- Rewrite the entire frontend into React immediately.
- Split the frontend into "microservices".
- Move global shell concerns into the editor micro-app.
- Migrate low-value or stable pages without product justification.

## Immediate Next Actions

1. Use the new `editor-session` backend contract as the React editor bootstrap entry.
2. Define the frontend mount API between the Vue host and React editor.
3. Move document state and save-state ownership fully into React.
4. Wire presence, cursor, selection, and lock handling to the React collaboration layer.
5. Design versioned persistence for edited results and history retrieval.
