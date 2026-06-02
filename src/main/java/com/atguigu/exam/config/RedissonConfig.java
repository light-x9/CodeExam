package com.atguigu.exam.config;

import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 分布式锁配置
 * 
 * ==================== 什么是 Redisson？ ====================
 * 
 * Redisson 是 Redis 的 Java 客户端，它不仅操作 Redis 基本数据类型，
 * 还封装了分布式锁（RLock）、布隆过滤器（RBloomFilter）等高级功能。
 * 
 * 和 RedisTemplate 的区别：
 *   RedisTemplate -> 底层操作（get/set/hash/zset），需要自己写锁逻辑
 *   Redisson      -> 高级封装（RLock/RBloomFilter），开箱即用
 * 
 * ==================== 分布式锁解决什么问题？ ====================
 * 
 * 场景：学生点击"提交考试"按钮
 *   前端做了防抖，但网络卡顿时学生刷新页面再次提交
 *   -> 两个请求同时到达后端
 *   -> 都通过了"考试状态=进行中"的检查
 *   -> 写入两条提交记录 -> 脏数据！
 * 
 * 分布式锁解决方案：
 *   请求1 -> 抢到锁(userId=1, examId=100) -> 处理 -> 释放锁
 *   请求2 -> tryLock失败，锁已被持有 -> 直接返回"请勿重复提交"
 * 
 * @author light
 */
@Slf4j
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    /**
     * 创建 RedissonClient Bean
     * destroyMethod = "shutdown"：容器关闭时自动释放资源
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        
        String address = "redis://" + redisHost + ":" + redisPort;
        
        String password = (redisPassword != null && !redisPassword.isEmpty()) 
                ? redisPassword : null;
        
        config.useSingleServer()
                .setAddress(address)
                .setPassword(password)
                .setConnectionPoolSize(16)
                .setConnectionMinimumIdleSize(4)
                .setConnectTimeout(3000)
                .setRetryAttempts(3)
                .setRetryInterval(1000);
        
        RedissonClient client = Redisson.create(config);
        log.info("RedissonClient 初始化成功: {}", address);
        return client;
    }
}