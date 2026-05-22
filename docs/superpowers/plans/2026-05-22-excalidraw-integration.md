# Excalidraw 协同白板集成实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 集成 Excalidraw 到云图片平台，支持图片标注和独立白板，基于 Yjs 实现实时协同

**Architecture:** 前端使用 @excalidraw/excalidraw 组件 + Zustand 状态管理 + Yjs CRDT 同步；后端新增 ExcalidrawScene 实体 + REST API，复用现有 Yjs/Hocuspocus 基础设施

**Tech Stack:** React 19, @excalidraw/excalidraw, Zustand, Yjs, HocuspocusProvider, Spring Boot, PostgreSQL

---

## 文件结构

### 后端 (cloud-picture-platform)

| 文件 | 职责 |
|------|------|
| `domain/excalidraw/ExcalidrawScene.java` | ExcalidrawScene JPA 实体 |
| `domain/excalidraw/ExcalidrawFile.java` | ExcalidrawFile JPA 实体 |
| `infrastructure/persistence/excalidraw/ExcalidrawSceneRepository.java` | 场景 Repository |
| `infrastructure/persistence/excalidraw/ExcalidrawFileRepository.java` | 文件 Repository |
| `application/excalidraw/ExcalidrawSceneService.java` | 场景业务逻辑 |
| `interfaces/excalidraw/ExcalidrawSceneController.java` | REST API |
| `interfaces/excalidraw/dto/ExcalidrawSceneResponse.java` | 响应 DTO |
| `interfaces/excalidraw/dto/CreateExcalidrawSceneRequest.java` | 创建请求 DTO |
| `interfaces/excalidraw/dto/UpdateSnapshotRequest.java` | 快照更新请求 DTO |
| `src/main/resources/db/migration/V28__create_excalidraw_tables.sql` | Flyway 迁移 |

### 前端 (cloud-picture-platform-web)

| 文件 | 职责 |
|------|------|
| `src/react-app/pages/ExcalidrawPage.tsx` | Excalidraw 编辑器页面 |
| `src/react-app/hooks/useExcalidrawYjs.ts` | Yjs 集成 hook |
| `src/react-app/hooks/useExcalidrawReconnect.ts` | 断线重连 hook |
| `src/react-app/hooks/useExcalidrawAutosave.ts` | 自动保存 hook |
| `src/react-app/api/scenes.ts` | 场景 API 客户端 |
| `src/react-app/collab/excalidrawDocument.ts` | Excalidraw Yjs 文档助手 |

---

## Task 1: 后端 - ExcalidrawScene 实体与迁移

**Files:**
- Create: `src/main/java/com/cn/cloudpictureplatform/domain/excalidraw/ExcalidrawScene.java`
- Create: `src/main/java/com/cn/cloudpictureplatform/domain/excalidraw/ExcalidrawFile.java`
- Create: `src/main/resources/db/migration/V28__create_excalidraw_tables.sql`

- [ ] **Step 1: 创建 Flyway 迁移**

```sql
-- V28__create_excalidraw_tables.sql
CREATE TABLE excalidraw_scene (
    id UUID PRIMARY KEY,
    picture_id UUID REFERENCES picture(id),
    scene_name VARCHAR(255) NOT NULL,
    snapshot_data TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    last_seq BIGINT NOT NULL DEFAULT 0,
    last_updated_by_user_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_excalidraw_scene_picture_id ON excalidraw_scene(picture_id);
CREATE INDEX idx_excalidraw_scene_updated_at ON excalidraw_scene(updated_at);

CREATE TABLE excalidraw_file (
    id UUID PRIMARY KEY,
    scene_id UUID NOT NULL REFERENCES excalidraw_scene(id) ON DELETE CASCADE,
    file_id VARCHAR(255) NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    url VARCHAR(1024) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(scene_id, file_id)
);

CREATE INDEX idx_excalidraw_file_scene_id ON excalidraw_file(scene_id);
```

- [ ] **Step 2: 创建 ExcalidrawScene 实体**

```java
package com.cn.cloudpictureplatform.domain.excalidraw;

import com.cn.cloudpictureplatform.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "excalidraw_scene")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExcalidrawScene extends BaseEntity {

    @Column(name = "picture_id")
    private java.util.UUID pictureId;

    @Column(name = "scene_name", nullable = false)
    private String sceneName;

    @Column(name = "snapshot_data", columnDefinition = "TEXT")
    private String snapshotData;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "last_seq", nullable = false)
    @Builder.Default
    private Long lastSeq = 0L;

    @Column(name = "last_updated_by_user_id")
    private java.util.UUID lastUpdatedByUserId;
}
```

- [ ] **Step 3: 创建 ExcalidrawFile 实体**

```java
package com.cn.cloudpictureplatform.domain.excalidraw;

import com.cn.cloudpictureplatform.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "excalidraw_file", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"scene_id", "file_id"})
})
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExcalidrawFile extends BaseEntity {

    @Column(name = "scene_id", nullable = false)
    private java.util.UUID sceneId;

    @Column(name = "file_id", nullable = false)
    private String fileId;

    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Column(name = "url", nullable = false, length = 1024)
    private String url;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "checksum", nullable = false, length = 64)
    private String checksum;
}
```

