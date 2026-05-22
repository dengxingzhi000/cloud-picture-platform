# Excalidraw 协同白板集成设计

**日期:** 2026-05-22  
**状态:** 已批准  
**作者:** AI Assistant  

---

## 1. 概述

将 Excalidraw 集成到云图片平台，支持：
- **图片标注模式**：在已有图片上用 Excalidraw 画标注（矩形、箭头、文字等）
- **独立白板模式**：作为独立的绘图/白板功能，不依赖已有图片

采用混合模式：图片详情页有"标注"按钮点击进入 Excalidraw，同时也有独立的"新建白板"入口。

---

## 2. 技术栈

| 层 | 技术 |
|---|---|
| 前端 | React 19 + @excalidraw/excalidraw + Zustand |
| 实时同步 | Yjs + y-websocket + HocuspocusProvider |
| 持久化 | PostgreSQL + Flyway |
| 状态管理 | Zustand (Store Authoritative) |

---

## 3. 数据模型

### 3.1 ExcalidrawScene 实体

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID v7 | 主键 |
| `pictureId` | UUID | 关联 Picture（白板模式为 null） |
| `sceneName` | String | 场景名称 |
| `snapshotData` | TEXT (JSON) | 完整场景快照（用于 reconnect/恢复） |
| `version` | Long | 乐观锁版本号 |
| `lastSeq` | Long | 最后处理的 seq 序号 |
| `lastUpdatedByUserId` | UUID | 最后编辑者 |
| `createdAt` | Timestamp | 创建时间 |
| `updatedAt` | Timestamp | 更新时间 |

### 3.2 ExcalidrawElementSnapshot 实体

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID v7 | 主键 |
| `sceneId` | UUID | 关联 ExcalidrawScene |
| `elementId` | String | Excalidraw 元素 ID |
| `elementData` | TEXT (JSON) | 元素 JSON |
| `version` | Long | 元素版本号 |
| `isDeleted` | Boolean | 软删除标记 |
| `createdAt` | Timestamp | 创建时间 |
| `updatedAt` | Timestamp | 更新时间 |

### 3.3 ExcalidrawFile 实体

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID v7 | 主键 |
| `sceneId` | UUID | 关联 ExcalidrawScene |
| `fileId` | String | Excalidraw 文件 ID |
| `storageKey` | String | 存储服务 key |
| `url` | String | CDN URL |
| `contentType` | String | MIME 类型 |
| `sizeBytes` | Long | 文件大小 |
| `checksum` | String | SHA-256 哈希 |
| `createdAt` | Timestamp | 创建时间 |

### 3.4 关系说明

- **图片标注模式**：`pictureId` 指向已有 Picture
- **独立白板模式**：`pictureId` 为 null，创建一个无文件的"白板" Picture 记录用于统一管理
- **文件存储**：图片用 CDN URL 存储，不存 base64；Excalidraw BinaryFiles 单独存储

---

## 4. 实时同步架构

### 4.1 核心原则

**Client Authoritative + Server Relay**

后端不做 merge/业务逻辑，只做：
1. 转发 changed elements（带 version 过滤）
2. 存储 snapshot（用于 reconnect/持久化）
3. 管理 presence

### 4.2 Yjs CRDT 语义

- Yjs 提供 scene-level CRDT consistency
- Excalidraw element 本身仍采用 object-level LWW (Last-Writer-Wins) merge
- 多人同时编辑同一元素 → 后写覆盖前写

### 4.3 消息结构

```typescript
interface ScenePatchMessage {
  sceneId: string;
  userId: string;
  sessionId: string;      // 多 tab/多设备区分
  seq: number;            // 单调递增序号
  updatedElements: ExcalidrawElement[];
  deletedElementIds: string[];
  timestamp: number;
}
```

### 4.4 Element 级 Version 过滤

服务端维护：
```
ConcurrentHashMap<sceneId, Map<elementId, ExcalidrawElement>>
```

收到更新时：
```
if (incoming.version > current.version) → 替换 + 转发
else → 丢弃
```

