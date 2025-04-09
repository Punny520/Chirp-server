package com.zyq.chirp.chirperserver.aspect;

import com.zyq.chirp.chirperserver.domain.enums.CacheKey;
import org.intellij.lang.annotations.Language;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 统计注解，用于记录推文相关的统计数据
 * 使用Redis作为存储，通过AOP实现自动统计功能
 * 支持多种统计类型，如点赞数、转发数、浏览数等
 */
@Retention(RetentionPolicy.RUNTIME) // 注解在运行时可用
@Target(ElementType.METHOD) // 注解只能应用于方法
public @interface Statistic {
    /**
     * 统计对象的ID，使用SpEL表达式
     * 例如：#chirperId 表示使用方法参数中的chirperId作为统计对象的ID
     */
    @Language("SpEL")
    String id();

    /**
     * 统计值的增量
     * 默认为1，表示每次调用增加1
     * 可以设置为负数，表示减少统计值
     */
    int delta() default 1;

    /**
     * 统计数据的缓存键类型
     * 可以同时指定多个统计类型
     * 例如：同时统计点赞数和点赞信息
     */
    CacheKey[] key();
}