- [ ] **Step 4: 编译验证**

Run: `$env:JAVA_HOME = "$env:USERPROFILE\.jdks\graalvm-jdk-21.0.8"; .\mvnw compile`

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cn/cloudpictureplatform/domain/excalidraw/ src/main/resources/db/migration/V28__create_excalidraw_tables.sql
git commit -m "feat: add ExcalidrawScene and ExcalidrawFile entities with Flyway migration"
```

---

## Task 2: 后端 - Repository 层

**Files:**
- Create: `src/main/java/com/cn/cloudpictureplatform/infrastructure/persistence/excalidraw/ExcalidrawSceneRepository.java`
- Create: `src/main/java/com/cn/cloudpictureplatform/infrastructure/persistence/excalidraw/ExcalidrawFileRepository.java`

- [ ] **Step 1: 创建 ExcalidrawSceneRepository**

```java
package com.cn.cloudpictureplatform.infrastructure.persistence.excalidraw;

import com.cn.cloudpictureplatform.domain.excalidraw.ExcalidrawScene;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExcalidrawSceneRepository extends JpaRepository<ExcalidrawScene, UUID> {

    Optional<ExcalidrawScene> findByPictureId(UUID pictureId);

    List<ExcalidrawScene> findByPictureIdIsNullOrderByUpdatedAtDesc();

    @Modifying
    @Query("UPDATE ExcalidrawScene s SET s.snapshotData = :snapshotData, s.lastSeq = :lastSeq, s.lastUpdatedByUserId = :userId WHERE s.id = :id")
    int updateSnapshot(@Param("id") UUID id, @Param("snapshotData") String snapshotData, @Param("lastSeq") Long lastSeq, @Param("userId") UUID userId);
}
```

- [ ] **Step 2: 创建 ExcalidrawFileRepository**

```java
package com.cn.cloudpictureplatform.infrastructure.persistence.excalidraw;

import com.cn.cloudpictureplatform.domain.excalidraw.ExcalidrawFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExcalidrawFileRepository extends JpaRepository<ExcalidrawFile, UUID> {

    List<ExcalidrawFile> findBySceneId(UUID sceneId);

    Optional<ExcalidrawFile> findBySceneIdAndFileId(UUID sceneId, String fileId);

    void deleteBySceneId(UUID sceneId);
}
```

- [ ] **Step 3: 编译验证**

Run: `$env:JAVA_HOME = "$env:USERPROFILE\.jdks\graalvm-jdk-21.0.8"; .\mvnw compile`

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/cn/cloudpictureplatform/infrastructure/persistence/excalidraw/
git commit -m "feat: add ExcalidrawScene and ExcalidrawFile repositories"
```

---

## Task 3: 后端 - Service 与 Controller

**Files:**
- Create: `src/main/java/com/cn/cloudpictureplatform/application/excalidraw/ExcalidrawSceneService.java`
- Create: `src/main/java/com/cn/cloudpictureplatform/interfaces/excalidraw/ExcalidrawSceneController.java`
- Create: `src/main/java/com/cn/cloudpictureplatform/interfaces/excalidraw/dto/ExcalidrawSceneResponse.java`
- Create: `src/main/java/com/cn/cloudpictureplatform/interfaces/excalidraw/dto/CreateExcalidrawSceneRequest.java`
- Create: `src/main/java/com/cn/cloudpictureplatform/interfaces/excalidraw/dto/UpdateSnapshotRequest.java`

- [ ] **Step 1: 创建 DTO**

```java
// ExcalidrawSceneResponse.java
package com.cn.cloudpictureplatform.interfaces.excalidraw.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ExcalidrawSceneResponse {
    private UUID id;
    private UUID pictureId;
    private String sceneName;
    private String snapshotData;
    private Long version;
    private Long lastSeq;
    private UUID lastUpdatedByUserId;
    private Instant createdAt;
    private Instant updatedAt;
}
```

```java
// CreateExcalidrawSceneRequest.java
package com.cn.cloudpictureplatform.interfaces.excalidraw.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class CreateExcalidrawSceneRequest {
    private UUID pictureId;
    private String sceneName;
}
```

```java
// UpdateSnapshotRequest.java
package com.cn.cloudpictureplatform.interfaces.excalidraw.dto;

import lombok.Data;

@Data
public class UpdateSnapshotRequest {
    private String snapshotData;
}
```

- [ ] **Step 2: 创建 ExcalidrawSceneService**

