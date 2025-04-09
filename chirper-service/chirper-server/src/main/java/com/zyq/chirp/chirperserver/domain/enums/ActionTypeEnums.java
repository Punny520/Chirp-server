package com.zyq.chirp.chirperserver.domain.enums;

import com.zyq.chirp.common.mq.enums.ActionType;

/**
 * 推文互动类型枚举
 * 定义了用户对推文的各种互动行为类型
 */
public enum ActionTypeEnums implements ActionType {
    /**
     * 点赞行为
     */
    LIKE,
    
    /**
     * 转发行为
     */
    FORWARD,
    
    /**
     * 回复行为
     */
    REPLY,
    
    /**
     * 引用行为（带评论的转发）
     */
    QUOTE;

    /**
     * 获取行为类型的字符串表示
     * 实现自ActionType接口
     *
     * @return 行为类型的名称
     */
    @Override
    public String getAction() {
        return this.name();
    }
}
