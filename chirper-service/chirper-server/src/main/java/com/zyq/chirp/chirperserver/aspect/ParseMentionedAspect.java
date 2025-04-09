package com.zyq.chirp.chirperserver.aspect;

import com.zyq.chirp.adviceclient.dto.NotificationDto;
import com.zyq.chirp.chirpclient.dto.ChirperDto;
import com.zyq.chirp.chirperserver.domain.enums.CacheKey;
import com.zyq.chirp.chirperserver.domain.enums.ChirperStatus;
import com.zyq.chirp.chirperserver.domain.enums.ChirperType;
import com.zyq.chirp.common.domain.exception.ChirpException;
import com.zyq.chirp.common.domain.model.Code;
import com.zyq.chirp.common.mq.model.Message;
import com.zyq.chirp.common.util.TextUtil;
import com.zyq.chirp.feedclient.dto.FeedDto;
import com.zyq.chirp.userclient.client.UserClient;
import jakarta.annotation.Resource;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 推文内容解析切面
 * 负责处理带有@ParseMentioned注解的方法
 * 实现推文中@用户通知、话题统计和Feed流更新等功能
 */
@Aspect
@Component
public class ParseMentionedAspect {
    /**
     * Kafka消息模板，用于发送消息通知
     */
    @Resource
    KafkaTemplate<String, Object> kafkaTemplate;
    
    /**
     * 用户服务客户端，用于查询用户信息
     */
    @Resource
    UserClient userClient;
    
    /**
     * 被@用户的消息主题
     */
    @Value("${mq.topic.site-message.mentioned}")
    String mentionedTopic;
    
    /**
     * 推文发布的消息主题
     */
    @Value("${mq.topic.publish}")
    String publishTopic;
    
    /**
     * Redis操作模板，用于话题统计
     */
    @Resource
    RedisTemplate<String, Object> redisTemplate;

    /**
     * 定义切点
     * 匹配所有使用@ParseMentioned注解的方法
     */
    @Pointcut("@annotation(com.zyq.chirp.chirperserver.aspect.ParseMentioned)")
    public void pointcut() {
    }

    /**
     * 环绕通知
     * 在目标方法执行前后进行处理
     * 主要处理：@用户通知、推文发布和话题统计
     *
     * @param joinPoint 连接点
     * @return 方法执行结果
     */
    @Around("pointcut()")
    public Object doParse(ProceedingJoinPoint joinPoint) {
        try {
            Object result = joinPoint.proceed();
            if (result instanceof ChirperDto chirperDto) {
                // 处理单条推文
                sendMentioned(chirperDto);  // 发送@通知
                sendPublish(chirperDto);    // 发送推文发布消息
                commitTag(chirperDto);      // 统计话题
            }
            if (result instanceof List<?> chirperDtos) {
                // 处理推文列表
                parseTend((List<ChirperDto>) chirperDtos);
            }
            return result;
        } catch (Throwable e) {
            throw new ChirpException(Code.ERR_BUSINESS, e);
        }
    }

    /**
     * 发送@用户通知
     * 解析推文中@的用户名，并向这些用户发送通知
     *
     * @param chirperDto 推文数据
     */
    public void sendMentioned(ChirperDto chirperDto) {
        Thread.ofVirtual().start(() -> {
            // 查找推文中@的用户名
            List<String> usernames = TextUtil.findMentioned(chirperDto.getText());
            if (!usernames.isEmpty()) {
                // 根据用户名获取用户ID并发送通知
                userClient.getIdByUsername(usernames).getBody().forEach(id -> {
                    NotificationDto messageDto = NotificationDto.builder()
                            .senderId(chirperDto.getAuthorId())
                            .receiverId(id)
                            .sonEntity(chirperDto.getId().toString())
                            .build();
                    kafkaTemplate.send(mentionedTopic, messageDto);
                });
            }
        });
    }

    /**
     * 发送推文发布消息
     * 用于更新用户的Feed流
     *
     * @param chirperDto 推文数据
     */
    public void sendPublish(ChirperDto chirperDto) {
        Thread.ofVirtual().start(() -> {
            FeedDto feedDto = FeedDto.builder()
                    .publisher(chirperDto.getAuthorId().toString())
                    .contentId(chirperDto.getId().toString())
                    .score((double) chirperDto.getCreateTime().getTime())
                    .build();
            Message<FeedDto> message = new Message<>();
            message.setBody(feedDto);
            kafkaTemplate.send(publishTopic, feedDto.getPublisher(), message);
        });
    }

    /**
     * 解析推文列表中的话题趋势
     * 统计所有推文中出现的话题
     *
     * @param chirperDtos 推文列表
     */
    public void parseTend(List<ChirperDto> chirperDtos) {
        Thread.ofVirtual().start(() -> {
            List<String> texts = chirperDtos.stream().map(ChirperDto::getText).toList();
            ZSetOperations<String, Object> operations = redisTemplate.opsForZSet();
            TextUtil.findTags(texts).forEach(tag -> {
                operations.incrementScore(CacheKey.TEND_TAG_BOUND_KEY.getKey(), tag, 1);
            });
        });
    }

    /**
     * 提交话题统计
     * 更新话题的总体统计和单个推文的统计
     *
     * @param chirperDto 推文数据
     */
    public void commitTag(ChirperDto chirperDto) {
        Thread.ofVirtual().start(() -> {
            TextUtil.findTags(chirperDto.getText()).forEach(tag -> {
                ZSetOperations<String, Object> operations = redisTemplate.opsForZSet();
                // 更新话题总体统计
                operations.incrementScore(CacheKey.TEND_TAG_BOUND_KEY.getKey(), tag, 1);
                // 更新单个推文的话题统计
                operations.incrementScore(CacheKey.TEND_POST_BOUND_KEY.getKey(), tag, 1);
            });
        });
    }
}
