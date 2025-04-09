package com.zyq.chirp.chirperserver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zyq.chirp.adviceclient.dto.NotificationDto;
import com.zyq.chirp.chirpclient.dto.ChirperDto;
import com.zyq.chirp.chirpclient.dto.LikeDto;
import com.zyq.chirp.chirperserver.aspect.Statistic;
import com.zyq.chirp.chirperserver.domain.enums.ActionTypeEnums;
import com.zyq.chirp.chirperserver.domain.enums.CacheKey;
import com.zyq.chirp.chirperserver.domain.pojo.Like;
import com.zyq.chirp.chirperserver.mapper.LikeMapper;
import com.zyq.chirp.chirperserver.service.ChirperService;
import com.zyq.chirp.chirperserver.service.LikeService;
import com.zyq.chirp.common.domain.exception.ChirpException;
import com.zyq.chirp.common.domain.model.Code;
import com.zyq.chirp.common.mq.enums.DefaultOperation;
import com.zyq.chirp.common.mq.model.Action;
import com.zyq.chirp.common.util.RetryUtil;
import com.zyq.chirp.common.util.StringUtil;
import com.zyq.chirp.communityclient.client.CommunityClient;
import com.zyq.chirp.communityclient.dto.CommunityDto;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 点赞服务实现类
 * 处理推文点赞相关的业务逻辑，包括点赞、取消点赞、统计等功能
 * 使用Redis缓存优化性能，使用Kafka实现异步消息处理
 */
@Service
@Slf4j
@CacheConfig(cacheNames = "like:chirper")
public class LikeServiceImpl implements LikeService {
    @Resource
    LikeMapper likeMapper;

    @Resource
    KafkaTemplate<String, Object> kafkaTemplate;
    
    @Value("${mq.topic.site-message.like}")
    String topic;
    
    @Value("${default-config.page-size}")
    Integer pageSize;
    
    Integer expire = 6;
    
    @Value("${mq.topic.chirper.like.count}")
    String LIKE_INCREMENT_COUNT_TOPIC;
    
    @Value("${mq.topic.chirper.like.record}")
    String LIKE_RECORD_TOPIC;

    /**
     * 添加点赞
     * 使用@Statistic注解统计浏览量
     * 使用@Cacheable注解缓存点赞状态
     */
    @Override
    @Statistic(id = "#likeDto.chirperId", key = {CacheKey.VIEW_COUNT_BOUND_KEY})
    @Cacheable(key = "#likeDto.userId+':'+#likeDto.chirperId")
    public void addLike(LikeDto likeDto) {
        if (likeDto.getUserId() == null || likeDto.getChirperId() == null) {
            throw new ChirpException(Code.ERR_BUSINESS, "未提供用户或推文信息");
        }
        // 创建点赞动作并发送到Kafka
        Action<Long, Long> action = new Action<>(
                ActionTypeEnums.LIKE.getAction(),
                DefaultOperation.INSET.getOperation(),
                likeDto.getUserId(),
                likeDto.getChirperId(),
                System.currentTimeMillis()
        );
        kafkaTemplate.send(LIKE_RECORD_TOPIC, action);
    }

    /**
     * 批量保存点赞记录
     * 处理来自Kafka的点赞消息，更新数据库并发送通知
     */
    @Override
    public void saveLike(List<Action<Long, Long>> actions) {
        // 转换为点赞实体
        List<Like> likes = actions.stream().map(action -> new Like(
                action.getTarget(),
                action.getOperator(),
                Timestamp.valueOf(LocalDateTime.now()))).toList();
        try {
            // 批量插入点赞记录
            RetryUtil.doDBRetry(() -> likeMapper.insertList(likes));
            // 统计每个推文的点赞数并发送增量消息
            Map<Long, Long> likeCount = likes.stream().collect(Collectors.groupingBy(Like::getChirperId, Collectors.counting()));
            likeCount.forEach((chirperId, count) -> {
                Action<Long, Long> increLikeAction = new Action<>();
                increLikeAction.setTarget(chirperId);
                increLikeAction.setActionType(ActionTypeEnums.LIKE.getAction());
                increLikeAction.setOperation(DefaultOperation.INCREMENT.getOperation());
                increLikeAction.setActionTime(System.currentTimeMillis());
                increLikeAction.setMulti(Math.toIntExact(count));
                kafkaTemplate.send(LIKE_INCREMENT_COUNT_TOPIC, increLikeAction);
            });

            // 发送点赞通知
            actions.forEach(action -> {
                NotificationDto notificationDto = NotificationDto.builder()
                        .sonEntity(String.valueOf(action.getTarget()))
                        .senderId(action.getOperator())
                        .build();
                kafkaTemplate.send(topic, notificationDto);
            });
        } catch (ExecutionException e) {
            log.error("插入点赞时发生无法成功的错误，点赞信息:{},错误:", likes, e);
        } catch (Exception e) {
            log.error("插入点赞失败，点赞消息:{},错误:", likes, e);
            // 失败重试
            actions.forEach(action -> {
                log.info("尝试重发:{}", action);
                kafkaTemplate.send(LIKE_RECORD_TOPIC, action);
            });
        }
    }

