package com.zyq.chirp.adviceserver.mq.Assembler;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.zyq.chirp.adviceclient.dto.NotificationDto;
import com.zyq.chirp.adviceserver.domain.enums.NoticeEntityTypeEnums;
import com.zyq.chirp.adviceserver.domain.enums.NoticeEventTypeEnums;
import com.zyq.chirp.adviceserver.domain.enums.NoticeStatusEnums;
import com.zyq.chirp.adviceserver.domain.enums.NoticeTypeEnums;
import com.zyq.chirp.adviceserver.service.NotificationService;
import com.zyq.chirp.chirpclient.client.ChirperClient;
import com.zyq.chirp.chirpclient.dto.ChirperDto;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 推文互动消息组装器
 * 负责处理与推文相关的互动消息（点赞、转发、引用、回复、提及等）
 * 将初始消息补充完整后发送给接收者
 */
@Component
@Slf4j
public class ChirperMessageAssembler {
    // 推文服务客户端，用于获取推文信息
    @Resource
    ChirperClient chirperClient;
    
    // Kafka消息发送模板
    @Resource
    KafkaTemplate<String, NotificationDto> kafkaTemplate;
    
    // 各种消息主题配置
    @Value("${mq.topic.site-message.notice}")
    String notice;
    @Value("${mq.topic.site-message.like}")
    String likeTopic;
    @Value("${mq.topic.site-message.forward}")
    String forwardTopic;
    @Value("${mq.topic.site-message.quote}")
    String quoteTopic;
    @Value("${mq.topic.site-message.reply}")
    String replyTopic;
    @Value("${mq.topic.site-message.mentioned}")
    String mentionedTopic;
    
    // 通知服务
    @Resource
    NotificationService notificationService;

    /**
     * 接收并处理推文互动消息
     * 1. 获取相关推文的详细信息
     * 2. 补充通知的接收者信息
     * 3. 设置通知的具体类型
     * 4. 检查消息是否可发送
     * 5. 发送到通知主题
     *
     * @param records Kafka消息记录列表
     * @param ack 消息确认对象
     */
    @KafkaListener(topics = {"${mq.topic.site-message.like}", "${mq.topic.site-message.forward}",
            "${mq.topic.site-message.quote}", "${mq.topic.site-message.reply}", "${mq.topic.site-message.mentioned}"},
            groupId = "${mq.consumer.group.pre-interaction}",
            batch = "true", concurrency = "4")
    public void receiver(@Payload List<ConsumerRecord<String, NotificationDto>> records, Acknowledgment ack) {
        try {
            // 提取所有相关推文ID
            List<Long> chirperIds = records.stream()
                    .map(ConsumerRecord::value)
                    .map(NotificationDto::getSonEntity)
                    .map(Long::parseLong)
                    .toList();
            if (!chirperIds.isEmpty()) {
                // 获取推文详细信息
                ResponseEntity<List<ChirperDto>> response = chirperClient.getBasicInfo(chirperIds);
                if (response.getStatusCode().is2xxSuccessful()) {
                    // 将推文信息转换为Map便于查找
                    Map<Long, ChirperDto> chirperDtoMap = Objects.requireNonNull(response.getBody())
                            .stream().collect(Collectors.toMap(ChirperDto::getId, Function.identity()));
                    List<NotificationDto> notificationDtos = new ArrayList<>();
                    
                    // 处理每条互动消息
                    for (ConsumerRecord<String, NotificationDto> record : records) {
                        String topic = record.topic();
                        NotificationDto notificationDto = record.value();
                        // 设置通知的基本信息
                        notificationDto.setId(IdWorker.getId());
                        notificationDto.setEntityType(NoticeEntityTypeEnums.CHIRPER.name());
                        notificationDto.setNoticeType(NoticeTypeEnums.USER.name());
                        notificationDto.setStatus(NoticeStatusEnums.UNREAD.getStatus());
                        notificationDto.setCreateTime(new Timestamp(System.currentTimeMillis()));
                        
                        // 设置接收者（推文作者）
                        if (notificationDto.getReceiverId() == null) {
                            ChirperDto chirperDto = chirperDtoMap.get(Long.parseLong(notificationDto.getSonEntity()));
                            // 跳过自己给自己的通知
                            if (chirperDto == null || notificationDto.getSenderId().equals(chirperDto.getAuthorId())) {
                                continue;
                            } else {
                                notificationDto.setReceiverId(chirperDto.getAuthorId());
                            }
                        }
                        
                        // 根据消息主题设置事件类型
                        if (likeTopic.equalsIgnoreCase(topic)) {
                            notificationDto.setEvent(NoticeEventTypeEnums.LIKE.name());
                        } else if (forwardTopic.equalsIgnoreCase(topic)) {
                            notificationDto.setEvent(NoticeEventTypeEnums.FORWARD.name());
                        } else if (replyTopic.equalsIgnoreCase(topic)) {
                            notificationDto.setEvent(NoticeEventTypeEnums.REPLY.name());
                        } else if (quoteTopic.equalsIgnoreCase(topic)) {
                            notificationDto.setEvent(NoticeEventTypeEnums.QUOTE.name());
                        } else if (mentionedTopic.equalsIgnoreCase(topic)) {
                            notificationDto.setEvent(NoticeEventTypeEnums.MENTIONED.name());
                        }
                        notificationDtos.add(notificationDto);
                    }
                    
                    // 检查消息是否可发送（例如检查接收者是否屏蔽了发送者）
                    notificationDtos = notificationService.getSendable(notificationDtos);
                    // 发送可达的消息
                    for (NotificationDto notificationDto : notificationDtos) {
                        if (NoticeStatusEnums.UNREACHABLE.getStatus() != notificationDto.getStatus()) {
                            kafkaTemplate.send(notice, notificationDto);
                        }
                    }
                } else if (response.getStatusCode().isError()) {
                    log.error("组装推文互动消息时获取推文信息错误，组装失败=>{}", response);
                }
            }
        } catch (Exception e) {
            log.error("组装推文互动消息失败，错误=>", e);
        } finally {
            ack.acknowledge();
        }
    }
}
