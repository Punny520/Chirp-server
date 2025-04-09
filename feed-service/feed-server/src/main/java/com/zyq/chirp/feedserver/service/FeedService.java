package com.zyq.chirp.feedserver.service;

import com.zyq.chirp.feedclient.dto.FeedDto;

import java.util.Collection;

/**
 * Feed流服务接口
 * 负责管理用户的信息流，包括初始化、添加、删除和查询等操作
 * 使用Redis的ZSet结构存储，以时间戳为分数进行排序
 */
public interface FeedService {
    /**
     * 初始化用户的feed流
     * 当用户首次访问feed流时，从数据库中加载最近的推文
     *
     * @param targetId 目标用户id
     */
    void initFeed(String targetId);

    /**
     * 添加单条feed记录
     * 用于实时推送新的内容到用户的feed流中
     *
     * @param feedDto feed数据，包含接收者、发布者、内容ID和时间分数
     */
    void addOne(FeedDto feedDto);

    /**
     * 批量添加feed记录
     * 用于批量推送内容，如初始化feed流时
     *
     * @param feedDtos feed数据集合
     */
    void addFeedBatch(Collection<FeedDto> feedDtos);

    /**
     * 批量移除指定接收者的feed记录
     * 当用户发生取关等事件时，需要将对应被取关者的内容移除
     *
     * @param receiverId 接收者ID
     * @param contentIds 要移除的内容ID集合
     */
    void removeBatch(String receiverId, Collection<String> contentIds);

    /**
     * 批量移除feed记录
     * 根据完整的feed数据进行移除
     *
     * @param feedDtos 要移除的feed数据集合
     */
    void removeBatch(Collection<FeedDto> feedDtos);

    /**
     * 分页获取用户的feed流
     * 如果用户的feed流未初始化，会先进行初始化
     *
     * @param receiverId 接收者ID
     * @param page 页码
     * @return feed数据集合
     */
    Collection<FeedDto> getPage(String receiverId, Integer page);

    /**
     * 根据分数(时间戳)获取feed流
     * 获取指定时间之前的内容，用于加载更多
     *
     * @param receiverId 接收者ID
     * @param score 时间分数
     * @return feed数据集合
     */
    Collection<FeedDto> getPageByScore(String receiverId, Double score);

    /**
     * 获取指定时间范围内的feed流
     *
     * @param receiverId 接收者ID
     * @param start 起始时间分数
     * @param end 结束时间分数
     * @return feed数据集合
     */
    Collection<FeedDto> getRange(String receiverId, Double start, Double end);
}
