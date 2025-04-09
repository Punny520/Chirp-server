package com.zyq.chirp.adviceserver.service.impl;

import com.zyq.chirp.adviceserver.mq.listener.RedisSubscribeListener;
import com.zyq.chirp.adviceserver.service.WsService;
import jakarta.annotation.Resource;
import jakarta.websocket.Session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket服务实现类
 * 负责管理WebSocket连接的生命周期，包括：
 * 1. 建立连接时的订阅处理
 * 2. 断开连接时的清理工作
 * 3. 维护用户与Redis订阅监听器的映射关系
 */
@Service
public class WsServiceImpl implements WsService {
    // 存储用户ID与其对应的Redis订阅监听器的映射关系
    private static final Map<String, RedisSubscribeListener> redisPSubMap = new HashMap<>();
    
    // Kafka消息发送模板
    @Resource
    KafkaTemplate<String, Object> kafkaTemplate;
    
    // Redis消息监听容器，用于管理消息监听器
    @Resource
    RedisMessageListenerContainer redisMessageListenerContainer;
    
    // WebSocket连接事件主题
    @Value("${mq.topic.socket-connect}")
    private String connectTopic;
    
    // WebSocket断开连接事件主题
    @Value("${mq.topic.socket-disconnect}")
    private String disconnectTopic;
    
    // 用户消息主题前缀
    @Value("${mq.topic.site-message.user}")
    private String messageTopic;

    /**
     * 处理WebSocket连接建立
     * 1. 发送连接事件到Kafka
     * 2. 创建或复用Redis订阅监听器
     * 3. 将会话添加到监听器中
     *
     * @param userId 用户ID
     * @param session WebSocket会话
     */
    @Override
    public void connect(Long userId, Session session) {
        // 发送连接事件到Kafka
        kafkaTemplate.send(connectTopic, userId);
        
        // 如果用户已有监听器，直接添加会话
        if (redisPSubMap.containsKey(userId.toString())) {
            redisPSubMap.get(userId.toString()).addSession(session);
        } else {
            // 否则创建新的监听器并配置
            RedisSubscribeListener subscribeListener = new RedisSubscribeListener(session);
            redisPSubMap.put(userId.toString(), subscribeListener);
            // 添加到Redis监听容器，订阅用户特定的消息通道
            redisMessageListenerContainer.addMessageListener(subscribeListener, new ChannelTopic(messageTopic + userId));
        }
    }

    /**
     * 处理WebSocket连接断开
     * 1. 发送断开连接事件到Kafka
     * 2. 从监听器中移除会话
     * 3. 如果用户没有活跃会话，清理相关资源
     *
     * @param userId 用户ID
     * @param session WebSocket会话
     */
    @Override
    public void disconnect(Long userId, Session session) {
        // 发送断开连接事件到Kafka
        kafkaTemplate.send(disconnectTopic, userId);
        
        // 获取用户的监听器
        RedisSubscribeListener listener = redisPSubMap.get(userId.toString());
        // 移除断开的会话
        listener.removeSession(session.getId());
        
        // 如果用户没有活跃会话了，清理监听器
        if (listener.getOpenSessionSize() <= 0) {
            redisMessageListenerContainer.removeMessageListener(listener);
            redisPSubMap.remove(userId.toString());
        }
    }
}