```java
package com.cn.cloudpictureplatform.application.excalidraw;

import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.common.exception.ApiErrorCode;
import com.cn.cloudpictureplatform.domain.excalidraw.ExcalidrawScene;
import com.cn.cloudpictureplatform.domain.picture.Picture;
import com.cn.cloudpictureplatform.infrastructure.persistence.excalidraw.ExcalidrawSceneRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.excalidraw.ExcalidrawFileRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.picture.PictureRepository;
import com.cn.cloudpictureplatform.interfaces.excalidraw.dto.CreateExcalidrawSceneRequest;
import com.cn.cloudpictureplatform.interfaces.excalidraw.dto.ExcalidrawSceneResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExcalidrawSceneService {

    private final ExcalidrawSceneRepository sceneRepository;
    private final ExcalidrawFileRepository fileRepository;
    private final PictureRepository pictureRepository;

    @Transactional
    public ExcalidrawSceneResponse createScene(CreateExcalidrawSceneRequest request, UUID userId) {
        UUID pictureId = request.getPictureId();

        // Whiteboard mode: create a placeholder Picture record for unified management
        if (pictureId == null) {
            Picture whiteboardPicture = Picture.builder()
                    .userId(userId)
                    .fileName(request.getSceneName() != null ? request.getSceneName() : "Untitled Whiteboard")
                    .fileSize(0L)
                    .storageKey("whiteboard:" + UUID.randomUUID())
                    .contentType("application/x-excalidraw")
                    .status(Picture.Status.ACTIVE)
                    .build();
            pictureId = pictureRepository.save(whiteboardPicture).getId();
        }

        ExcalidrawScene scene = ExcalidrawScene.builder()
                .pictureId(pictureId)
                .sceneName(request.getSceneName() != null ? request.getSceneName() : "Untitled")
                .lastUpdatedByUserId(userId)
                .build();
        scene = sceneRepository.save(scene);
        return toResponse(scene);
    }

    @Transactional(readOnly = true)
    public ExcalidrawSceneResponse getScene(UUID sceneId) {
        ExcalidrawScene scene = sceneRepository.findById(sceneId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "Scene not found"));
        return toResponse(scene);
    }

    @Transactional(readOnly = true)
    public ExcalidrawSceneResponse getSceneByPictureId(UUID pictureId) {
        ExcalidrawScene scene = sceneRepository.findByPictureId(pictureId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "Scene not found for picture"));
        return toResponse(scene);
    }

    @Transactional
    public ExcalidrawSceneResponse updateSnapshot(UUID sceneId, String snapshotData, Long lastSeq, UUID userId) {
        ExcalidrawScene scene = sceneRepository.findById(sceneId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "Scene not found"));
        scene.setSnapshotData(snapshotData);
        scene.setLastSeq(lastSeq);
        scene.setLastUpdatedByUserId(userId);
        scene = sceneRepository.save(scene);
        return toResponse(scene);
    }

    @Transactional
    public void deleteScene(UUID sceneId) {
        fileRepository.deleteBySceneId(sceneId);
        sceneRepository.deleteById(sceneId);
    }

    private ExcalidrawSceneResponse toResponse(ExcalidrawScene scene) {
        return ExcalidrawSceneResponse.builder()
                .id(scene.getId())
                .pictureId(scene.getPictureId())
                .sceneName(scene.getSceneName())
                .snapshotData(scene.getSnapshotData())
                .version(scene.getVersion())
                .lastSeq(scene.getLastSeq())
                .lastUpdatedByUserId(scene.getLastUpdatedByUserId())
                .createdAt(scene.getCreatedAt())
                .updatedAt(scene.getUpdatedAt())
                .build();
    }
}
```

- [ ] **Step 3: 创建 ExcalidrawSceneController**

```java
package com.cn.cloudpictureplatform.interfaces.excalidraw;

import com.cn.cloudpictureplatform.application.excalidraw.ExcalidrawSceneService;
import com.cn.cloudpictureplatform.common.model.ApiResponse;
import com.cn.cloudpictureplatform.infrastructure.security.JwtAuthenticationToken;
import com.cn.cloudpictureplatform.interfaces.excalidraw.dto.CreateExcalidrawSceneRequest;
import com.cn.cloudpictureplatform.interfaces.excalidraw.dto.ExcalidrawSceneResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/scenes")
@RequiredArgsConstructor
public class ExcalidrawSceneController {

    private final ExcalidrawSceneService sceneService;

    @PostMapping
    public ApiResponse<ExcalidrawSceneResponse> createScene(
            @RequestBody CreateExcalidrawSceneRequest request,
            @AuthenticationPrincipal JwtAuthenticationToken principal) {
        UUID userId = principal.getUserId();
        return ApiResponse.ok(sceneService.createScene(request, userId));
    }

    @GetMapping("/{sceneId}")
    public ApiResponse<ExcalidrawSceneResponse> getScene(@PathVariable UUID sceneId) {
        return ApiResponse.ok(sceneService.getScene(sceneId));
    }

    @GetMapping("/{sceneId}/snapshot")
    public ApiResponse<ExcalidrawSceneResponse> getSnapshot(@PathVariable UUID sceneId) {
        return ApiResponse.ok(sceneService.getScene(sceneId));
    }

    @GetMapping("/by-picture/{pictureId}")
    public ApiResponse<ExcalidrawSceneResponse> getSceneByPictureId(@PathVariable UUID pictureId) {
        return ApiResponse.ok(sceneService.getSceneByPictureId(pictureId));
    }

    @DeleteMapping("/{sceneId}")
    public ApiResponse<Void> deleteScene(@PathVariable UUID sceneId) {
        sceneService.deleteScene(sceneId);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{sceneId}/snapshot")
    public ApiResponse<ExcalidrawSceneResponse> updateSnapshot(
            @PathVariable UUID sceneId,
            @RequestBody UpdateSnapshotRequest request,
            @AuthenticationPrincipal JwtAuthenticationToken principal) {
        UUID userId = principal.getUserId();
        return ApiResponse.ok(sceneService.updateSnapshot(sceneId, request.getSnapshotData(), null, userId));
    }
}
```

