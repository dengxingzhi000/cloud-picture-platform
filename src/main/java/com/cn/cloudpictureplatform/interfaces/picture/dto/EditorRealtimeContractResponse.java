package com.cn.cloudpictureplatform.interfaces.picture.dto;

import java.util.List;
import lombok.Builder;

@Builder
public record EditorRealtimeContractResponse(
        String eventSchemaVersion,
        boolean documentMutationRequiresLock,
        boolean documentVersionCheckSupported,
        long lockTtlSeconds,
        String websocketEndpoint,
        String topicDestination,
        String userQueueDestination,
        String joinDestination,
        String leaveDestination,
        String lockDestination,
        String lockRefreshDestination,
        String unlockDestination,
        String eventDestination,
        List<String> supportedEvents,
        List<EditorRealtimeEventDefinitionResponse> eventDefinitions
) {
}
