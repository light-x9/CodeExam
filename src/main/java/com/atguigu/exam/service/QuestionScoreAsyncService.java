package com.atguigu.exam.service;

import com.atguigu.exam.common.CacheConstants;
import com.atguigu.exam.utils.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 题目相关异步任务服务
 * <p>
 * 为什么要把异步方法抽到独立的 Component？
 * - Spring 的 @Async 依赖 AOP 代理，类内部调用（this.method()）不会触发代理，@Async 不生效
 * - 抽到独立的 Bean 后，通过注入调用，走的是 Spring 代理，@Async 才能被拦截并提交到线程池
 */
@Slf4j
@Component
public class QuestionScoreAsyncService {

    @Autowired
    private RedisUtils redisUtils;

    /**
     * 异步更新题目热榜分数（Redis ZSet）
     * <p>
     * @Async("questionScoreExecutor") 的两个作用：
     * 1. 方法提交到 "questionScoreExecutor" 线程池异步执行
     * 2. 调用方（HTTP 请求线程）立即返回，不阻塞查询接口响应
     *
     * @param questionId 题目ID
     */
    @Async("questionScoreExecutor")
    public void incrementQuestionScore(Long questionId) {
        Double score = redisUtils.zIncrementScore(
                CacheConstants.POPULAR_QUESTIONS_KEY, questionId, 1);
        log.debug("完成 id:{} 题目的热榜分数累计，累计后的分数为：{}", questionId, score);
    }
}
