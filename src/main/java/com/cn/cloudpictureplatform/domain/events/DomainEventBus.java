package com.cn.cloudpictureplatform.domain.events;

import java.util.List;

public interface DomainEventBus {
    void publish(DomainEvent event);

    void publishAll(List<? extends DomainEvent> events);
}
