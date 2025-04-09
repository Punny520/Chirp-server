package com.zyq.chirp.adviceserver.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zyq.chirp.adviceclient.dto.ChatDto;
import com.zyq.chirp.adviceserver.domain.enums.MessageTypeEnums;
import com.zyq.chirp.adviceserver.domain.enums.WsActionEnum;
import com.zyq.chirp.adviceserver.mq.dispatcher.MessageDispatcher;
import com.zyq.chirp.adviceserver.service.ChatService;
import com.zyq.chirp.adviceserver.service.WsService;
import com.zyq.chirp.common.domain.exception.ChirpException;
import com.zyq.chirp.common.domain.model.Code;
import jakarta.annotation.Resource;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket控制器
 * 处理实时消息通信，包括：
 * 1. 用户在线状态管理
 * 2. 实时聊天消息处理
 * 3. 心跳检测
 * 4. 系统通知推送
 */
@ServerEndpoint("/interaction/{userId}")  // WebSocket端点，通过用户ID建立连接
@Component
@Slf4j
public class WsController {

    // 用于JSON序列化和反序列化
    static ObjectMapper objectMapper;
    // 处理聊天相关的业务逻辑
    static ChatService chatService;
    // 处理WebSocket连接管理
    static WsService wsService;

    // 当前WebSocket连接的唯一标识
    String socketId;

    /**
     * 注入ObjectMapper
     * 由于WebSocket的特性，需要使用静态注入
     */
    @Autowired
    public void setObjectMapper(ObjectMapper objectMapper) {
        WsController.objectMapper = objectMapper;
    }

    /**
     * 注入ChatService
     * 用于处理聊天消息
     */
    @Autowired
    public void setPrivateMessageService(ChatService chatService) {
        WsController.chatService = chatService;
    }

    /**
     * 注入WsService
     * 用于管理WebSocket连接
     */
    @Autowired
    public void setWsService(WsService wsService) {
        WsController.wsService = wsService;
    }

    /**
     * 处理WebSocket连接建立
     * 当用户连接时，将其Session保存到连接管理器中
     * @param session WebSocket会话
     * @param userId 连接用户的ID
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("userId") Long userId) {
        log.info("WebSocket建立连接中,连接用户ID：{}", userId);
        socketId = session.getId();
        wsService.connect(userId, session);
    }

    /**
     * 处理接收到的WebSocket消息
     * 支持两种类型的消息：
     * 1. 心跳消息：保持连接活跃
     * 2. 聊天消息：需要进行处理并转发
     * @param message 接收到的消息内容
     * @param session WebSocket会话
     * @param userId 发送消息用户的ID
     */
    @OnMessage
    public void onMessage(String message, Session session, @PathParam("userId") Long userId) {
        // 处理心跳消息
        if (WsActionEnum.HEARTBEAT.name().equalsIgnoreCase(message)) {
            session.getAsyncRemote().sendText(WsActionEnum.HEARTBEAT.name());
        } else {
            try {
                // 处理聊天消息
                ChatDto chatDto = objectMapper.readValue(message, ChatDto.class);
                chatDto.setSenderId(userId);
                chatService.send(chatDto);
                // 发送确认消息给发送者
                session.getAsyncRemote().sendText(objectMapper.writeValueAsString(Map.of(MessageTypeEnums.CHAT.name(), List.of(chatDto))));
            } catch (JsonProcessingException e) {
                log.error("", e);
                throw new ChirpException(Code.ERR_BUSINESS, "json转化失败,请检查消息格式");
            }
        }
    }

    /**
     * 处理WebSocket连接关闭
     * 清理相关的会话信息和用户状态
     * @param session WebSocket会话
     * @param userId 用户ID
     */
    @OnClose
    public void onClose(Session session, @PathParam("userId") Long userId) {
        log.info("WebSocket断开连接,用户ID：{}", userId);
        wsService.disconnect(userId, session);
    }

    /**
     * 处理WebSocket连接错误
     * 记录错误日志以便排查问题
     * @param session WebSocket会话
     * @param userId 用户ID
     * @param error 错误信息
     */
    @OnError
    public void onError(Session session, @PathParam("userId") Long userId, Throwable error) {
        log.error("", error);
    }
}
