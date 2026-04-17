package com.cn.cloudpictureplatform.infrastructure.security;

import java.util.List;
import java.util.Map;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import com.cn.cloudpictureplatform.domain.user.UserRole;
import com.cn.cloudpictureplatform.websocket.PictureCollabAccessService;

@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final Pattern PICTURE_TOPIC_PATTERN = Pattern.compile("^/topic/pictures/([0-9a-fA-F\\-]+)/collab$");
    private static final Pattern PICTURE_APP_PATTERN =
            Pattern.compile("^/app/pictures/([0-9a-fA-F\\-]+)/(join|leave|lock|unlock|annotation)$");

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
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }
        if (accessor.getCommand() == StompCommand.CONNECT) {
            return handleConnect(message, accessor);
        }

        enforcePictureCollabAccess(accessor);
        return message;
    }

    private Message<?> handleConnect(Message<?> message, StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return message;
        }

        String token = authHeader.substring(7);
        if (!jwtTokenService.isTokenValid(token)) {
            return message;
        }

        String username = jwtTokenService.extractUsername(token);
        try {
            AppUserPrincipal principal =
                    (AppUserPrincipal) appUserDetailsService.loadUserByUsername(username);

            var authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + principal.getRole().name()))
            );
            accessor.setUser(authentication);

            Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
            if (sessionAttributes != null) {
                sessionAttributes.put("userId", principal.getId().toString());
                sessionAttributes.put("username", principal.getUsername());
                sessionAttributes.put("role", principal.getRole().name());
            }
        } catch (Exception ignored) {
            // Unknown user proceeds unauthenticated and will fail protected operations later.
        }

        return message;
    }

    private void enforcePictureCollabAccess(StompHeaderAccessor accessor) {
        StompCommand command = accessor.getCommand();
        if (command == null) {
            return;
        }
        String destination = accessor.getDestination();
        if (destination == null || destination.isBlank()) {
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
        UserRole role = extractRole(accessor);
        if (!pictureCollabAccessService.canAccess(pictureId, userId, role)) {
            throw new AccessDeniedException("forbidden");
        }
    }

    private static UUID extractPictureId(String destination, Pattern pattern) {
        Matcher matcher = pattern.matcher(destination);
        if (!matcher.matches()) {
            return null;
        }
        return UUID.fromString(matcher.group(1));
    }

    private static UUID extractUserId(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) {
            return null;
        }
        Object value = sessionAttributes.get("userId");
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof String text && !text.isBlank()) {
            return UUID.fromString(text);
        }
        return null;
    }

    private static UserRole extractRole(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) {
            return UserRole.USER;
        }
        Object value = sessionAttributes.get("role");
        if (value instanceof UserRole role) {
            return role;
        }
        if (value instanceof String text && !text.isBlank()) {
            return UserRole.valueOf(text);
        }
        return UserRole.USER;
    }
}
