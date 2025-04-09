package com.zyq.chirp.adviceserver.mq.Assembler;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.zyq.chirp.adviceclient.dto.NotificationDto;
import com.zyq.chirp.adviceserver.domain.enums.NoticeEntityTypeEnums;
import com.zyq.chirp.adviceserver.domain.enums.NoticeEventTypeEnums;
import com.zyq.chirp.adviceserver.domain.enums.NoticeStatusEnums;
import com.zyq.chirp.adviceserver.domain.enums.NoticeTypeEnums;
import com.zyq.chirp.adviceserver.service.NotificationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户互动消息组装器
 * 负责处理用户之间的互动消息（如关注等）
 * 将消息补充完整后发送给接收者
 */
@Component
@Slf4j
public class UserMessageAssembler {
    // Kafka消息发送模板
    @Resource
    KafkaTemplate<String, NotificationDto> kafkaTemplate;
    
    // 通知消息主题
    @Value("${mq.topic.site-message.notice}")
    String notice;
    
    // 关注消息主题
    @Value("${mq.topic.site-message.follow}")
    String followTopic;
    
    // 通知服务，用于检查消息是否可发送
    @Resource
    NotificationService notificationService;

    /**
     * 接收并处理用户互动消息
     * 1. 补充消息的基本信息（ID、类型、状态、时间戳等）
     * 2. 根据消息类型设置具体的事件类型
     * 3. 检查消息是否可发送
     * 4. 发送到通知主题
     *
     * @param records Kafka消息记录列表
     * @param ack 消息确认对象
     */
    @KafkaListener(topics = "${mq.topic.site-message.follow}",
            groupId = "${mq.consumer.group.pre-interaction}",
            batch = "true")
    public void receiver(@Payload List<ConsumerRecord<String, NotificationDto>> records, Acknowledgment ack) {
        try {
            List<NotificationDto> notificationDtos = new ArrayList<>();
            // 处理每条用户互动消息
            for (ConsumerRecord<String, NotificationDto> record : records) {
                String topic = record.topic();
                NotificationDto notificationDto = record.value();
                // 设置通知的基本信息
                notificationDto.setId(IdWorker.getId());
                notificationDto.setNoticeType(NoticeTypeEnums.USER.name());
                notificationDto.setEntityType(NoticeEntityTypeEnums.USER.name());
                notificationDto.setCreateTime(new Timestamp(System.currentTimeMillis()));
                notificationDto.setStatus(NoticeStatusEnums.UNREAD.getStatus());
                
                // 根据消息主题设置事件类型
                if (followTopic.equalsIgnoreCase(topic)) {
                    notificationDto.setEvent(NoticeEventTypeEnums.FOLLOW.name());
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
        } catch (Exception e) {
            log.error("组装用户互动消息失败，错误=>", e);
        } finally {
            // 无论成功失败都确认消息，防止消息积压
            ack.acknowledge();
        }
    }
}
