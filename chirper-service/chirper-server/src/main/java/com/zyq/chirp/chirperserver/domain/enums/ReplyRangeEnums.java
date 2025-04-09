package com.zyq.chirp.chirperserver.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 推文回复权限范围枚举
 * 定义了谁可以回复推文的权限设置
 */
@AllArgsConstructor
@Getter
public enum ReplyRangeEnums {
    /**
     * 所有人都可以回复
     * 默认的回复权限设置
     */
    EVERYONE(1),

    /**
     * 只有关注该推文作者的用户可以回复
     * 用于限制回复者必须是粉丝
     */
    FOLLOWING(2),

    /**
     * 只有在推文中被@提到的用户可以回复
     * 最严格的回复限制
     */
    MENTION(3);

    /**
     * 权限代码
     */
    private final int code;

    /**
     * 根据权限代码查找对应的枚举值
     * @param code 权限代码
     * @return 对应的枚举值，如果未找到则返回null
     */
    public static ReplyRangeEnums findByCode(int code) {
        for (ReplyRangeEnums enums : ReplyRangeEnums.values()) {
            if (enums.getCode() == code) {
                return enums;
            }
        }
        return null;
    }

    /**
     * 根据权限代码查找对应的枚举值，如果未找到则返回默认值(EVERYONE)
     * @param code 权限代码
     * @return 对应的枚举值或默认值
     */
    public static ReplyRangeEnums findByCodeWithDefault(int code) {
        ReplyRangeEnums rangeEnums = findByCode(code);
        return rangeEnums != null ? rangeEnums : ReplyRangeEnums.EVERYONE;
    }

    /**
     * 处理可能为null的权限代码
     * @param code 权限代码
     * @return 对应的枚举值或默认值
     */
    public static ReplyRangeEnums findByCodeWithDefault(Integer code) {
        if (code == null) {
            return ReplyRangeEnums.EVERYONE;
        }
        return findByCodeWithDefault(code.intValue());
    }

    /**
     * 获取权限范围的中文描述
     * @param code 权限代码
     * @return 对应的中文描述
     */
    public static String getHint(int code) {
        return switch (findByCodeWithDefault(code)) {
            case EVERYONE -> "所有人";
            case FOLLOWING -> "关注";
            case MENTION -> "提及";
        };
    }
}