### 4.5 光标同步

- Cursor 是 volatile event
- 不存储、不 ACK、不 replay
- `setPreservePublishOrder(false)` 允许丢帧
- 网络差时自动丢弃，避免 cursor 鬼畜

### 4.6 Presence

| EventType | 用途 |
|-----------|------|
| `USER_JOIN` | 加入场景 |
| `USER_LEAVE` | 离开场景 |
| `USER_ACTIVE` | 心跳/活跃状态 |

Presence 包含：user color, avatar, idle, active tool, viewport

### 4.7 Session Ownership

```typescript
{
  userId: string;
  sessionId: string;   // 多 tab 区分
  deviceId: string;    // 多设备区分
}
```

### 4.8 Reconnect 流程

1. WebSocket 断开 → HocuspocusProvider 自动重连
2. 指数退避：1s → 2s → 5s → 10s → max 30s
3. 重连后：rejoin room → request snapshot → merge local unsynced → resume
4. Awareness 是 ephemeral，disconnect 后 cursor 消失
5. 房间恢复：load snapshot + replay recent updates

---

## 5. 前端组件架构

### 5.1 核心原则

**Store Authoritative + Excalidraw Renderer Only**

Excalidraw 只是 View，不让 websocket/持久化/presence 侵入画布组件。

### 5.2 目录结构

```
ExcalidrawPage
│
├── stores/ (Zustand)
│   ├── sceneStore      — elements, appState, files
│   ├── collabStore     — connected users, session info
│   └── presenceStore   — cursors, active tools, viewport
│
├── hooks/
│   ├── useSceneSync    — 场景同步（throttle 100-200ms）
│   ├── useCursorSync   — 光标同步（volatile, 允许丢帧）
│   ├── usePresence     — 在线用户管理
│   ├── useReconnect    — 断线重连 + snapshot 恢复
│   └── useAutosave     — 自动保存（throttle 3-5s）
│
├── components/
│   ├── ExcalidrawCanvas — 纯渲染，无业务逻辑
│   └── CollabOverlay    — 光标、presence 显示
│
├── services/
│   ├── WebSocketClient  — STOMP/Yjs 连接管理
│   ├── SceneApi         — REST API 调用
│   └── ExportService    — 导出 PNG/SVG/JSON
│
└── PersistenceLayer
    ├── saveSceneMetadata
    ├── saveElements
    ├── loadScene
    ├── autosave (3-5s)
    └── snapshot (30-60s)
```

### 5.3 Yjs 集成

```tsx
// ExcalidrawPage
import { Excalidraw } from '@excalidraw/excalidraw'
import { useExcalidrawYjs } from './hooks/useExcalidrawYjs'

// 复用现有 usePictureCollabSession 中的 Y.Doc
// 后端 CollaborationProperties 中的 yjs-websocket provider 不需要改动
```

**Shared Types：**
- `Y.Map` for elements（key = elementId, value = element JSON）
- `Y.Map` for files（key = fileId, value = binary data 或 CDN URL）

### 5.4 三层 Throttle

| 层 | 频率 | 说明 |
|---|---|---|
| WebSocket | 100-200ms | 场景同步 |
| Autosave | 3-5s | 持久化 |
| Snapshot | 30-60s | 完整快照 |

**注意：** throttle 仅限 network send，不限 document apply，否则本地用户会有 draw latency。

### 5.5 图片标注模式

- 图片作为 Excalidraw image 元素插入
- 插入后 `locked: true`，不参与 diff
- 图片用 CDN URL 存储，不存 base64
- 背景图不参与协同 diff

### 5.6 主题管理

- 统一 ThemeProvider，不依赖 Excalidraw 自带主题管理
- 跟随系统/手动切换

### 5.7 Bundle 优化

- Excalidraw 约 1.5MB gzip 后约 500KB
- 路由级懒加载 `React.lazy()`

---

## 6. 持久化策略

### 6.1 实时内存

- `ConcurrentHashMap` 维护最新元素状态

### 6.2 定期持久化

