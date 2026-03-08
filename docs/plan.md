# Project Plan

## Scope
- Build an enterprise-grade collaborative cloud picture platform.
- Support public galleries, private spaces, and team spaces.
- Provide real-time collaboration, analytics, and AI-assisted workflows.

## Current Status

### Completed
- Backend skeleton with DDD packages and core dependencies.
- Flyway migrations for core tables (users, spaces, pictures, moderation).
- JWT auth (register/login/me) and base security config.
- Upload pipeline with local storage and COS provider option.
- Public gallery listing and moderation workflow (approve/reject).
- Moderation audit history, filters, and CSV export API.
- Vue 3 + Vite + TS + Element Plus frontend scaffold with pages:
  - Login, Public Gallery, Upload, Admin Review List, Admin Review Detail.

### In Progress
- Resolve local H2 Flyway migration and verify tables on startup.
- Align frontend admin review detail with dedicated picture detail API (if added).

### Next Up (Detailed)
- Team space model, roles, and member management.
  - Add `team`, `membership`, and `role` tables + migrations.
  - Implement invite/accept flow and role checks in controllers.
  - Add team space APIs: create, list, member management.
- Tags, search, and indexing (keyword + filters).
  - Add `tag` and `picture_tag` tables + CRUD endpoints.
  - Add search API with filters (tag, owner, space, time range).
  - Queue async indexing (stub job + hook on upload).
- WebSocket collaboration and notifications.
  - Add presence/notification channels and message DTOs.
  - Broadcast review events and upload events to admins.
  - Add basic WebSocket auth handshake with JWT.
- Analytics dashboards and metrics export.
  - Add daily aggregates table (views, uploads, storage usage).
  - Admin API endpoints for metrics and CSV export.
  - Frontend admin dashboard page.
- AI tagging/similarity workflows.
  - Define AI job table + status enums.
  - Add background job stub and API trigger.
  - Store AI tags and similarity groups.

### Suggested Iterations
- Iteration A (Core Team Space)
  - Team space schema + APIs.
  - Team member invite + role management.
  - Frontend team space page (list/create/members).
- Iteration B (Tagging + Search)
  - Tag CRUD, assign/unassign tags.
  - Search API + paging + filters.
  - Frontend: search page + tag filters.
- Iteration C (Real-Time + Notifications)
  - WebSocket auth + presence.
  - Review/upload notifications to admins.
  - Frontend: notification drawer + live updates.
- Iteration D (Analytics)
  - Daily aggregates and admin metrics endpoints.
  - CSV export for analytics.
  - Frontend: admin dashboard charts.
- Iteration E (AI)
  - AI job model + trigger endpoints.
  - Store AI tags and similarity clusters.
  - Frontend: AI tagging controls + similarity view.

### Frontend Tasks (Breakdown)
- App foundation
  - Route guards for admin-only areas.
  - Global error handling and toast strategy.
  - Upload progress + retry UI.
- Public gallery
  - Search bar + filters (when backend ready).
  - Responsive grid with lazy loading.
- Upload
  - Batch upload support.
  - Validation (size/type limits).
- Admin review
  - Review list filters and bulk actions.
  - Review detail: show EXIF metadata + audit history.
- Team spaces (future)
  - Team list + create.
  - Member management and roles.
  - Shared album view.

## Phases (Reference)

### P0 - Foundation (current)
- Establish repository structure and DDD layering.
- Define requirements and module boundaries.
- Add core backend dependencies and config placeholders.
- Prepare base documentation and contribution guidance.

Acceptance:
- Project starts with default config.
- Docs describe scope, roles, and module requirements.

### P1 - Identity, Storage, and Upload
- Authentication, authorization, and basic user profile.
- OSS/COS abstraction and local storage fallback.
- Upload pipeline: metadata extraction, checksum, and size validation.
- Public gallery listing and basic search by keyword.

Acceptance:
- Users can register/login and upload images.
- Public images are searchable and viewable.

### P2 - Metadata, Audit, and Search
- Tagging, folders, and collections.
- Moderation workflow with audit trail.
- Advanced search (tags, time range, owner, space).
- Redis + Caffeine multi-level cache for hot data.

Acceptance:
- Admin can approve/reject uploads.
- Search filters work with paging and caching.

### P3 - Spaces and Team Collaboration
- Personal space quotas and lifecycle policies.
- Team space with invitation, roles, and permissions.
- Shared albums, comments, and versioned assets.

Acceptance:
- Team members can co-manage assets in shared space.
- Role-based access enforced across APIs.

### P4 - Real-Time Collaboration
- WebSocket presence, typing/locking, and notifications.
- Collaborative edit sessions with conflict handling.
- Activity feed and audit events.

Acceptance:
- Multiple users see live updates and edit status.

### P5 - Analytics and AI
- Usage analytics, storage growth, and access heatmaps.
- AI tagging, similarity search, and generation workflows.
- Cost controls and model usage governance.

Acceptance:
- Admin dashboard shows key metrics and AI usage.

## Quality Gates
- API contract and schema validation.
- Security review for permissions, upload abuse, and data leakage.
- Performance targets for upload throughput and search latency.
