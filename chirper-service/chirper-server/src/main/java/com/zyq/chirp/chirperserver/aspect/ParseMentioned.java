package com.zyq.chirp.chirperserver.aspect;

import org.intellij.lang.annotations.Language;

import java.lang.annotation.*;

/**
 * 解析推文内容的注解
 * 主要用于：
 * 1. 检测推文中被@的用户，用于发送提醒通知
 * 2. 解析推文中的标签(#话题)，用于话题统计和趋势分析
 * 3. 处理推文发布后的feed流更新
 */
@Retention(RetentionPolicy.RUNTIME) // 注解在运行时可用
@Target(ElementType.METHOD) // 注解只能应用于方法
@Documented // 注解包含在JavaDoc中
public @interface ParseMentioned {
    /**
     * SpEL表达式，用于获取需要解析的内容
     * 默认为空字符串，表示使用默认解析逻辑
     */
    @Language("SpEL")
    String value() default "";
}