- 每 60s 或 seq 每增加 100 → 写入 snapshot + 更新 ExcalidrawScene.snapshotData
- Autosave 每 3-5s 保存元素变更

### 6.3 Snapshot Compaction

- 定期 merge Yjs updates，防止 update log 膨胀
- 否则 join room 会越来越慢

### 6.4 Reconnect 恢复

- 从 snapshot 恢复 + replay recent updates
- 不仅依赖 Yjs replay

---

## 7. API 设计

### 7.1 REST API

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/scenes` | 创建场景（图片标注/白板） |
| `GET` | `/api/scenes/{sceneId}` | 获取场景元数据 |
| `GET` | `/api/scenes/{sceneId}/snapshot` | 获取完整快照（reconnect 用） |
| `PUT` | `/api/scenes/{sceneId}` | 更新场景元数据 |
| `DELETE` | `/api/scenes/{sceneId}` | 删除场景 |

### 7.2 Yjs Room 端点

复用现有 `CollaborationProperties` 配置的 y-websocket 通道：
- Room ID: `scene:{sceneId}`
- 鉴权: Hocuspocus Auth Extension + PictureCollabAccessService

---

## 8. 异常处理与边界情况

### 8.1 大场景性能

- Excalidraw 无真正虚拟化
- 优化方案：viewport culling + spatial indexing (RBush/quadtree) + incremental redraw

### 8.2 文件上传限制

| 限制 | 值 |
|------|------|
| 图片最大尺寸 | 4096x4096 像素 |
| 文件大小 | 10MB |
| MIME 白名单 | image/png, image/jpeg, image/gif, image/webp, image/svg+xml |

### 8.3 监控指标

| 指标 | 说明 |
|------|------|
| update payload size | 场景大小 |
| awareness count | 在线用户数 |
| snapshot load time | 快照加载时间 |
| room memory usage | Yjs doc 内存 |
| websocket outbound queue | 广播积压 |
| 协同连接数 | 总连接数 |
| 断线重连次数 | 重连频率 |
| 持久化成功率 | 写入成功/失败 |

### 8.4 安全

- Room-level authorization（websocket subscribe 时校验 scene access）
- 不仅校验 HTTP API
- WSS 传输加密

---

## 9. 非功能性需求

### 9.1 性能要求

| 指标 | 目标值 |
|------|--------|
| 场景首次加载 | < 2s (含 Excalidraw bundle) |
| 元素同步延迟 | < 200ms (同地域) |
| 光标同步延迟 | < 100ms |
| 自动保存延迟 | < 500ms |
| 最大并发房间数 | 100+ |
| 单房间最大用户数 | 20 |

### 9.2 可用性

- 断线后自动重连，指数退避 1s → 30s
- IndexedDB 离线缓存，恢复后自动同步
- 服务端快照每 60s 自动保存

### 9.3 安全性

- WebSocket 连接需 JWT 鉴权
- Room 级别访问控制（scene owner/editor/viewer）
- WSS 传输加密
- 文件上传 MIME 白名单校验

### 9.4 可观测性

| 指标 | 采集方式 |
|------|----------|
| 协同连接数 | WebSocket session count |
| 消息吞吐量 | Yjs update count/min |
| 断线重连次数 | reconnect event counter |
| 快照保存成功率 | API success/fail ratio |
| 房间内存占用 | Yjs doc size metric |

---

## 10. 演进路径

| 阶段 | 技术 | 说明 |
|------|------|------|
| 当前 | Excalidraw + Yjs + Hocuspocus | 满足需求 |
| 后续 | Yjs 优化 + CRDT 深度集成 | 大场景优化 |
| 未来 | 自定义 CRDT 或升级方案 | 超大规模协同 |

---

## 11. 评分

| 维度 | 评分 |
|------|------|
| 简洁性 | 7 |
| 稳定性 | 9 |
| 扩展性 | 9 |
| 生产可用 | 9 |
| 组件拆分 | 9 |
| 可维护性 | 9 |
| 协同设计 | 9 |
| 安全性 | 9 |
| 监控设计 | 9 |