    /**
     * 取消点赞
     * 使用@CacheEvict注解清除点赞缓存
     */
    @Override
    @CacheEvict(key = "#likeDto.userId+':'+#likeDto.chirperId")
    public void cancelLike(LikeDto likeDto) {
        if (likeDto.getUserId() == null || likeDto.getChirperId() == null) {
            throw new ChirpException(Code.ERR_BUSINESS, "未提供用户或推文信息");
        }
        // 创建取消点赞动作并发送到Kafka
        Action<Long, Long> action = new Action<>(
                ActionTypeEnums.LIKE.getAction(),
                DefaultOperation.DELETE.getOperation(),
                likeDto.getUserId(),
                likeDto.getChirperId(),
                System.currentTimeMillis()
        );
        kafkaTemplate.send(LIKE_RECORD_TOPIC, action);
    }

    /**
     * 批量处理取消点赞
     * 处理来自Kafka的取消点赞消息，更新数据库
     */
    @Override
    public void saveLikeCancel(List<Action<Long, Long>> actions) {
        // 按推文ID分组处理
        Map<Long, List<Like>> collect = actions.stream().map(action ->
                        new Like(action.getTarget(), action.getOperator(), new Timestamp(action.getActionTime())))
                .collect(Collectors.groupingBy(Like::getChirperId));

        collect.forEach((chirperId, likes) -> {
            try {
                var ref = new Object() {
                    int affectRows = 0;
                };
                // 批量删除点赞记录
                RetryUtil.doDBRetry(() ->
                {
                    ref.affectRows = likeMapper.deleteList(likes);
                    return true;
                });
                // 如果删除成功，发送减少点赞数的消息
                if (ref.affectRows > 0) {
                    Action<Long, Long> action = new Action<>(
                            ActionTypeEnums.LIKE.getAction(),
                            DefaultOperation.DECREMENT.getOperation(),
                            ref.affectRows,
                            null,
                            chirperId,
                            System.currentTimeMillis()
                    );
                    kafkaTemplate.send(LIKE_INCREMENT_COUNT_TOPIC, action);
                }
            } catch (ExecutionException e) {
                log.error("删除点赞时发生无法成功的错误，点赞信息:{},错误:", likes, e);
            } catch (Exception e) {
                log.error("删除点赞失败，点赞消息:{},错误:", likes, e);
                // 失败重试
                likes.forEach(like -> {
                    Action<Long, Long> action = new Action<>(
                            ActionTypeEnums.LIKE.getAction(),
                            DefaultOperation.DELETE.getOperation(),
                            like.getUserId(),
                            like.getChirperId(),
                            System.currentTimeMillis()
                    );
                    log.warn("尝试重发:{}", action);
                    kafkaTemplate.send(LIKE_RECORD_TOPIC, action);
                });
            }
        });
    }

    /**
     * 修改推文的点赞数量
     * 处理来自Kafka的点赞数量变更消息
     */
    @Override
    public void modifyLikeCount(List<Action<Long, Long>> actions) {
        Map<Long, List<Action<Long, Long>>> collect =
                actions.stream().collect(Collectors.groupingBy(Action::getTarget));
        collect.forEach((chirperId, actionList) -> {
            int count = Action.getIncCount(actionList);
            try {
                RetryUtil.doDBRetry(() -> likeMapper.updateChirperLikeCount(chirperId, count));
            } catch (ExecutionException e) {
                log.error("修改点赞数时发生无法成功的错误，推文id:{}", chirperId, e);
            } catch (Exception e) {
                log.error("修改点赞数失败,推文id:{}，数量:{},错误:", chirperId, count, e);
                // 失败重试
                for (Action<Long, Long> action : actionList) {
                    log.warn("尝试重发:{}", action);
                    kafkaTemplate.send(LIKE_INCREMENT_COUNT_TOPIC, action);
                }
            }
        });
    }

    /**
     * 获取用户对推文的点赞信息
     */
    @Override
    public List<Long> getLikeInfo(Collection<Long> chirperIds, Long userId) {
        if (chirperIds == null || chirperIds.isEmpty() || userId == null) {
            return List.of();
        }
        return likeMapper.selectList(new LambdaQueryWrapper<Like>()
                        .select(Like::getChirperId)
                        .eq(Like::getUserId, userId)
                        .in(Like::getChirperId, chirperIds))
                .stream()
                .map(Like::getChirperId).toList();
    }

    /**
     * 获取用户的点赞记录
     * 分页查询
     */
    @Override
    public List<Like> getLikeRecord(Long userId, Integer page) {
        Page<Like> likePage = new Page<>(page, pageSize);
        likePage.setSearchCount(false);
        return likeMapper.selectPage(likePage, new LambdaQueryWrapper<Like>()
                        .eq(Like::getUserId, userId)
                        .orderByDesc(Like::getCreateTime))
                .getRecords();
    }

    /**
     * 生成点赞记录的缓存键
     */
    @Override
    public String getKey(LikeDto likeDto) {
        return likeDto.getUserId() + ":" + likeDto.getChirperId();
    }

    /**
     * 批量添加点赞记录
     */
    @Override
    public boolean addList(List<Like> likes) {
        return likeMapper.insertList(likes) > 0;
    }

    /**
     * 批量删除点赞记录
     */
    @Override
    public boolean deleteList(List<Like> likes) {
        return likeMapper.deleteList(likes) > 0;
    }

    /**
     * 根据推文ID删除相关的点赞记录
     */
    @Override
    public int deleteByChirperId(List<Long> chirperIds) {
        return likeMapper.delete(new LambdaQueryWrapper<Like>()
                .in(Like::getChirperId, chirperIds));
    }

    /**
     * 更新推文的点赞计数
     */
    @Override
    public boolean updateLikeCount(Long chirperId, Integer delta) {
        return likeMapper.updateChirperLikeCount(chirperId, delta) > 0;
    }
}
