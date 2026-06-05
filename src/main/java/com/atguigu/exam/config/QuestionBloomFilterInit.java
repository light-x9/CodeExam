package com.atguigu.exam.config;

import com.atguigu.exam.mapper.QuestionMapper;
import com.atguigu.exam.utils.RedisUtils;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnel;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.List;

/**
 * 题目布隆过滤器 —— 防止缓存穿透
 * 
 * <h3>什么是缓存穿透？</h3>
 * 攻击者/用户大量请求不存在的题目ID，缓存和数据库中都没有，
 * 每次请求都穿透缓存直接打DB，可能把数据库压垮。
 * 
 * <h3>布隆过滤器如何解决？</h3>
 * 布隆过滤器是一个"可能存在"或"一定不存在"的数据结构：
 * - 如果布隆说"不存在" → 100%确定不存在，直接返回404，不用查DB
 * - 如果布隆说"可能存在" → 有极低概率误判（约1%），需要再查缓存/DB
 * 
 * <h3>数据流：</h3>
 * 请求 → 布隆过滤器判断 → 不存在？直接返回404
 *                        → 可能存在？→ 查缓存 → 缓存有？返回
 *                                          → 缓存无？→ 查DB → 返回
 * 
 * <h3>存储设计：</h3>
 * - 主存储：Redis（byte[]序列化），多实例共享
 * - 本地缓存：volatile字段，减少Redis反序列化开销
 * - 降级策略：Redis不可用时，使用本地内存布隆过滤器
 * 
 * @author light
 */
@Slf4j
@Component
public class QuestionBloomFilterInit {

    /** Redis中存储布隆过滤器的key */
    private static final String REDIS_KEY = "bloom:question:filter";

    /** 预期题目数量（用于初始化布隆过滤器大小） */
    private static final int EXPECTED_INSERTIONS = 100_000;

    /** 误判率：1% */
    private static final double FPP = 0.01;

    @Autowired
    private QuestionMapper questionMapper;

    @Autowired
    private RedisUtils redisUtils;

    /** 本地缓存，避免每次请求都从Redis反序列化 */
    private volatile BloomFilter<Long> localFilter;

    /**
     * 布隆过滤器的Funnel —— 告诉Guava如何将Long对象"喂"给哈希函数
     * 这里直接把long值写入，简单高效
     */
    private static final Funnel<Long> LONG_FUNNEL = (from, into) -> into.putLong(from);

    /**
     * 项目启动时自动执行 —— 从DB加载全量ID，构建布隆过滤器
     */
    @PostConstruct
    public void init() {
        log.info("=== 布隆过滤器初始化开始 ===");
        try {
            rebuildBloomFilter();
            log.info("=== 布隆过滤器初始化完成 ===");
        } catch (Exception e) {
            log.error("布隆过滤器初始化失败，将使用降级策略", e);
            // 降级：在内存中创建一个空的布隆过滤器
            localFilter = createEmptyFilter();
        }
    }

    /**
     * 定时重建 —— 每天凌晨3点，防止DB数据变更导致布隆过滤器过期
     * cron表达式：秒 分 时 日 月 周
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void scheduledRebuild() {
        log.info("定时任务触发：开始重建布隆过滤器");
        try {
            rebuildBloomFilter();
            log.info("定时任务完成：布隆过滤器已重建");
        } catch (Exception e) {
            log.error("定时重建布隆过滤器失败", e);
        }
    }

    /**
     * 判断题目ID是否可能存在
     * 
     * @param id 题目ID
     * @return true=可能存在（需要继续查缓存/DB），false=一定不存在（直接返回404）
     */
    public boolean mightContain(Long id) {
        BloomFilter<Long> filter = getFilter();
        if (filter == null) {
            // 完全不可用，放行（避免误拦截正常请求）
            return true;
        }
        return filter.mightContain(id);
    }

