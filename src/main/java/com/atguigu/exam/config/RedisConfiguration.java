package com.atguigu.exam.config;// 导入Spring配置相关的注解
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 导入Redis核心操作类、连接工厂
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.connection.RedisConnectionFactory;

// 导入Redis序列化器（核心，解决序列化乱码问题）
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * projectName: exam_system_server_online
 * @author: 赵伟风
 * description: redis的配置类，核心作用是【自定义Redis的序列化规则】
 * 解决Spring默认RedisTemplate的JDK序列化带来的乱码、可读性差、兼容性差的问题
 */
// @Configuration 注解：标记这个类是Spring的配置类，项目启动时会自动加载、执行这个类里的配置
@Configuration
public class RedisConfiguration {

    /**
     * 自定义RedisTemplate对象，交给SpringIOC容器管理
     * 后续在Service/Controller里直接@Autowired注入就能使用
     * @param factory Redis连接工厂：SpringBoot会自动读取application.yml里的redis配置，自动创建并注入这个对象
     * @return 配置好序列化规则的RedisTemplate模板对象
     */
    @Bean // @Bean注解：把方法返回的对象放到Spring容器里，成为全局可注入的Bean
    public RedisTemplate<String,Object> redisTemplate(RedisConnectionFactory factory) {
        // 1. 创建RedisTemplate核心对象，Redis的所有增删改查操作，都通过这个对象的API来完成
        RedisTemplate<String,Object> redisTemplate = new RedisTemplate<>();

        // 2. 给RedisTemplate设置连接工厂
        // 连接工厂里封装了Redis的IP、端口、密码、连接池等所有连接信息，对应你在application.yml里写的redis配置
        redisTemplate.setConnectionFactory(factory);

        // -------------------------- 核心：序列化器配置 --------------------------
        // 序列化：把Java对象转换成可以在Redis里存储的格式（字符串/字节）
        // 反序列化：把Redis里存储的数据，转回Java对象
        // 不自定义序列化的话，Spring默认用JDK序列化，存到Redis里的key/value会变成乱码，可读性极差

        // 3.1 字符串序列化器：专门处理字符串类型的key/字段名
        // 采用UTF-8编码，保证Redis里的key不会乱码，在可视化工具里能直接看懂
        StringRedisSerializer stringRedisSerializer = StringRedisSerializer.UTF_8;

        // 3.2 JSON序列化器：专门处理Java对象类型的value
        // 作用：自动把Java对象序列化成JSON格式的字符串存到Redis里，读取时自动把JSON转回Java对象
        // 优势1：可读性极强，在Redis可视化工具里能直接看到完整的JSON内容，方便调试
        // 优势2：跨语言兼容性好，Python/Go等其他语言也能正常读取这个JSON数据
        // 优势3：GenericJackson2JsonRedisSerializer 会自动处理泛型、多态类型，比普通Jackson序列化器更通用、不易出错
        GenericJackson2JsonRedisSerializer jsonRedisSerializer = new GenericJackson2JsonRedisSerializer();

        // 4. 给RedisTemplate的不同部分，设置对应的序列化规则

        // 4.1 普通key（比如 set name 张三 里的name）的序列化规则：用字符串序列化器
        redisTemplate.setKeySerializer(stringRedisSerializer);
        // 4.2 Hash类型的字段名（比如 hset user:1 name 张三 里的name）的序列化规则：用字符串序列化器
        redisTemplate.setHashKeySerializer(stringRedisSerializer);

        // 4.3 普通value（比如 set name 张三 里的张三）的序列化规则：用JSON序列化器
        redisTemplate.setValueSerializer(jsonRedisSerializer);
        // 4.4 Hash类型的字段值（比如 hset user:1 name 张三 里的张三）的序列化规则：用JSON序列化器
        redisTemplate.setHashValueSerializer(jsonRedisSerializer);

        // 5. 返回配置完成的RedisTemplate对象，Spring会自动把这个对象存入容器，后续直接注入使用即可
        return redisTemplate;
    }
}