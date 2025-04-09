package com.zyq.chirp.feedserver.service.impl;

import com.zyq.chirp.chirpclient.client.ChirperClient;
import com.zyq.chirp.chirpclient.dto.ChirperDto;
import com.zyq.chirp.common.util.PageUtil;
import com.zyq.chirp.feedclient.dto.FeedDto;
import com.zyq.chirp.feedserver.domain.enums.CacheKey;
import com.zyq.chirp.feedserver.service.FeedService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Feed流服务实现类
 * 使用Redis ZSet实现Feed流的存储和管理
 * key格式：feed:{userId}
 * score：推文创建时间戳
 * value：推文ID
 */
@Service
@Slf4j
public class FeedServiceImpl implements FeedService {
    /**
     * Redis操作模板
     */
    @Resource
    RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 推文服务客户端
     */
    @Resource
    ChirperClient chirperClient;
    
    /**
     * 分页大小
     */
    @Value("${default-config.page-size}")
    Integer pageSize;

    @Override
    public void initFeed(String targetId) {
        // 检查用户的feed流是否已初始化
        Long zCard = redisTemplate.opsForZSet().zCard(STR."\{CacheKey.FEED_BOUND_KEY.getKey()}:\{targetId}");
        if (zCard <= 0) {
            // 未初始化，从推文服务获取最近的推文
            int querySize = 1000;
            ResponseEntity<List<ChirperDto>> response = chirperClient.getByFollowerId(Long.valueOf(targetId), querySize);
            if (response.getStatusCode().is2xxSuccessful()) {
                List<ChirperDto> chirperDtoList = response.getBody();
                if (chirperDtoList != null && !chirperDtoList.isEmpty()) {
                    // 转换为Feed数据并批量添加
                    List<FeedDto> feedDtos = chirperDtoList.stream()
                            .map(chirperDto -> FeedDto.builder()
                                    .receiverId(targetId)
                                    .contentId(chirperDto.getId().toString())
                                    .publisher(chirperDto.getAuthorId().toString())
                                    .score((double) chirperDto.getCreateTime().getTime())
                                    .build())
                            .toList();
                    this.addFeedBatch(feedDtos);
                }
            }
        }
    }

    @Override
    public void addOne(FeedDto feedDto) {
        // 向用户的feed流中添加一条记录
        ZSetOperations<String, Object> operations = redisTemplate.opsForZSet();
        operations.add(STR. "\{ CacheKey.FEED_BOUND_KEY.getKey() }:\{ feedDto.getReceiverId() }" , feedDto.getContentId(), feedDto.getScore());
    }

    @Override
    public void addFeedBatch(Collection<FeedDto> feedDtos) {
        // 批量添加feed记录
        ZSetOperations<String, Object> operations = redisTemplate.opsForZSet();
        for (FeedDto feedDto : feedDtos) {
            try {
                operations.add(STR. "\{ CacheKey.FEED_BOUND_KEY.getKey() }:\{ feedDto.getReceiverId() }" , feedDto.getContentId(), System.currentTimeMillis());
            } catch (Exception e) {
                log.warn("{}", e);
            }
        }
    }

    @Override
    public void removeBatch(String receiverId, Collection<String> contentIds) {
        // 从用户的feed流中批量移除内容
        ZSetOperations<String, Object> operations = redisTemplate.opsForZSet();
        for (String id : contentIds) {
            try {
                operations.remove(STR. "\{ CacheKey.FEED_BOUND_KEY.getKey() }:\{ receiverId }" , id);
            } catch (Exception e) {
                log.warn("{}", e);
            }
        }
    }

    @Override
    public void removeBatch(Collection<FeedDto> feedDtos) {
        // 批量移除feed记录
        ZSetOperations<String, Object> operations = redisTemplate.opsForZSet();
        feedDtos.forEach(feedDto -> {
            try {
                operations.remove(STR. "\{ CacheKey.FEED_BOUND_KEY.getKey() }:\{ feedDto.getReceiverId() }" , feedDto.getContentId());
            } catch (Exception e) {
                log.warn("{}", e);
            }
        });
    }

    @Override
    public Collection<FeedDto> getPage(String receiverId, Integer page) {
        // 确保feed流已初始化
        this.initFeed(receiverId);
        // 计算分页偏移量
        int offset = PageUtil.getOffset(page, pageSize);
        ZSetOperations<String, Object> operations = redisTemplate.opsForZSet();
        Set<ZSetOperations.TypedTuple<Object>> feeds = operations.reverseRangeWithScores(STR. "\{ CacheKey.FEED_BOUND_KEY.getKey() }:\{ receiverId }" , offset, offset + pageSize);
        return feeds != null && !feeds.isEmpty() ? feeds.stream()
                .map(tuple -> FeedDto.builder()
                        .receiverId(receiverId)
                        .contentId((String) tuple.getValue())
                        .score(tuple.getScore())
                        .build())
                .toList() : List.of();
    }

    @Override
    public Collection<FeedDto> getPageByScore(String receiverId, Double score) {
        // 获取指定分数(时间)之前的数据
        ZSetOperations<String, Object> operations = redisTemplate.opsForZSet();
        Set<ZSetOperations.TypedTuple<Object>> feeds = operations.reverseRangeByScoreWithScores(STR."\{CacheKey.FEED_BOUND_KEY.getKey()}:\{receiverId}", Double.MIN_VALUE, score, 1, pageSize);
        return feeds != null && !feeds.isEmpty() ? feeds.stream()
                .map(tuple -> FeedDto.builder()
                        .receiverId(receiverId)
                        .contentId((String) tuple.getValue())
                        .score(tuple.getScore())
                        .build())
                .sorted(Comparator.comparingDouble(FeedDto::getScore).reversed())
                .toList() : List.of();
    }

    @Override
    public Collection<FeedDto> getRange(String receiverId, Double start, Double end) {
        // 获取指定时间范围内的数据
        ZSetOperations<String, Object> operations = redisTemplate.opsForZSet();
        Set<ZSetOperations.TypedTuple<Object>> feeds = operations.rangeByScoreWithScores(STR. "\{ CacheKey.FEED_BOUND_KEY.getKey() }:\{ receiverId }" , start, end);
        return feeds != null && !feeds.isEmpty() ? feeds.stream()
                .map(tuple -> FeedDto.builder()
                        .receiverId(receiverId)
                        .contentId((String) tuple.getValue())
                        .score(tuple.getScore())
                        .build())
                .sorted(Comparator.comparingDouble(FeedDto::getScore).reversed())
                .toList() : List.of();
    }
}
