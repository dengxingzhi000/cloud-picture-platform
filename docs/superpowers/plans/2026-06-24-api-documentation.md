# Cloud Picture Platform — API Documentation Summary

> **Base URL:** `http://localhost:8080/api/v1` (after Task 22 adds versioning)  
> **Current Base URL:** `http://localhost:8080/api` (before versioning)  
> **Auth:** Bearer JWT token in `Authorization` header, or `X-API-Key` header  
> **Content-Type:** `application/json` (except file uploads: `multipart/form-data`)

---

## Authentication

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/auth/register` | POST | No | Register a new user |
| `/api/auth/login` | POST | No | Login, returns JWT token |
| `/api/auth/me` | GET | Yes | Get current user info with menus |
| `/api/auth/me` | PATCH | Yes | Update current user profile |

### Register
```
POST /api/auth/register
{
  "username": "string (3-64)",
  "email": "string (email format)",
  "password": "string (8-100)",
  "displayName": "string (max 80, optional)"
}
```
**Response:** `{ success, code, message, data: { userId, username, token, expiresAt } }`

### Login
```
POST /api/auth/login
{
  "usernameOrEmail": "string",
  "password": "string"
}
```
**Response:** `{ success, code, message, data: { userId, username, token, expiresAt } }`

---

## Pictures

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/pictures/upload` | POST | Yes | Upload a picture |
| `/api/pictures/upload/deduplication` | POST | Yes | Upload with dedup (rapid upload) |
| `/api/pictures/check-duplicate` | GET | Yes | Pre-check if file exists by SHA-256 |
| `/api/pictures/public` | GET | No | List public pictures |
| `/api/pictures/recommendations` | GET | Optional | Get recommended pictures |
| `/api/pictures/search` | GET | Optional | Search pictures |
| `/api/pictures/{id}` | GET | Optional | Get picture detail |
| `/api/pictures/{id}/document` | GET | Yes | Get editor document |
| `/api/pictures/{id}/editor-session` | GET | Yes | Get full editor session |
| `/api/pictures/{id}/collaboration-room` | GET | Yes | Get collaboration room info |
| `/api/pictures/{id}/collaboration-room/refresh` | POST | Yes | Refresh room token |
| `/api/pictures/{id}/tags` | GET | No | List tags |
| `/api/pictures/{id}/tags` | POST | Yes | Add tags |
| `/api/pictures/{id}/tags/{tagId}` | DELETE | Yes | Remove a tag |

### Upload Picture
```
POST /api/pictures/upload
Content-Type: multipart/form-data

file: <binary>
name: "string (optional, defaults to filename)"
spaceId: "uuid (optional, defaults to personal space)"
visibility: "PUBLIC|PRIVATE|TEAM (default: PRIVATE)"
```
**Response:** `PictureResponse { id, name, url, visibility, reviewStatus, sizeBytes, width, height, contentType }`

### Search Pictures
```
GET /api/pictures/search?keyword=xxx&page=0&size=20&visibility=PUBLIC&sortBy=createdAt&sortDir=DESC
```
**Query Params:**
- `keyword` — search text
- `page` — page number (default 0)
- `size` — page size (default 20, max 100)
- `visibility` — PUBLIC|PRIVATE|TEAM
- `reviewStatus` — PENDING|APPROVED|REJECTED|AUTO_APPROVED|AUTO_REJECTED
- `sortBy` — createdAt|name|sizeBytes
- `sortDir` — ASC|DESC

---

## Batch Operations

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/pictures/batch/delete` | POST | Yes | Batch delete pictures |
| `/api/pictures/batch/visibility` | POST | Yes | Batch update visibility |
| `/api/pictures/batch/tag` | POST | Yes | Batch add tags |
| `/api/pictures/batch/move` | POST | Yes | Batch move to album |

### Batch Delete
```
POST /api/pictures/batch/delete
{
  "pictureIds": ["uuid1", "uuid2"]
}
```

---

## Picture Versions

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/pictures/{pictureId}/versions` | GET | No | List all versions |
| `/api/pictures/{pictureId}/versions/{version}` | GET | No | Get specific version |
| `/api/pictures/{pictureId}/versions` | POST | Yes | Create version snapshot |

---

## Picture Comments

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/pictures/{pictureId}/comments` | POST | Yes | Create comment |
| `/api/pictures/{pictureId}/comments` | GET | No | List comments |
| `/api/pictures/{pictureId}/comments/{commentId}` | PATCH | Yes | Update comment |
| `/api/pictures/{pictureId}/comments/{commentId}` | DELETE | Yes | Delete comment |
| `/api/pictures/{pictureId}/comments/{commentId}/resolve` | POST | Yes | Resolve comment |

### Create Comment
```
POST /api/pictures/{pictureId}/comments
{
  "content": "string (required, max 2000)",
  "parentId": "uuid (optional, for threaded replies)",
  "x": 0.5,  // optional, spatial coordinate on image (0.0-1.0)
  "y": 0.3   // optional, spatial coordinate on image (0.0-1.0)
}
```

---

## Image Search

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/search/semantic` | GET | No | Semantic search via hybrid search |

