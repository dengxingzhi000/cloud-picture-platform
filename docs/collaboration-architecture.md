# Collaboration Architecture

## Goal

Move collaboration from an ad hoc STOMP document-mutation model toward a room-based architecture that a React editor can use reliably.

## Current backend contract

The backend now exposes two collaboration surfaces:

- `/api/pictures/{id}/editor-session`
  Returns picture detail bootstrap, persisted editor document snapshot, current presence snapshot, legacy STOMP event contract, and a signed collaboration room bootstrap.
- `/api/pictures/{id}/collaboration-room`
  Returns only the signed room bootstrap for clients that already have picture detail and document state.
- `/api/pictures/{id}/collaboration-room/refresh`
  Returns a refreshed signed room bootstrap so the client can renew its room token without rebuilding the full editor session.

## Signed room bootstrap

The room bootstrap contract is intended for a Yjs-compatible provider such as `y-websocket` or Hocuspocus.

It includes:

- `provider`
- `roomId`
- `serverUrl`
- `token`
- `tokenExpiresAt`
- `permission`
- `awarenessEnabled`
- `indexedDbRecommended`

## Active users

Active-user display should be driven by provider awareness state, not by document mutations.

Recommended frontend model:

1. Use the signed room bootstrap to connect to the collaboration provider.
2. Publish local presence with provider awareness:
   - user id
   - username
   - display name
   - avatar color
   - cursor or selection state when relevant
3. Render the active-user list directly from awareness peers.
4. Keep the existing backend `PresenceSnapshot` only as a transitional bootstrap and fallback surface.

## Recommended provider stack

- `yjs`
- `y-websocket` or `@hocuspocus/provider`
- `y-indexeddb`

## Backend responsibility split

Spring Boot remains responsible for:

- JWT authentication
- picture access control
- editor-session bootstrap
- room token signing
- picture metadata and persisted snapshot APIs
- moderation, notifications, and audit-adjacent business logic

The collaboration provider is responsible for:

- room transport
- realtime document sync
- awareness and active-user presence
- reconnect and offline resync

## Transitional note

The existing STOMP collaboration controller still exists for the current frontend path, but new frontend work should treat the signed room bootstrap as the primary architecture target.
