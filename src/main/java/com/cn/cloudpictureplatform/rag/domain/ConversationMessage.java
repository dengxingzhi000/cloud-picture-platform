package com.cn.cloudpictureplatform.rag.domain;

import com.cn.cloudpictureplatform.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "rag_conversation")
public class ConversationMessage extends BaseEntity {

    @Column(nullable = false)
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageRole role;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    public enum MessageRole {
        USER, ASSISTANT
    }
}