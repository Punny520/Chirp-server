package com.zyq.chirp.adviceserver.mq.Assembler;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.zyq.chirp.adviceclient.dto.NotificationDto;
import com.zyq.chirp.adviceserver.domain.enums.NoticeEntityTypeEnums;
import com.zyq.chirp.adviceserver.domain.enums.NoticeEventTypeEnums;
import com.zyq.chirp.adviceserver.domain.enums.NoticeTypeEnums;
import com.zyq.chirp.authclient.client.AuthClient;
import com.zyq.chirp.common.mq.model.Message;
import com.zyq.chirp.feedclient.dto.FeedDto;
import com.zyq.chirp.userclient.client.UserClient;
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
import java.util.List;
import java.util.Map;

/**
 * 推文发布事件组装器
 * 负责处理用户发布新推文的事件
 * 将新推文通知发送给所有在线的关注者
 */
@Component
@Slf4j
public class TweetedEventAssembler {
    // 每次查询关注者的数量
    @Value("${default-config.follower-query-size}")
    Integer querySize;
    
    // Kafka消息发送模板
    @Resource
    KafkaTemplate<String, NotificationDto> kafkaTemplate;
    
    // 用户服务客户端
    @Resource
    UserClient userClient;
    
    // 认证服务客户端
    @Resource
    AuthClient authClient;
    
    // 推文通知主题
    @Value("${mq.topic.site-message.tweeted-advice}")
    String tweeted;

    /**
     * 接收并处理推文发布事件
     * 1. 获取发布者的所有关注者
     * 2. 分批处理关注者（避免一次处理太多）
     * 3. 检查关注者的在线状态
     * 4. 向在线的关注者发送通知
     *
     * @param record Kafka消息记录
     * @param ack 消息确认对象
     */
    @KafkaListener(topics = "${mq.topic.tweeted}",
            groupId = "${mq.consumer.group.tweeted}",
            batch = "false", concurrency = "4")
    public void receiver(@Payload ConsumerRecord<String, Message<FeedDto>> record, Acknowledgment ack) {
        try {
            Message<FeedDto> message = record.value();
            FeedDto feedDto = message.getBody();
            long userId = Long.parseLong(feedDto.getPublisher());
            
            // 获取关注者总数
            Long followerCount = userClient.getFollowerCount(userId).getBody().getFollower();
            
            // 分批处理关注者
            for (int i = 0; i < Math.ceilDiv(followerCount, querySize); i++) {
                int finalI = i;
                // 使用虚拟线程处理每一批关注者
                Thread.ofVirtual().start(() -> {
                    // 获取当前批次的关注者ID列表
                    List<Long> followers = userClient.getFollowerIds(userId, finalI, querySize).getBody();
                    if (followers != null) {
                        List<String> followerIds = followers.stream().map(String::valueOf).toList();
                        // 检查关注者的在线状态
                        Map<String, Boolean> onlineMap = authClient.multiCheck(followerIds).getBody();
                        if (onlineMap != null) {
                            onlineMap.forEach((follower, onlineStatus) -> {
                                // 只给在线用户发送通知
                                if (onlineStatus) {
                                    NotificationDto messageDto = NotificationDto.builder()
                                            .id(IdWorker.getId())
                                            .receiverId(Long.parseLong(follower))
                                            .senderId(Long.parseLong(feedDto.getPublisher()))
                                            .sonEntity(feedDto.getContentId())
                                            .entityType(NoticeEntityTypeEnums.CHIRPER.name())
                                            .event(NoticeEventTypeEnums.TWEETED.name())
                                            .createTime(new Timestamp(System.currentTimeMillis()))
                                            .noticeType(NoticeTypeEnums.SYSTEM.name())
                                            .build();
                                    kafkaTemplate.send(tweeted, messageDto);
                                }
                            });
                        }
                    }
                });
            }
        } catch (Exception e) {
            log.error("组装发推通知错误，错误=>", e);
        } finally {
            ack.acknowledge();
        }
    }
}