- [ ] **Step 4: 编译验证**

Run: `$env:JAVA_HOME = "$env:USERPROFILE\.jdks\graalvm-jdk-21.0.8"; .\mvnw compile`

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cn/cloudpictureplatform/application/excalidraw/ src/main/java/com/cn/cloudpictureplatform/interfaces/excalidraw/
git commit -m "feat: add ExcalidrawScene service and REST controller"
```

---

## Task 4: 前端 - 安装 Excalidraw 依赖

**Files:**
- Modify: `cloud-picture-platform-web/package.json`

- [ ] **Step 1: 安装 @excalidraw/excalidraw 和 zustand**

Run:
```bash
cd D:\ProgramProject\PolymerizationProject\cloud-picture-platform-web
npm install @excalidraw/excalidraw zustand
```

Expected: package.json 新增 `@excalidraw/excalidraw` 和 `zustand` 依赖

- [ ] **Step 2: 验证安装**

Run: `npm ls @excalidraw/excalidraw zustand`

Expected: 显示已安装版本

- [ ] **Step 3: Commit**

```bash
git add package.json package-lock.json
git commit -m "feat: add @excalidraw/excalidraw and zustand dependencies"
```

---

## Task 5: 前端 - 场景 API 客户端

**Files:**
- Create: `src/react-app/api/scenes.ts`

- [ ] **Step 1: 创建场景 API**

```typescript
import { client, unwrap } from './client';

