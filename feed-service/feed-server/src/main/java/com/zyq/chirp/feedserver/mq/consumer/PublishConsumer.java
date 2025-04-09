package com.zyq.chirp.feedserver.mq.consumer;

import com.zyq.chirp.common.mq.model.Message;
import com.zyq.chirp.feedclient.dto.FeedDto;
import com.zyq.chirp.feedserver.service.FeedService;
import com.zyq.chirp.userclient.client.UserClient;
import com.zyq.chirp.userclient.dto.FollowDto;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 推文发布消息消费者
 * 负责处理新推文发布事件，将推文推送到粉丝的Feed流中
 */
@Component
@Slf4j
public class PublishConsumer {
    /**
     * 用户服务客户端
     */
    @Resource
    UserClient userClient;

    /**
     * 每次查询的粉丝数量
     */
    @Value("${default-config.follower-query-size}")
    Integer querySize;

    /**
     * 消息重试最大次数
     */
    @Value("${mq.retry.max}")
    Integer maxRetryTimes;

    /**
     * Feed服务
     */
    @Resource
    FeedService feedService;

    /**
     * 推文已发布的消息主题
     */
    @Value("${mq.topic.tweeted}")
    String tweetedTopic;

    /**
     * Kafka消息模板
     */
    @Resource
    KafkaTemplate<String, Message<FeedDto>> kafkaTemplate;

    /**
     * 消费推文发布消息
     * 将新发布的推文添加到所有粉丝的Feed流中
     *
     * @param record Kafka消息记录
     * @param ack 消息确认对象
     */
    @KafkaListener(topics = "${mq.topic.publish}",
            batch = "false", concurrency = "4")
    public void receiver(@Payload ConsumerRecord<String, Message<FeedDto>> record, Acknowledgment ack) {
        Message<FeedDto> message = record.value();
        try {
            FeedDto feedDto = message.getBody();
            long userId = Long.parseLong(feedDto.getPublisher());
            // 获取发布者的粉丝数量
            FollowDto followDto = userClient.getFollowerCount(userId).getBody();
            // 分批获取粉丝ID
            for (int i = 0; i < Math.ceilDiv(followDto.getFollower(), querySize); i++) {
                int finalI = i;
                Thread.ofVirtual().start(() -> {
                    // 获取一批粉丝ID
                    List<Long> followers = userClient.getFollowerIds(userId, finalI, querySize).getBody();
                    if (followers != null && !followers.isEmpty()) {
                        // 为每个粉丝添加Feed记录
                        followers.forEach(follower -> Thread.ofVirtual().start(() -> {
                            try {
                                FeedDto dto = FeedDto.builder()
                                        .receiverId(follower.toString())
                                        .publisher(feedDto.getPublisher())
                                        .contentId(feedDto.getContentId())
                                        .score(feedDto.getScore())
                                        .build();
                                // 添加到Feed流
                                feedService.addOne(dto);
                                // 发送推文已发布消息
                                Message<FeedDto> dtoMessage = Message.<FeedDto>builder().body(dto).retryTimes(0).build();
                                kafkaTemplate.send(tweetedTopic, dtoMessage);
                            } catch (Exception e) {
                                log.error("", e);
                            }
                        }));
                    }
                });
            }
        } catch (Exception e) {
            log.error("推送用户推文更新通知失败,错误==>", e);
            // 消息重试处理
            if (message.getRetryTimes() < maxRetryTimes) {
                message.setRetryTimes(message.getRetryTimes() + 1);
                kafkaTemplate.send(record.topic(), record.key(), message);
            }
        } finally {
            // 确认消息已处理
            ack.acknowledge();
        }
    }
}
