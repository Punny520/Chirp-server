package com.zyq.chirp.adviceserver.mq.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zyq.chirp.adviceclient.dto.ChatDto;
import com.zyq.chirp.adviceclient.dto.NotificationDto;
import com.zyq.chirp.adviceserver.config.SpringbootContext;
import com.zyq.chirp.adviceserver.domain.enums.MessageTypeEnums;
import com.zyq.chirp.adviceserver.strategy.impl.ChatPushStrategy;
import com.zyq.chirp.adviceserver.strategy.impl.NoticePushStrategy;
import jakarta.validation.constraints.NotNull;
import jakarta.websocket.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Redis订阅监听器
 * 负责接收Redis频道中的消息，并通过WebSocket推送给客户端
 * 每个用户都有自己的监听器实例，维护着该用户的所有WebSocket会话
 */
@Slf4j
public class RedisSubscribeListener implements MessageListener {
    // 存储用户的WebSocket会话，key是会话ID，value是会话对象
    private final Map<String, Session> sessionMap = new HashMap<>();
    
    // 通知消息推送策略
    NoticePushStrategy noticePushStrategy;
    
    // Redis操作模板
    RedisTemplate redisTemplate;
    
    // JSON序列化工具
    ObjectMapper objectMapper;
    
    // 聊天消息推送策略
    ChatPushStrategy chatPushStrategy;

    /**
     * 带会话的构造函数
     * 用于创建新的监听器实例并初始化第一个会话
     *
     * @param session 要添加的WebSocket会话
     */
    public RedisSubscribeListener(Session session) {
        ApplicationContext context = SpringbootContext.getContext();
        noticePushStrategy = context.getBean(NoticePushStrategy.class);
        chatPushStrategy = context.getBean(ChatPushStrategy.class);
        objectMapper = context.getBean(ObjectMapper.class);
        redisTemplate = (RedisTemplate) context.getBean("redisTemplate");
        this.sessionMap.put(session.getId(), session);
    }

    /**
     * 无参构造函数
     * 用于创建空的监听器实例
     */
    public RedisSubscribeListener() {
        ApplicationContext context = SpringbootContext.getContext();
        noticePushStrategy = context.getBean(NoticePushStrategy.class);
        redisTemplate = (RedisTemplate) context.getBean("redisTemplate");
    }

    /**
     * 获取当前打开的会话数量
     *
     * @return 打开的会话数量
     */
    public int getOpenSessionSize() {
        return sessionMap.values().stream().filter(Session::isOpen).toList().size();
    }

    /**
     * 添加新的WebSocket会话
     *
     * @param session 要添加的会话
     */
    public void addSession(Session session) {
        sessionMap.put(session.getId(), session);
    }

    /**
     * 移除指定的WebSocket会话
     *
     * @param id 要移除的会话ID
     */
    public void removeSession(String id) {
        sessionMap.remove(id);
    }

    /**
     * 处理从Redis接收到的消息
     * 将消息解析后通过WebSocket推送给客户端
     *
     * @param message Redis消息对象
     * @param pattern 订阅模式（未使用）
     */
    @Override
    public void onMessage(@NotNull Message message, byte[] pattern) {
        // 遍历所有会话，向每个活跃的会话推送消息
        sessionMap.forEach((sessionId, session) -> {
            if (session != null && session.isOpen()) {
                try {
                    // 解析Redis消息体
                    Map<String, String> map = objectMapper.readValue(message.getBody(), new TypeReference<>() {});
                    List<NotificationDto> notice = new ArrayList<>();
                    List<ChatDto> chatDtos = new ArrayList<>();

                    // 根据消息类型分别处理
                    map.forEach((type, siteMessageStr) -> {
                        try {
                            // 处理聊天消息
                            if (MessageTypeEnums.CHAT.name().equals(type)) {
                                List<ChatDto> chatDtoList = objectMapper.readValue(siteMessageStr, new TypeReference<>() {
                                });
                                chatDtos.addAll(chatDtoList);
                            }
                            // 处理通知消息
                            if (MessageTypeEnums.NOTICE.name().equals(type)) {
                                List<NotificationDto> notificationDtos = objectMapper.readValue(siteMessageStr, new TypeReference<>() {
                                });
                                notice.addAll(notificationDtos);
                            }
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    });

                    // 使用对应的策略推送消息
                    if (!notice.isEmpty()) {
                        noticePushStrategy.send(notice, List.of(session));
                    }
                    if (!chatDtos.isEmpty()) {
                        chatPushStrategy.send(chatDtos, List.of(session));
                    }
                } catch (Exception e) {
                    log.info("消息推送失败", e);
                }
            }
        });
    }
}