export interface ExcalidrawSceneResponse {
  id: string;
  pictureId: string | null;
  sceneName: string;
  snapshotData: string | null;
  version: number;
  lastSeq: number;
  lastUpdatedByUserId: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateExcalidrawSceneRequest {
  pictureId?: string;
  sceneName?: string;
}

export async function createScene(req: CreateExcalidrawSceneRequest): Promise<ExcalidrawSceneResponse> {
  return unwrap(client.post<ExcalidrawSceneResponse>('/api/scenes', req));
}

export async function getScene(sceneId: string): Promise<ExcalidrawSceneResponse> {
  return unwrap(client.get<ExcalidrawSceneResponse>(`/api/scenes/${sceneId}`));
}

export async function getSceneByPictureId(pictureId: string): Promise<ExcalidrawSceneResponse> {
  return unwrap(client.get<ExcalidrawSceneResponse>(`/api/scenes/by-picture/${pictureId}`));
}

export async function getSnapshot(sceneId: string): Promise<ExcalidrawSceneResponse> {
  return unwrap(client.get<ExcalidrawSceneResponse>(`/api/scenes/${sceneId}/snapshot`));
}

export async function deleteScene(sceneId: string): Promise<void> {
  return unwrap(client.delete(`/api/scenes/${sceneId}`));
}

export async function updateSnapshot(sceneId: string, snapshotData: string): Promise<ExcalidrawSceneResponse> {
  return unwrap(client.put<ExcalidrawSceneResponse>(`/api/scenes/${sceneId}/snapshot`, { snapshotData }));
}
```

- [ ] **Step 2: TypeScript 编译验证**

Run: `npx tsc --noEmit`

Expected: 无错误

- [ ] **Step 3: Commit**

```bash
git add src/react-app/api/scenes.ts
git commit -m "feat: add Excalidraw scene API client"
```

---

## Task 6: 前端 - Excalidraw Yjs 集成 Hook

**Files:**
- Create: `src/react-app/collab/excalidrawDocument.ts`
- Create: `src/react-app/hooks/useExcalidrawYjs.ts`

- [ ] **Step 1: 创建 Excalidraw Yjs 文档助手**

```typescript
// src/react-app/collab/excalidrawDocument.ts
import * as Y from 'yjs';

export const ELEMENTS_KEY = 'excalidraw-elements';
export const FILES_KEY = 'excalidraw-files';

export function readExcalidrawElements(doc: Y.Doc): Map<string, any> {
  const yMap = doc.getMap(ELEMENTS_KEY);
  const elements = new Map<string, any>();
  yMap.forEach((value, key) => {
    elements.set(key, value);
  });
  return elements;
}

export function writeExcalidrawElements(doc: Y.Doc, elements: any[]): void {
  const yMap = doc.getMap(ELEMENTS_KEY);
  doc.transact(() => {
    const existingIds = new Set(elements.map(el => el.id));
    // Remove deleted elements
    yMap.forEach((_, key) => {
      if (!existingIds.has(key)) {
        yMap.delete(key);
      }
    });
    // Upsert elements
    elements.forEach(el => {
      yMap.set(el.id, el);
    });
  });
}

export function readExcalidrawFiles(doc: Y.Doc): Map<string, any> {
  const yMap = doc.getMap(FILES_KEY);
  const files = new Map<string, any>();
  yMap.forEach((value, key) => {
    files.set(key, value);
  });
  return files;
}

export function writeExcalidrawFile(doc: Y.Doc, fileId: string, fileData: any): void {
  const yMap = doc.getMap(FILES_KEY);
  yMap.set(fileId, fileData);
}

export function isExcalidrawDocEmpty(doc: Y.Doc): boolean {
  const yMap = doc.getMap(ELEMENTS_KEY);
  return yMap.size === 0;
}
```

- [ ] **Step 2: 创建 useExcalidrawYjs Hook**

```typescript
// src/react-app/hooks/useExcalidrawYjs.ts
import { useEffect, useRef, useState, useCallback } from 'react';
import * as Y from 'yjs';
import { HocuspocusProvider } from '@hocuspocus/provider';
import { IndexeddbPersistence } from 'y-indexeddb';
import { readExcalidrawElements, writeExcalidrawElements, readExcalidrawFiles, writeExcalidrawFile, isExcalidrawDocEmpty } from '../collab/excalidrawDocument';
import { getSnapshot } from '../api/scenes';

interface UseExcalidrawYjsOptions {
  sceneId: string;
  roomUrl: string;
  token: string;
  enabled?: boolean;
}

interface UseExcalidrawYjsReturn {
  yDoc: Y.Doc | null;
  provider: HocuspocusProvider | null;
  connected: boolean;
  synced: boolean;
  elements: any[];
  files: Map<string, any>;
  updateElements: (elements: any[]) => void;
  updateFile: (fileId: string, fileData: any) => void;
}

export function useExcalidrawYjs({ sceneId, roomUrl, token, enabled = true }: UseExcalidrawYjsOptions): UseExcalidrawYjsReturn {
  const [connected, setConnected] = useState(false);
  const [synced, setSynced] = useState(false);
  const [elements, setElements] = useState<any[]>([]);
  const [files, setFiles] = useState<Map<string, any>>(new Map());
  const yDocRef = useRef<Y.Doc | null>(null);
  const providerRef = useRef<HocuspocusProvider | null>(null);
  const indexeddbRef = useRef<IndexeddbPersistence | null>(null);

  useEffect(() => {
    if (!enabled || !sceneId || !roomUrl) return;

    const doc = new Y.Doc();
    yDocRef.current = doc;

    // IndexedDB persistence for offline support
    const indexeddb = new IndexeddbPersistence(`excalidraw-${sceneId}`, doc);
    indexeddbRef.current = indexeddb;

    // Hocuspocus provider
    const provider = new HocuspocusProvider({
      url: roomUrl,
      name: `scene:${sceneId}`,
      token,
      document: doc,
      onConnect: () => setConnected(true),
      onDisconnect: () => setConnected(false),
      onSynced: () => setSynced(true),
    });
    providerRef.current = provider;

    // Listen for element changes
    const yElements = doc.getMap('excalidraw-elements');
    const elementsObserver = () => {
      const newElements: any[] = [];
      yElements.forEach((value) => {
        newElements.push(value);
      });
      setElements(newElements);
    };
    yElements.observe(elementsObserver);

    // Listen for file changes
    const yFiles = doc.getMap('excalidraw-files');
    const filesObserver = () => {
      const newFiles = new Map<string, any>();
      yFiles.forEach((value, key) => {
        newFiles.set(key, value);
      });
      setFiles(newFiles);
    };
    yFiles.observe(filesObserver);

    // Seed from server snapshot if doc is empty
    const seedFromSnapshot = async () => {
      if (isExcalidrawDocEmpty(doc)) {
        try {
          const snapshot = await getSnapshot(sceneId);
          if (snapshot.snapshotData) {
            const data = JSON.parse(snapshot.snapshotData);
            if (data.elements) {
              writeExcalidrawElements(doc, data.elements);
            }
            if (data.files) {
              Object.entries(data.files).forEach(([key, value]) => {
                writeExcalidrawFile(doc, key, value);
              });
            }
          }
        } catch (err) {
          console.warn('Failed to seed from snapshot:', err);
        }
      }
    };
    seedFromSnapshot();

    return () => {
      yElements.unobserve(elementsObserver);
      yFiles.unobserve(filesObserver);
      provider.destroy();
      indexeddb.destroy();
      doc.destroy();
      yDocRef.current = null;
      providerRef.current = null;
      indexeddbRef.current = null;
      setConnected(false);
      setSynced(false);
    };
  }, [sceneId, roomUrl, token, enabled]);

  const updateElements = useCallback((newElements: any[]) => {
    if (yDocRef.current) {
      writeExcalidrawElements(yDocRef.current, newElements);
    }
  }, []);

  const updateFile = useCallback((fileId: string, fileData: any) => {
    if (yDocRef.current) {
      writeExcalidrawFile(yDocRef.current, fileId, fileData);
    }
  }, []);

  return {
    yDoc: yDocRef.current,
    provider: providerRef.current,
    connected,
    synced,
    elements,
    files,
    updateElements,
    updateFile,
  };
}
```

- [ ] **Step 3: TypeScript 编译验证**

Run: `npx tsc --noEmit`

Expected: 无错误

- [ ] **Step 4: Commit**

```bash
git add src/react-app/collab/excalidrawDocument.ts src/react-app/hooks/useExcalidrawYjs.ts
git commit -m "feat: add Excalidraw Yjs integration hook with IndexedDB persistence"
```

---

## Task 7: 前端 - Excalidraw 页面组件

**Files:**
- Create: `src/react-app/pages/ExcalidrawPage.tsx`
- Modify: `src/react-app/App.tsx` (添加路由)

- [ ] **Step 1: 创建 ExcalidrawPage**

```tsx
// src/react-app/pages/ExcalidrawPage.tsx
import React, { useCallback, useEffect, useMemo, Suspense, lazy } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useExcalidrawYjs } from '../hooks/useExcalidrawYjs';
import { useAuth } from '../auth';
import { getScene, createScene } from '../api/scenes';

