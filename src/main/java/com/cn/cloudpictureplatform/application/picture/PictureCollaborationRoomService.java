package com.cn.cloudpictureplatform.application.picture;

import com.cn.cloudpictureplatform.common.web.ApiErrorCode;
import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.config.CollaborationProperties;
import com.cn.cloudpictureplatform.config.JwtProperties;
import com.cn.cloudpictureplatform.infrastructure.security.AppUserPrincipal;
import com.cn.cloudpictureplatform.interfaces.picture.dto.PictureCollaborationRoomResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Builds frontend room bootstrap data for an external collaborative editing transport.
 */
@Service
public class PictureCollaborationRoomService {

    private static final String ROOM_CONTRACT_VERSION = "picture-collab-room.v1";

    private final CollaborationProperties collaborationProperties;
    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    public PictureCollaborationRoomService(
            CollaborationProperties collaborationProperties,
            JwtProperties jwtProperties
    ) {
        this.collaborationProperties = collaborationProperties;
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Create a signed room bootstrap contract for the requested picture and authenticated user.
     */
    public PictureCollaborationRoomResponse buildRoom(
            UUID pictureId,
            AppUserPrincipal principal,
            boolean canEdit,
            boolean canManage
    ) {
        if (pictureId == null || principal == null) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "invalid collaboration bootstrap request");
        }
        String roomId = collaborationProperties.getRoomPrefix() + ":" + pictureId;
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(collaborationProperties.getRoomTokenTtlSeconds());
        String permission = canManage ? "OWNER" : canEdit ? "EDITOR" : "VIEWER";

        String token = Jwts.builder()
                .subject(principal.getUsername())
                .issuer(jwtProperties.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim("uid", principal.getId().toString())
                .claim("pictureId", pictureId.toString())
                .claim("roomId", roomId)
                .claim("permission", permission)
                .signWith(secretKey)
                .compact();

        return PictureCollaborationRoomResponse.builder()
                .contractVersion(ROOM_CONTRACT_VERSION)
                .provider(collaborationProperties.getProvider())
                .roomId(roomId)
                .pictureId(pictureId)
                .serverUrl(normalizeServerUrl(collaborationProperties.getPublicUrl()))
                .token(token)
                .tokenExpiresAt(expiresAt)
                .permission(permission)
                .awarenessEnabled(collaborationProperties.isAwarenessEnabled())
                .indexedDbRecommended(collaborationProperties.isIndexedDbRecommended())
                .recommendedLibraries(List.of("yjs", "y-websocket", "y-indexeddb"))
                .build();
    }

    /**
     * Refresh the signed room token while preserving the same room contract.
     */
    public PictureCollaborationRoomResponse refreshRoomToken(
            UUID pictureId,
            AppUserPrincipal principal,
            boolean canEdit,
            boolean canManage
    ) {
        return buildRoom(pictureId, principal, canEdit, canManage);
    }

    private String normalizeServerUrl(String serverUrl) {
        if (!StringUtils.hasText(serverUrl)) {
            throw new ApiException(ApiErrorCode.SERVER_ERROR, "collaboration server url is not configured");
        }
        return serverUrl.trim();
    }
}
