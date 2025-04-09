package com.zyq.chirp.chirperserver.aspect;

import com.zyq.chirp.chirperserver.domain.enums.CacheKey;
import com.zyq.chirp.common.util.SpElUtil;
import jakarta.annotation.Resource;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 统计切面类
 * 负责处理带有@Statistic注解的方法
 * 在方法执行完成后自动更新Redis中的统计数据
 */
@Aspect
@Component
public class StatisticAspect {
    /**
     * Redis操作模板
     * 用于访问和操作Redis中的统计数据
     */
    @Resource
    RedisTemplate<String, Object> redisTemplate;

    /**
     * 定义切点
     * 匹配所有使用@Statistic注解的方法
     */
    @Pointcut("@annotation(com.zyq.chirp.chirperserver.aspect.Statistic)")
    public void pointcut() {
    }

    /**
     * 后置通知
     * 在目标方法成功执行后执行
     * 负责更新Redis中的统计数据
     *
     * @param joinPoint 连接点，用于获取方法的相关信息
     */
    @AfterReturning("pointcut()")
    public void after(JoinPoint joinPoint) {
        // 获取方法签名
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        // 获取注解信息
        Statistic annotation = method.getAnnotation(Statistic.class);
        // 遍历所有需要更新的统计类型
        for (CacheKey cacheKey : annotation.key()) {
            // 获取Redis Hash操作对象
            BoundHashOperations<String, String, Integer> operations = redisTemplate.boundHashOps(cacheKey.getKey());
            // 解析SpEL表达式获取统计对象ID
            String id = SpElUtil.generateKeyBySPEL(annotation.id(), joinPoint);
            // 增加统计值
            operations.increment(id, annotation.delta());
        }
    }
}