```
GET /api/search/semantic?query=nature+landscape&limit=20
```

---

## Teams

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/teams` | POST | Yes | Create a team |
| `/api/teams` | GET | Yes | List my teams |
| `/api/teams/{id}` | GET | Yes | Get team detail |
| `/api/teams/{id}` | PATCH | Yes | Update team |
| `/api/teams/invites` | GET | Yes | List my invites |
| `/api/teams/{id}/members` | GET | Yes | List team members |
| `/api/teams/{id}/invites` | GET | Yes | List pending invites |
| `/api/teams/{id}/invites/history` | GET | Yes | Invite history |
| `/api/teams/{id}/events` | GET | Yes | Member events |
| `/api/teams/{id}/events/export` | GET | Yes | Export events as CSV |
| `/api/teams/{id}/invites` | POST | Yes | Invite a member |
| `/api/teams/{id}/accept` | POST | Yes | Accept invite |
| `/api/teams/{id}/reject` | POST | Yes | Reject invite |
| `/api/teams/{id}/invites/{userId}` | DELETE | Yes | Cancel invite |
| `/api/teams/{id}/members/{userId}/role` | PATCH | Yes | Update member role |
| `/api/teams/{id}/members/{userId}` | DELETE | Yes | Remove member |

### Create Team
```
POST /api/teams
{
  "name": "string (required, max 80)",
  "description": "string (optional, max 200)"
}
```

### Invite Member
```
POST /api/teams/{id}/invites
{
  "username": "string (required, max 120)",
  "role": "ADMIN|MEMBER (default: MEMBER)"
}
```

---

## Team Activities

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/teams/{teamId}/activities` | GET | Yes* | List team activities |

*Requires authentication after Task 6 fix.

---

## Albums

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/albums` | POST | Yes | Create album |
| `/api/albums` | GET | Yes | List albums by space |
| `/api/albums/{id}` | GET | Yes | Get album detail |
| `/api/albums/{id}` | PATCH | Yes | Update album |
| `/api/albums/{id}` | DELETE | Yes | Delete album |
| `/api/albums/{id}/pictures` | POST | Yes | Add pictures to album |
| `/api/albums/{id}/pictures` | GET | Yes | List album pictures |
| `/api/albums/{id}/pictures/{pictureId}` | DELETE | Yes | Remove picture from album |
| `/api/albums/{id}/cover` | PATCH | Yes | Set album cover |

---

## Tags

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/tags` | POST | Yes | Create a tag |
| `/api/tags` | GET | Yes | List tags |
| `/api/tags/{id}` | PATCH | Yes | Update a tag |
| `/api/tags/{id}` | DELETE | Yes | Delete a tag |

---

## Spaces

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/spaces/{id}/usage` | GET | Yes | Get space usage/quota |

---

## Notifications

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/notifications` | GET | Yes | List notifications |
| `/api/notifications/unread-count` | GET | Yes | Get unread count |
| `/api/notifications/{id}/read` | PATCH | Yes | Mark single read |
| `/api/notifications/read-all` | PATCH | Yes | Mark all read |

---

## AI Assistant

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/ai/chat` | POST | Yes | Chat with AI (conditional on AiGateway) |

```
POST /api/ai/chat
{
  "message": "string",
  "context": "string (optional)"
}
```

---

## Developer API Keys

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/developer/keys` | POST | Yes | Create API key |
| `/api/developer/keys` | GET | Yes | List API keys |
| `/api/developer/keys/{id}` | DELETE | Yes | Delete API key |

---

## Watermark & Export

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/teams/{teamId}/watermark` | GET | Yes* | Get watermark config |
| `/api/teams/{teamId}/watermark` | PUT | Yes | Update watermark config |
| `/api/teams/{teamId}/export-presets` | GET | Yes* | List export presets |
| `/api/teams/{teamId}/export-presets` | POST | Yes | Create export preset |
| `/api/export-presets/{presetId}` | DELETE | Yes | Delete export preset |
| `/api/pictures/{pictureId}/export` | POST | Yes | Create export task |
| `/api/export-tasks/{taskId}` | GET | Yes* | Get export task status |

*Requires authentication after Task 6 fix.

---

## Webhooks

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/webhooks` | POST | Yes | Register webhook |
| `/api/webhooks` | GET | Yes | List webhooks |
| `/api/webhooks/{id}` | DELETE | Yes | Delete webhook |
| `/api/webhooks/{id}/test` | POST | Yes | Send test ping |
| `/api/webhooks/{id}/deliveries` | GET | Yes | List deliveries |

---

