package com.zyq.chirp.feedserver.mq.consumer;

import com.zyq.chirp.chirpclient.client.ChirperClient;
import com.zyq.chirp.common.mq.model.Message;
import com.zyq.chirp.feedserver.service.FeedService;
import com.zyq.chirp.userclient.dto.RelationDto;
import jakarta.annotation.Resource;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 取关消息消费者
 * 当用户取关某人时，从Feed流中移除被取关用户的所有推文
 */
@Component
public class UnfollowConsumer {
    /**
     * Feed服务
     */
    @Resource
    FeedService feedService;

    /**
     * 推文服务客户端
     */
    @Resource
    ChirperClient chirperClient;

    /**
     * 消息重试最大次数
     */
    @Value("${mq.retry.max}")
    Integer maxRetryTimes;

    /**
     * Kafka消息模板
     */
    @Resource
    KafkaTemplate<String, Message<RelationDto>> kafkaTemplate;

    /**
     * 消费取关消息
     * 批量处理取关事件，从用户的Feed流中移除被取关用户的推文
     *
     * @param records Kafka消息记录列表
     * @param ack 消息确认对象
     */
    @KafkaListener(topics = "${mq.topic.unfollow}",
            batch = "true", concurrency = "4")
    public void receiver(@Payload List<ConsumerRecord<String, Message<RelationDto>>> records, Acknowledgment ack) {
        try {
            // 获取所有被取关用户的ID
            List<Long> authors = records.stream().map(record -> record.value().getBody().getToId()).toList();
            // 获取这些用户的所有推文ID
            Map<Long, List<Long>> chirperMap = chirperClient.getIdByAuthor(authors).getBody();
            if (chirperMap != null && !chirperMap.isEmpty()) {
                // 处理每条取关消息
                records.forEach(record -> {
                    Thread.ofVirtual().start(() -> {
                        Message<RelationDto> message = record.value();
                        try {
                            RelationDto relationDto = message.getBody();
                            // 获取被取关用户的推文ID列表
                            List<Long> contentIds = chirperMap.get(relationDto.getToId());
                            if (contentIds != null && !contentIds.isEmpty()) {
                                // 从Feed流中移除这些推文
                                List<String> contents = contentIds.stream().map(String::valueOf).toList();
                                feedService.removeBatch(relationDto.getFromId().toString(), contents);
                            }
                        } catch (Exception e) {
                            // 消息重试处理
                            if (message.getRetryTimes() < maxRetryTimes) {
                                message.setRetryTimes(message.getRetryTimes() + 1);
                                kafkaTemplate.send(record.topic(), message);
                            }
                        }
                    });
                });
            }
        } finally {
            // 确认消息已处理
            ack.acknowledge();
        }
    }
}