const Excalidraw = lazy(() =>
  import('@excalidraw/excalidraw').then(module => ({ default: module.Excalidraw }))
);

export default function ExcalidrawPage() {
  const { sceneId, pictureId } = useParams<{ sceneId?: string; pictureId?: string }>();
  const navigate = useNavigate();
  const { token } = useAuth();
  const [currentSceneId, setCurrentSceneId] = React.useState<string | null>(sceneId || null);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);

  // Resolve scene ID
  useEffect(() => {
    const resolveScene = async () => {
      try {
        if (sceneId) {
          setCurrentSceneId(sceneId);
        } else if (pictureId) {
          const scene = await getSceneByPictureId(pictureId);
          setCurrentSceneId(scene.id);
        } else {
          const scene = await createScene({ sceneName: 'Untitled Whiteboard' });
          setCurrentSceneId(scene.id);
          navigate(`/excalidraw/${scene.id}`, { replace: true });
        }
      } catch (err: any) {
        if (err?.response?.status === 404 && pictureId) {
          const scene = await createScene({ pictureId, sceneName: 'Image Annotation' });
          setCurrentSceneId(scene.id);
          navigate(`/excalidraw/${scene.id}`, { replace: true });
        } else {
          setError(err?.message || 'Failed to load scene');
        }
      } finally {
        setLoading(false);
      }
    };
    resolveScene();
  }, [sceneId, pictureId, navigate]);

  // Yjs integration
  const roomUrl = useMemo(() => {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${protocol}//${window.location.host}/ws`;
  }, []);

  const { connected, synced, elements, files, updateElements, updateFile } = useExcalidrawYjs({
    sceneId: currentSceneId || '',
    roomUrl,
    token: token || '',
    enabled: !!currentSceneId && !!token,
  });

  // Handle Excalidraw changes
  const handleChange = useCallback((excalidrawElements: any[], appState: any, excalidrawFiles: any) => {
    updateElements(excalidrawElements);
    if (excalidrawFiles) {
      Object.entries(excalidrawFiles).forEach(([key, value]) => {
        updateFile(key, value);
      });
    }
  }, [updateElements, updateFile]);

  if (loading) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh' }}>
        <span>Loading scene...</span>
      </div>
    );
  }

  if (error) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh', color: 'red' }}>
        <span>Error: {error}</span>
      </div>
    );
  }

  return (
    <div style={{ height: '100vh', display: 'flex', flexDirection: 'column' }}>
      {/* Status bar */}
      <div style={{ padding: '4px 12px', background: 'var(--bg-secondary, #f5f5f5)', borderBottom: '1px solid var(--border, #e0e0e0)', display: 'flex', gap: '12px', fontSize: '12px' }}>
        <span style={{ color: connected ? 'green' : 'red' }}>
          {connected ? '● Connected' : '○ Disconnected'}
        </span>
        <span style={{ color: synced ? 'green' : 'gray' }}>
          {synced ? '● Synced' : '○ Syncing...'}
        </span>
        <span style={{ color: 'gray' }}>
          {elements.length} elements
        </span>
      </div>

      {/* Excalidraw canvas */}
      <div style={{ flex: 1 }}>
        <Suspense fallback={
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%' }}>
            <span>Loading Excalidraw...</span>
          </div>
        }>
          <Excalidraw
            initialData={{ elements, files }}
            onChange={handleChange}
            isCollaborating={connected}
          />
        </Suspense>
      </div>
    </div>
  );
}

// Helper to get scene by picture ID
async function getSceneByPictureId(pictureId: string) {
  const { getSceneByPictureId: getScene } = await import('../api/scenes');
  return getScene(pictureId);
}
```

- [ ] **Step 2: 添加路由到 App.tsx**

在 App.tsx 的路由配置中添加：

```tsx
// 在 RequireAuth 内部添加
<Route path="excalidraw" element={<ExcalidrawPage />} />
<Route path="excalidraw/:sceneId" element={<ExcalidrawPage />} />
<Route path="pictures/:pictureId/excalidraw" element={<ExcalidrawPage />} />
```

注意：ExcalidrawPage 需要懒加载：
```tsx
const ExcalidrawPage = lazy(() => import('./pages/ExcalidrawPage'));
```

- [ ] **Step 3: TypeScript 编译验证**

Run: `npx tsc --noEmit`

Expected: 无错误

- [ ] **Step 4: Commit**

```bash
git add src/react-app/pages/ExcalidrawPage.tsx src/react-app/App.tsx
git commit -m "feat: add Excalidraw page with lazy loading and Yjs integration"
```

---

## Task 8: 前端 - 断线重连与自动保存

**Files:**
- Create: `src/react-app/hooks/useExcalidrawReconnect.ts`
- Create: `src/react-app/hooks/useExcalidrawAutosave.ts`

- [ ] **Step 1: 创建 useExcalidrawReconnect**

```typescript
// src/react-app/hooks/useExcalidrawReconnect.ts
import { useEffect, useRef, useCallback, useState } from 'react';

