# Initial ER Model

The diagram focuses on P1 functionality: identity, personal space, and picture assets.

```mermaid
erDiagram
  app_user ||--|| space : owns
  app_user ||--o{ picture_asset : uploads
  app_user ||--o{ moderation_record : reviews
  space ||--o{ picture_asset : contains
  picture_asset ||--o{ moderation_record : audited

  app_user {
    uuid id PK
    varchar username
    varchar email
    varchar password_hash
    varchar display_name
    varchar status
    varchar role
    timestamp created_at
    timestamp updated_at
  }

  space {
    uuid id PK
    uuid owner_id FK
    varchar type
    varchar name
    bigint quota_bytes
    bigint used_bytes
    timestamp created_at
    timestamp updated_at
  }

  picture_asset {
    uuid id PK
    uuid owner_id FK
    uuid space_id FK
    varchar visibility
    varchar review_status
    varchar name
    varchar original_filename
    varchar content_type
    bigint size_bytes
    varchar checksum
    varchar storage_key
    varchar url
    int width
    int height
    timestamp created_at
    timestamp updated_at
  }

  moderation_record {
    uuid id PK
    uuid picture_id FK
    uuid reviewer_id FK
    varchar from_status
    varchar to_status
    varchar reason
    timestamp reviewed_at
    timestamp created_at
    timestamp updated_at
  }
```

## Table Notes
- `app_user.username` and `app_user.email` are unique.
- `app_user.role` uses `USER` or `ADMIN`.
- `space` defaults to a personal space created on registration.
- `picture_asset.review_status` is `PENDING` for public uploads.
- `picture_asset.visibility` supports `PUBLIC`, `PRIVATE`, `TEAM`.
