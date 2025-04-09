package com.zyq.chirp.adviceserver.mq.consumer;

import com.zyq.chirp.adviceclient.dto.ChatDto;
import com.zyq.chirp.adviceserver.service.ChatService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 聊天消息消费者
 * 负责消费Kafka中的聊天消息，并将其持久化到数据库中
 * 确保聊天记录的可靠存储和历史查询
 */
@Component
@Slf4j
public class ChatMessageConsumer {
    // 聊天消息服务，用于处理和存储聊天记录
    @Resource
    ChatService messageService;

    /**
     * 接收并处理聊天消息
     * 将消息批量保存到数据库中
     * 使用批量处理提高性能，并发数为4
     *
     * @param messageDtos 聊天消息DTO列表
     * @param ack 消息确认对象
     */
    @KafkaListener(topics = "${mq.topic.site-message.chat}",
            groupId = "${mq.consumer.group.chat}",
            batch = "true", concurrency = "4")
    public void receiver(@Payload List<ChatDto> messageDtos, Acknowledgment ack) {
        try {
            // 批量保存聊天消息
            messageService.addBatch(messageDtos);
        } catch (Exception e) {
            log.error("写入私信失败,错误=>", e);
        } finally {
            // 无论成功失败都确认消息，防止消息积压
            ack.acknowledge();
        }
    }
}