interface UseExcalidrawReconnectOptions {
  connected: boolean;
  synced: boolean;
  onReconnect: () => void;
}

export function useExcalidrawReconnect({ connected, synced, onReconnect }: UseExcalidrawReconnectOptions) {
  const [reconnectAttempts, setReconnectAttempts] = useState(0);
  const maxReconnectAttempts = 10;
  const baseDelay = 1000;
  const maxDelay = 30000;

  useEffect(() => {
    if (connected) {
      setReconnectAttempts(0);
    }
  }, [connected]);

  const scheduleReconnect = useCallback(() => {
    if (reconnectAttempts >= maxReconnectAttempts) return;

    const delay = Math.min(baseDelay * Math.pow(2, reconnectAttempts), maxDelay);
    const jitter = delay * 0.1 * Math.random();
    const totalDelay = delay + jitter;

    const timer = setTimeout(() => {
      setReconnectAttempts(prev => prev + 1);
      onReconnect();
    }, totalDelay);

    return () => clearTimeout(timer);
  }, [reconnectAttempts, onReconnect]);

  useEffect(() => {
    if (!connected && reconnectAttempts < maxReconnectAttempts) {
      return scheduleReconnect();
    }
  }, [connected, reconnectAttempts, scheduleReconnect]);

  return {
    reconnectAttempts,
    maxReconnectAttempts,
    isReconnecting: !connected && reconnectAttempts > 0 && reconnectAttempts < maxReconnectAttempts,
  };
}
```

- [ ] **Step 2: 创建 useExcalidrawAutosave**

```typescript
// src/react-app/hooks/useExcalidrawAutosave.ts
import { useEffect, useRef, useCallback } from 'react';
import { getSnapshot, updateSnapshot } from '../api/scenes';

interface UseExcalidrawAutosaveOptions {
  sceneId: string | null;
  elements: any[];
  files: Map<string, any>;
  enabled?: boolean;
  intervalMs?: number;
}

export function useExcalidrawAutosave({
  sceneId,
  elements,
  files,
  enabled = true,
  intervalMs = 5000,
}: UseExcalidrawAutosaveOptions) {
  const lastSavedRef = useRef<string>('');
  const saveTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const saveSnapshot = useCallback(async () => {
    if (!sceneId || !enabled) return;

    const snapshotData = JSON.stringify({
      elements,
      files: Object.fromEntries(files),
    });

    // Only save if changed
    if (snapshotData === lastSavedRef.current) return;

    try {
      await updateSnapshot(sceneId, snapshotData);
      lastSavedRef.current = snapshotData;
      console.debug('[ExcalidrawAutosave] Snapshot saved');
    } catch (err) {
      console.warn('[ExcalidrawAutosave] Failed to save snapshot:', err);
    }
  }, [sceneId, elements, files, enabled]);

  useEffect(() => {
    if (!enabled || !sceneId) return;

    saveTimerRef.current = setInterval(saveSnapshot, intervalMs);

    return () => {
      if (saveTimerRef.current) {
        clearInterval(saveTimerRef.current);
      }
    };
  }, [enabled, sceneId, intervalMs, saveSnapshot]);

  return {
    saveNow: saveSnapshot,
  };
}
```

- [ ] **Step 3: TypeScript 编译验证**

Run: `npx tsc --noEmit`

Expected: 无错误

- [ ] **Step 4: Commit**

```bash
git add src/react-app/hooks/useExcalidrawReconnect.ts src/react-app/hooks/useExcalidrawAutosave.ts
git commit -m "feat: add Excalidraw reconnect with exponential backoff and autosave"
```

---

## Task 9: 集成测试 - 后端 API 测试

**Files:**
- Create: `src/test/java/com/cn/cloudpictureplatform/application/excalidraw/ExcalidrawSceneServiceTests.java`

- [ ] **Step 1: 创建 Service 测试**

```java
package com.cn.cloudpictureplatform.application.excalidraw;

import com.cn.cloudpictureplatform.domain.excalidraw.ExcalidrawScene;
import com.cn.cloudpictureplatform.infrastructure.persistence.excalidraw.ExcalidrawSceneRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.excalidraw.ExcalidrawFileRepository;
import com.cn.cloudpictureplatform.interfaces.excalidraw.dto.CreateExcalidrawSceneRequest;
import com.cn.cloudpictureplatform.interfaces.excalidraw.dto.ExcalidrawSceneResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
class ExcalidrawSceneServiceTests {