    /**
     * 新增题目时，将ID加入布隆过滤器
     * 
     * @param id 新增的题目ID
     */
    public void add(Long id) {
        BloomFilter<Long> filter = getFilter();
        if (filter != null) {
            filter.put(id);
            // 同步到Redis，让其他实例也能感知到
            saveToRedis(filter);
        }
    }

    // ======================== 内部方法 ========================

    /**
     * 获取当前可用的布隆过滤器
     * 优先级：Redis > 本地缓存 > 本地空过滤器
     */
    private BloomFilter<Long> getFilter() {
        // 1. 先查本地缓存
        if (localFilter != null) {
            return localFilter;
        }

        // 2. 尝试从Redis加载
        try {
            localFilter = loadFromRedis();
            if (localFilter != null) {
                log.debug("从Redis加载布隆过滤器成功");
                return localFilter;
            }
        } catch (Exception e) {
            log.warn("Redis不可用，无法加载布隆过滤器，使用本地降级策略", e);
        }

        // 3. Redis没有或不可用，创建本地空过滤器作为降级
        localFilter = createEmptyFilter();
        return localFilter;
    }

    /**
     * 核心：重建布隆过滤器
     * 1. 从DB查询全量题目ID
     * 2. 构建BloomFilter
     * 3. 保存到Redis
     * 4. 更新本地缓存
     */
    private void rebuildBloomFilter() {
        // ① 从DB加载全量ID
        List<Long> allIds = questionMapper.selectAllIds();
        log.info("从DB加载到 {} 个题目ID", allIds.size());

        // ② 构建布隆过滤器
        // 预期数量取DB实际数量的2倍（留余量），最少10000
        long expectedSize = Math.max(allIds.size() * 2L, EXPECTED_INSERTIONS);
        BloomFilter<Long> filter = BloomFilter.create(LONG_FUNNEL, expectedSize, FPP);

        // 将每个ID加入过滤器
        for (Long id : allIds) {
            filter.put(id);
        }

        // ③ 保存到Redis
        saveToRedis(filter);

        // ④ 更新本地缓存
        localFilter = filter;

        log.info("布隆过滤器重建完成: 预期容量={}, 实际数量={}, 误判率={}", expectedSize, allIds.size(), FPP);
    }

    /**
     * 将布隆过滤器序列化并保存到Redis
     */
    private void saveToRedis(BloomFilter<Long> filter) {
        try {
            byte[] bytes = serialize(filter);
            redisUtils.set(REDIS_KEY, bytes);
            log.debug("布隆过滤器已保存到Redis, key={}, size={}bytes", REDIS_KEY, bytes.length);
        } catch (Exception e) {
            log.error("保存布隆过滤器到Redis失败", e);
        }
    }

    /**
     * 从Redis加载布隆过滤器
     */
    @SuppressWarnings("unchecked")
    private BloomFilter<Long> loadFromRedis() {
        try {
            Object obj = redisUtils.get(REDIS_KEY);
            if (obj == null) {
                return null;
            }
            byte[] bytes;
            if (obj instanceof byte[]) {
                bytes = (byte[]) obj;
            } else {
                log.warn("Redis中布隆过滤器数据类型异常: {}", obj.getClass().getName());
                return null;
            }
            return deserialize(bytes);
        } catch (Exception e) {
            log.error("从Redis反序列化布隆过滤器失败", e);
            return null;
        }
    }

    /**
     * Java原生序列化 —— 将BloomFilter转为byte[]
     */
    private byte[] serialize(BloomFilter<Long> filter) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(filter);
        }
        return baos.toByteArray();
    }

    /**
     * Java原生反序列化 —— 将byte[]还原为BloomFilter
     */
    @SuppressWarnings("unchecked")
    private BloomFilter<Long> deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (BloomFilter<Long>) ois.readObject();
        }
    }

    /**
     * 创建空的布隆过滤器（终极降级）
     */
    private BloomFilter<Long> createEmptyFilter() {
        log.warn("创建空布隆过滤器作为降级，所有请求将直接放行到DB");
        return BloomFilter.create(LONG_FUNNEL, EXPECTED_INSERTIONS, FPP);
    }
}
