package com.zyq.chirp.chirperserver.domain.enums;

/**
 * Redis缓存键枚举
 * 定义了推文服务中使用的各种Redis缓存键
 */
public enum CacheKey {
    /**
     * 推文点赞信息缓存键
     * 记录用户对推文的点赞状态
     */
    LIKE_INFO_BOUND_KEY("chirper:like"),

    /**
     * 推文点赞数量缓存键
     * 记录每条推文的点赞总数
     */
    LIKE_COUNT_BOUND_KEY("count:like"),

    /**
     * 推文转发数量缓存键
     * 记录每条推文的转发总数
     */
    FORWARD_COUNT_BOUND_KEY("count:forward:add"),

    /**
     * 推文浏览数量缓存键
     * 记录每条推文的浏览总数
     */
    VIEW_COUNT_BOUND_KEY("count:view"),

    /**
     * 推文转发信息缓存键
     * 记录用户对推文的转发状态
     */
    FORWARD_INFO_BOUND_KEY("chirper:forward"),

    /**
     * 话题趋势缓存键
     * 记录热门话题的排名信息
     */
    TEND_TAG_BOUND_KEY("trend:tag"),

    /**
     * 推文趋势缓存键
     * 记录热门推文的排名信息
     */
    TEND_POST_BOUND_KEY("trend:post"),

    /**
     * 延迟发布推文缓存键
     * 用于存储待发布的定时推文
     */
    DELAY_POST_KEY("chirper:delay");

    /**
     * 缓存键的实际值
     */
    private final String key;

    /**
     * 构造函数
     *
     * @param key 缓存键的实际值
     */
    CacheKey(String key) {
        this.key = key;
    }

    /**
     * 获取缓存键的实际值
     *
     * @return 缓存键字符串
     */
    public String getKey() {
        return key;
    }
}
