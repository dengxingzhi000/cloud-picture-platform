package com.cn.cloudpictureplatform.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import com.cn.cloudpictureplatform.domain.user.UserRole;
import com.cn.cloudpictureplatform.websocket.PictureCollabAccessService;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthChannelInterceptorTests {

    @Mock
    private JwtTokenService jwtTokenService;
    @Mock
    private AppUserDetailsService appUserDetailsService;
    @Mock
    private PictureCollabAccessService pictureCollabAccessService;

    private WebSocketAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new WebSocketAuthChannelInterceptor(
                jwtTokenService,
                appUserDetailsService,
                pictureCollabAccessService
        );
    }

    @Test
    void shouldRejectSubscribeWhenPictureAccessDenied() {
        UUID pictureId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(pictureCollabAccessService.canAccess(pictureId, userId, UserRole.USER)).thenReturn(false);

        Message<byte[]> message = buildMessage(
                StompCommand.SUBSCRIBE,
                "/topic/pictures/" + pictureId + "/collab",
                userId,
                UserRole.USER
        );

        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, null));
    }

    @Test
    void shouldAllowSendWhenPictureAccessGranted() {
        UUID pictureId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(pictureCollabAccessService.canAccess(pictureId, userId, UserRole.USER)).thenReturn(true);

        Message<byte[]> message = buildMessage(
                StompCommand.SEND,
                "/app/pictures/" + pictureId + "/join",
                userId,
                UserRole.USER
        );

        assertDoesNotThrow(() -> interceptor.preSend(message, null));
    }

    @Test
    void shouldRejectAnnotationSendWhenPictureAccessDenied() {
        UUID pictureId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(pictureCollabAccessService.canAccess(pictureId, userId, UserRole.USER)).thenReturn(false);

        Message<byte[]> message = buildMessage(
                StompCommand.SEND,
                "/app/pictures/" + pictureId + "/annotation",
                userId,
                UserRole.USER
        );

        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, null));
    }

    private Message<byte[]> buildMessage(StompCommand command, String destination, UUID userId, UserRole role) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put("userId", userId.toString());
        sessionAttributes.put("role", role.name());
        accessor.setSessionAttributes(sessionAttributes);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
