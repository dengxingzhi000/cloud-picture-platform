package com.cn.cloudpictureplatform.infrastructure.events;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import com.cn.cloudpictureplatform.domain.events.DomainEvent;
import com.cn.cloudpictureplatform.domain.events.DomainEventBus;

@Component
@Primary
public class SpringDomainEventBus implements DomainEventBus {

    private final ApplicationEventPublisher publisher;

    public SpringDomainEventBus(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(DomainEvent event) {
        publisher.publishEvent(event);
    }

    @Override
    public void publishAll(List<? extends DomainEvent> events) {
        events.forEach(publisher::publishEvent);
    }
}
