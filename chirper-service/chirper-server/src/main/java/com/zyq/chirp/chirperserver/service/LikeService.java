package com.zyq.chirp.chirperserver.service;

import com.zyq.chirp.chirpclient.dto.LikeDto;
import com.zyq.chirp.chirperserver.domain.pojo.Like;
import com.zyq.chirp.common.mq.model.Action;

import java.util.Collection;
import java.util.List;

/**
 * 点赞服务接口
 * 提供推文点赞相关的功能，包括点赞、取消点赞、获取点赞信息等
 */
public interface LikeService {

    /**
     * 添加点赞
     * 用户对推文进行点赞操作
     * @param likeDto 点赞信息，包含用户ID和推文ID
     */
    void addLike(LikeDto likeDto);

    /**
     * 保存点赞记录
     * 批量处理点赞操作，更新数据库并发送相关通知
     * @param actions 点赞操作的集合
     */
    void saveLike(List<Action<Long, Long>> actions);

    /**
     * 取消点赞
     * 用户取消对推文的点赞
     * @param likeDto 点赞信息，包含用户ID和推文ID
     */
    void cancelLike(LikeDto likeDto);

    /**
     * 保存取消点赞的记录
     * 批量处理取消点赞操作，更新数据库并发送相关通知
     * @param actions 取消点赞操作的集合
     */
    void saveLikeCancel(List<Action<Long, Long>> actions);

    /**
     * 修改推文的点赞数量
     * @param actions 点赞操作的集合
     */
    void modifyLikeCount(List<Action<Long, Long>> actions);

    /**
     * 获取用户对推文的点赞信息
     * @param chirperIds 推文ID集合
     * @param userId 用户ID
     * @return 用户点赞过的推文ID列表
     */
    List<Long> getLikeInfo(Collection<Long> chirperIds, Long userId);

    /**
     * 获取用户的点赞记录
     * @param userId 用户ID
     * @param page 页码
     * @return 点赞记录列表
     */
    List<Like> getLikeRecord(Long userId, Integer page);

    /**
     * 生成点赞记录的缓存键
     * @param likeDto 点赞信息
     * @return 缓存键
     */
    String getKey(LikeDto likeDto);

    /**
     * 批量添加点赞记录
     * @param likes 点赞记录列表
     * @return 是否添加成功
     */
    boolean addList(List<Like> likes);

    /**
     * 批量删除点赞记录
     * @param likes 要删除的点赞记录列表
     * @return 是否删除成功
     */
    boolean deleteList(List<Like> likes);

    /**
     * 根据推文ID删除相关的点赞记录
     * @param chirperIds 推文ID列表
     * @return 删除的记录数
     */
    int deleteByChirperId(List<Long> chirperIds);

    /**
     * 更新推文的点赞计数
     * @param chirperId 推文ID
     * @param delta 点赞数变化值
     * @return 是否更新成功
     */
    boolean updateLikeCount(Long chirperId, Integer delta);
}