    @Autowired
    private ExcalidrawSceneService sceneService;

    @Autowired
    private ExcalidrawSceneRepository sceneRepository;

    @Test
    void shouldCreateScene() {
        CreateExcalidrawSceneRequest request = new CreateExcalidrawSceneRequest();
        request.setSceneName("Test Scene");

        UUID userId = UUID.randomUUID();
        ExcalidrawSceneResponse response = sceneService.createScene(request, userId);

        assertNotNull(response.getId());
        assertEquals("Test Scene", response.getSceneName());
        assertEquals(userId, response.getLastUpdatedByUserId());
        assertEquals(0L, response.getVersion());
        assertEquals(0L, response.getLastSeq());
    }

    @Test
    void shouldCreateSceneWithDefaultName() {
        CreateExcalidrawSceneRequest request = new CreateExcalidrawSceneRequest();

        ExcalidrawSceneResponse response = sceneService.createScene(request, UUID.randomUUID());

        assertEquals("Untitled", response.getSceneName());
    }

    @Test
    void shouldGetSceneById() {
        CreateExcalidrawSceneRequest request = new CreateExcalidrawSceneRequest();
        request.setSceneName("Get Test");

        ExcalidrawSceneResponse created = sceneService.createScene(request, UUID.randomUUID());
        ExcalidrawSceneResponse fetched = sceneService.getScene(created.getId());

        assertEquals(created.getId(), fetched.getId());
        assertEquals("Get Test", fetched.getSceneName());
    }

    @Test
    void shouldUpdateSnapshot() {
        CreateExcalidrawSceneRequest request = new CreateExcalidrawSceneRequest();
        request.setSceneName("Snapshot Test");

        ExcalidrawSceneResponse created = sceneService.createScene(request, UUID.randomUUID());
        String snapshotData = "{\"elements\":[],\"files\":{}}";
        UUID userId = UUID.randomUUID();

        ExcalidrawSceneResponse updated = sceneService.updateSnapshot(created.getId(), snapshotData, 10L, userId);

        assertEquals(snapshotData, updated.getSnapshotData());
        assertEquals(10L, updated.getLastSeq());
        assertEquals(userId, updated.getLastUpdatedByUserId());
    }

    @Test
    void shouldDeleteScene() {
        CreateExcalidrawSceneRequest request = new CreateExcalidrawSceneRequest();
        request.setSceneName("Delete Test");

        ExcalidrawSceneResponse created = sceneService.createScene(request, UUID.randomUUID());
        sceneService.deleteScene(created.getId());

        assertThrows(Exception.class, () -> sceneService.getScene(created.getId()));
    }
}
```

- [ ] **Step 2: 运行测试**

Run: `$env:JAVA_HOME = "$env:USERPROFILE\.jdks\graalvm-jdk-21.0.8"; .\mvnw test -Dtest=ExcalidrawSceneServiceTests`

Expected: 所有测试通过

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/cn/cloudpictureplatform/application/excalidraw/
git commit -m "test: add ExcalidrawSceneService unit tests"
```

---

## Task 10: 集成验证 - 端到端流程

- [ ] **Step 1: 启动后端**

Run:
```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\graalvm-jdk-21.0.8"
.\mvnw spring-boot:run --spring.profiles.active=dev
```

Expected: 应用启动在 http://localhost:8080

- [ ] **Step 2: 验证 API 端点**

Run:
```bash
curl -X POST http://localhost:8080/api/scenes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"sceneName":"Test Whiteboard"}'
```

Expected: 返回包含 scene ID 的 JSON 响应

- [ ] **Step 3: 启动前端**

Run:
```bash
cd D:\ProgramProject\PolymerizationProject\cloud-picture-platform-web
npm run dev
```

Expected: 前端启动在 http://localhost:5173

- [ ] **Step 4: 验证 Excalidraw 页面**

1. 访问 http://localhost:5173/excalidraw
2. 应自动创建新场景并加载 Excalidraw
3. 绘制内容应自动同步

Expected: Excalidraw 画布正常加载，可绘制

- [ ] **Step 5: Commit 最终状态**

```bash
git add -A
git commit -m "feat: complete Excalidraw integration Phase 1 - Yjs collab + snapshot + reconnect"
```

---

## Phase 1 完成标准

| 功能 | 状态 |
|------|------|
| Excalidraw 组件加载 | ✅ |
| Yjs 实时同步 | ✅ |
| IndexedDB 离线持久化 | ✅ |
| 断线重连（指数退避） | ✅ |
| 自动保存 | ✅ |
| REST API (CRUD) | ✅ |
| 图片标注入口 | ✅ |
| 独立白板入口 | ✅ |

## Phase 2 待做（后续迭代）

- [ ] Presence 管理（在线用户、光标、活跃工具）
- [ ] Snapshot Compaction（定期合并 Yjs updates）
- [ ] 大场景优化（viewport culling）
- [ ] 文件上传限制（图片尺寸、MIME 白名单）
- [ ] 监控指标（update payload size、room memory usage）
- [ ] Scene Role（viewer/editor/owner/commenter）
