package com.cn.cloudpictureplatform.infrastructure.security;
import com.cn.cloudpictureplatform.common.security.AppUserPrincipal;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import com.cn.cloudpictureplatform.application.collaboration.PictureCollabAccessService;
import lombok.NonNull;

@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {
    private static final Pattern PICTURE_TOPIC_PATTERN = Pattern.compile("^/topic/pictures/([0-9a-fA-F\\-]+)/collab$");
    private static final Pattern PICTURE_APP_PATTERN =
            Pattern.compile("^/app/pictures/([0-9a-fA-F\\-]+)/(join|leave|lock|unlock|annotation)$");
    private static final Pattern ADMIN_TOPIC_PATTERN = Pattern.compile("^/topic/admin/.+");
    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final JwtTokenService jwtTokenService;
    private final AppUserDetailsService appUserDetailsService;
    private final PictureCollabAccessService pictureCollabAccessService;

    public WebSocketAuthChannelInterceptor(
            JwtTokenService jwtTokenService,
            AppUserDetailsService appUserDetailsService,
            PictureCollabAccessService pictureCollabAccessService
    ) {
        this.jwtTokenService = jwtTokenService;
        this.appUserDetailsService = appUserDetailsService;
        this.pictureCollabAccessService = pictureCollabAccessService;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }
        if (accessor.getCommand() == StompCommand.CONNECT) {
            return handleConnect(message, accessor);
        }

        enforceDestinationAccess(accessor);
        return message;
    }

    private Message<?> handleConnect(Message<?> message, StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new AccessDeniedException("missing or invalid authorization header");
        }

        String token = authHeader.substring(7);
        if (!jwtTokenService.isTokenValid(token)) {
            throw new AccessDeniedException("invalid token");
        }

        String username = jwtTokenService.extractUsername(token);
        AppUserPrincipal principal;
        try {
            principal = (AppUserPrincipal) appUserDetailsService.loadUserByUsername(username);
        } catch (Exception ex) {
            throw new AccessDeniedException("user not found: " + username);
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        );
        accessor.setUser(authentication);

        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes != null) {
            sessionAttributes.put("userId", principal.getId().toString());
            sessionAttributes.put("username", principal.getUsername());
            sessionAttributes.put("permissions", principal.getPermissions());
            sessionAttributes.put("roles", principal.getRoles());
        }

        return message;
    }

    private void enforceDestinationAccess(StompHeaderAccessor accessor) {
        StompCommand command = accessor.getCommand();
        if (command == null) {
            return;
        }
        String destination = accessor.getDestination();
        if (destination == null || destination.isBlank()) {
            return;
        }

        if (command == StompCommand.SUBSCRIBE && isAdminTopic(destination)) {
            enforceAdminAccess(accessor);
            return;
        }

        UUID pictureId = switch (command) {
            case SUBSCRIBE -> extractPictureId(destination, PICTURE_TOPIC_PATTERN);
            case SEND -> extractPictureId(destination, PICTURE_APP_PATTERN);
            default -> null;
        };
        if (pictureId == null) {
            return;
        }

        UUID userId = extractUserId(accessor);
        Set<String> permissions = extractSessionAttribute(accessor, "permissions");
        if (!pictureCollabAccessService.canAccess(pictureId, userId, permissions)) {
            throw new AccessDeniedException("forbidden");
        }
    }

    private static boolean isAdminTopic(String destination) {
        return ADMIN_TOPIC_PATTERN.matcher(destination).matches();
    }

    private void enforceAdminAccess(StompHeaderAccessor accessor) {
        Set<String> roles = extractSessionAttribute(accessor, "roles");
        if (roles == null || !roles.contains(ROLE_ADMIN)) {
            throw new AccessDeniedException("admin role required");
        }
    }

    private static UUID extractPictureId(String destination, Pattern pattern) {
        Matcher matcher = pattern.matcher(destination);
        if (!matcher.matches()) {
            return null;
        }
        return UUID.fromString(matcher.group(1));
    }

    @SuppressWarnings("unchecked")
    private static <T> T extractSessionAttribute(StompHeaderAccessor accessor, String key) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) {
            return null;
        }
        Object value = sessionAttributes.get(key);
        return switch (value) {
            case null -> null;
            case Set<?> set -> (T) set;
            case List<?> list -> (T) new HashSet<>((List<String>) list);
            default -> (T) value;
        };
    }

    private static UUID extractUserId(StompHeaderAccessor accessor) {
        Object value = extractSessionAttribute(accessor, "userId");
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof String text && !text.isBlank()) {
            return UUID.fromString(text);
        }
        return null;
    }
}
