package com.zyq.chirp.chirperserver.service;

import com.zyq.chirp.chirpclient.dto.ChirperDto;
import com.zyq.chirp.chirpclient.dto.ChirperQueryDto;
import com.zyq.chirp.chirperserver.domain.enums.ChirperStatus;
import com.zyq.chirp.chirperserver.domain.enums.ChirperType;
import com.zyq.chirp.common.mq.model.Action;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 推文服务接口
 * 提供推文的发布、回复、转发、引用等核心功能
 */
public interface ChirperService {
    /**
     * 发布新推文
     * @param chirperDto 推文内容，包含文本、媒体等信息
     * @return 发布成功的推文信息
     */
    ChirperDto save(ChirperDto chirperDto);

    /**
     * 回复推文
     * 根据回复权限设置（所有人、关注者、被提到的人）进行权限检查
     * @param chirperDto 回复内容，需包含被回复推文ID
     * @return 回复的推文信息
     */
    ChirperDto reply(ChirperDto chirperDto);

    /**
     * 修改推文的回复数量
     * @param actions 回复操作的集合
     */
    void modifyReplyCount(List<Action<Long, Long>> actions);

    /**
     * 获取被引用的推文
     * @param ids 被引用推文的ID集合
     * @return 推文ID到推文内容的映射
     */
    Map<Long, ChirperDto> fetchReference(Collection<Long> ids);

    /**
     * 获取用户与推文的互动信息
     * 包括是否点赞、转发、引用等状态
     * @param chirperDtos 推文列表
     * @param userId 用户ID
     * @return 包含互动信息的推文列表
     */
    List<ChirperDto> getInteractionInfo(List<ChirperDto> chirperDtos, Long userId);

    /**
     * 转发推文
     * @param chirperId 要转发的推文ID
     * @param userId 执行转发的用户ID
     */
    void forward(Long chirperId, Long userId);

    /**
     * 取消转发
     * @param chirperId 要取消转发的推文ID
     * @param userId 用户ID
     * @return 取消是否成功
     */
    boolean cancelForward(Long chirperId, Long userId);

    /**
     * 修改推文的转发数量
     * @param actions 转发操作的集合
     */
    void modifyForwardCount(List<Action<Long, Long>> actions);

    /**
     * 引用推文（带评论的转发）
     * @param chirperDto 引用内容，包含被引用推文ID和评论
     * @return 引用的推文信息
     */
    ChirperDto quote(ChirperDto chirperDto);

    /**
     * 修改推文的引用数量
     * @param actions 引用操作的集合
     */
    void modifyQuoteCount(List<Action<Long, Long>> actions);

    /**
     * 根据ID获取推文详情
     * @param chirperIds 推文ID列表
     * @return 推文详情列表
     */
    List<ChirperDto> getById(List<Long> chirperIds);

    /**
     * 分页查询推文
     * @param chirperQueryDto 查询条件，支持多种筛选
     * @return 符合条件的推文列表
     */
    List<ChirperDto> getPage(ChirperQueryDto chirperQueryDto);

    /**
     * 获取用户点赞的推文记录
     * @param userId 用户ID
     * @param page 页码
     * @return 用户点赞的推文列表
     */
    List<ChirperDto> getLikeRecordByUserId(Long userId, Integer page);

    /**
     * 更新推文状态
     * @param chirperId 推文ID
     * @param chirperStatus 新状态
     */
    void updateStatus(Long chirperId, ChirperStatus chirperStatus);

    /**
     * 获取推文的基本信息
     * @param chirperIds 推文ID集合
     * @return 推文基本信息列表
     */
    List<ChirperDto> getBasicInfo(Collection<Long> chirperIds);

    /**
     * 组合推文信息
     * 包括作者信息、媒体信息等
     * @param chirperDtos 推文列表
     * @return 完整的推文信息列表
     */
    List<ChirperDto> combine(Collection<ChirperDto> chirperDtos);

    /**
     * 获取推文趋势
     * @param page 页码
     * @param type 趋势类型
     * @return 趋势数据
     */
    Map<Object, Map<String, Object>> getTrend(Integer page, String type);

    /**
     * 获取作者的所有推文ID
     * @param userIds 用户ID集合
     * @return 用户ID到其推文ID列表的映射
     */
    Map<Long, List<Long>> getAllIdByAuthors(Collection<Long> userIds);

    /**
     * 获取用户关注的人的推文
     * @param userId 用户ID
     * @param size 获取数量
     * @return 关注用户的推文列表
     */
    List<ChirperDto> getByFollowerId(Long userId, Integer size);

    /**
     * 获取推文的互动权限状态
     * 检查用户是否可以回复、转发、引用、点赞等
     * @param chirperDtos 推文列表
     * @param userId 当前用户ID
     * @return 包含互动权限信息的推文列表
     */
    List<ChirperDto> getInteractionStatus(List<ChirperDto> chirperDtos, Long userId);

    /**
     * 处理推文的前置条件
     * 检查内容是否为空、设置回复范围等
     * @param chirperDto 推文信息
     * @return 处理后的推文信息
     */
    ChirperDto getWithPrecondition(ChirperDto chirperDto);

    /**
     * 处理延时发布的推文
     * @param chirperDto 推文信息
     */
    void postDelay(ChirperDto chirperDto);

    /**
     * 激活延时发布的推文
     * @param chirperIds 要激活的推文ID集合
     */
    void activeDelay(Collection<Long> chirperIds);

    /**
     * 自动激活到期的延时发布推文
     */
    void activeDelayAuto();
}