## Excalidraw Scenes

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/scenes` | POST | Yes | Create scene |
| `/api/scenes/{sceneId}` | GET | No | Get scene |
| `/api/scenes/{sceneId}/snapshot` | GET | No | Get scene snapshot |
| `/api/scenes/by-picture/{pictureId}` | GET | No | Get scene by picture |
| `/api/scenes/{sceneId}` | DELETE | Yes | Delete scene |
| `/api/scenes/{sceneId}/snapshot` | PUT | Yes | Update snapshot |

---

## Admin Endpoints

### Pictures
| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/admin/pictures/pending` | GET | Admin | List pending pictures |
| `/api/admin/pictures/{id}` | GET | Admin | Get picture detail |
| `/api/admin/pictures/{id}/review` | POST | Admin | Approve/reject picture |
| `/api/admin/pictures/{id}/reviews` | GET | Admin | List moderation history |
| `/api/admin/pictures/reviews` | GET | Admin | Search moderation records |
| `/api/admin/pictures/reviews/export` | GET | Admin | Export as CSV |

### Users
| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/admin/users` | GET | Admin | List users |

### Roles
| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/admin/roles` | GET | Admin | List roles |
| `/api/admin/roles/{id}` | GET | Admin | Get role detail |
| `/api/admin/roles` | POST | Admin | Create role |
| `/api/admin/roles/{id}` | PUT | Admin | Update role |
| `/api/admin/roles/{id}` | DELETE | Admin | Delete role |
| `/api/admin/roles/{id}/permissions` | GET | Admin | Get role permissions |
| `/api/admin/roles/{id}/permissions` | PUT | Admin | Update role permissions |

### Permissions
| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/admin/permissions` | GET | Admin | List permissions |
| `/api/admin/permissions/{id}` | GET | Admin | Get permission detail |
| `/api/admin/permissions` | POST | Admin | Create permission |
| `/api/admin/permissions/{id}` | PUT | Admin | Update permission |
| `/api/admin/permissions/{id}` | DELETE | Admin | Delete permission |

### User Roles
| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/admin/users/{userId}/roles` | GET | Admin | Get user's roles |
| `/api/admin/users/{userId}/roles` | POST | Admin | Assign role |
| `/api/admin/users/{userId}/roles/{roleId}` | DELETE | Admin | Remove role |
| `/api/admin/users/{userId}/roles` | PUT | Admin | Bulk update roles |

### AI Stats
| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/admin/ai/stats` | GET | Admin | Get AI usage statistics |

### Search Management
| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/admin/search/reindex` | POST | Admin | Reindex all pictures |
| `/api/admin/search/pictures/{pictureId}/reindex` | POST | Admin | Reindex single picture |

---

## WebSocket (STOMP over SockJS)

**Endpoint:** `ws://localhost:8080/ws`

### STOMP Destinations

| Destination | Type | Description |
|-------------|------|-------------|
| `/topic/pictures/{id}/collab` | Subscribe | Picture collaboration events |
| `/topic/admin/notifications` | Subscribe | Admin notifications |
| `/topic/admin/reviews` | Subscribe | Admin review events |
| `/user/queue/notifications` | Subscribe | Personal notifications |
| `/app/pictures/{id}/join` | Send | Join picture editing |
| `/app/pictures/{id}/leave` | Send | Leave picture editing |
| `/app/pictures/{id}/lock` | Send | Request edit lock |
| `/app/pictures/{id}/unlock` | Send | Release edit lock |
| `/app/pictures/{id}/lock/refresh` | Send | Refresh lock TTL |
| `/app/pictures/{id}/annotation` | Send | Send cursor/selection/document operations |

### STOMP Auth
Include JWT in CONNECT frame headers:
```
CONNECT
Authorization: Bearer <jwt-token>
```

---

## Error Responses

All errors return:
```json
{
  "success": false,
  "code": "ERROR_CODE",
  "message": "Human-readable error message",
  "data": null
}
```

### Error Codes
| Code | HTTP Status | Description |
|------|-------------|-------------|
| `BAD_REQUEST` | 400 | Invalid input |
| `UNAUTHORIZED` | 401 | Missing or invalid credentials |
| `FORBIDDEN` | 403 | Insufficient permissions |
| `NOT_FOUND` | 404 | Resource not found |
| `CONFLICT` | 409 | Resource conflict |
| `QUOTA_EXCEEDED` | 413 | Storage quota exceeded |
| `INTERNAL_ERROR` | 500 | Server error |

---

## Rate Limiting

- API Key requests: 100 requests/60 seconds per key (configurable per key)
- No rate limiting on JWT-authenticated requests (recommend adding)

---

## Pagination

Paginated endpoints return:
```json
{
  "success": true,
  "code": "200",
  "message": "OK",
  "data": {
    "items": [...],
    "total": 100,
    "page": 0,
    "size": 20
  }
}
```

Query params: `page` (0-indexed), `size` (1-100, default 20)
