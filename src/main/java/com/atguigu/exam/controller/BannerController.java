package com.atguigu.exam.controller;

// ============================================================================
// 【导入部分】
// ============================================================================
// 统一响应结果类 - 所有接口都返回统一的 JSON 格式
import com.atguigu.exam.common.Result;
// 轮播图实体类 - 对应数据库的 banners 表
import com.atguigu.exam.entity.Banner;
// 基础实体类 - 包含 id、createTime 等公共字段
import com.atguigu.exam.entity.BaseEntity;
// 轮播图服务接口 - 业务逻辑层
import com.atguigu.exam.service.BannerService;
// 文件上传服务接口 - 上传图片到 MinIO
import com.atguigu.exam.service.FileUploadService;
// MyBatis-Plus 的条件构造器 - 用于构建查询条件
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
// MyBatis-Plus 的更新条件构造器 - 用于构建更新条件
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
// Swagger 注解 - 用于生成 API 文档
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
// Lombok 的日志注解 - 自动生成 log 对象
import lombok.extern.slf4j.Slf4j;
// Spring 的依赖注入注解
import org.springframework.beans.factory.annotation.Autowired;
// Spring MVC 的 REST 控制器相关注解
import org.springframework.web.bind.annotation.*;
// 文件上传支持类
import org.springframework.web.multipart.MultipartFile;

// Java 集合类
import java.util.List;
import java.util.Map;

/**
 * ============================================================================
 * 【类级别注释】
 * ============================================================================
 * 轮播图控制器 - RESTful API 设计
 * 
 * 核心功能：
 * 1. 图片上传：将轮播图图片上传到 MinIO 对象存储服务器
 * 2. 轮播图查询：支持查询启用的轮播图（前台）和所有轮播图（后台）
 * 3. CRUD 操作：创建、读取、更新、删除轮播图记录
 * 4. 状态管理：启用/禁用轮播图的显示状态
 * 
 * 技术栈说明：
 * - Spring Boot 3.x：提供 RESTful API 框架
 * - MyBatis-Plus：ORM 框架，简化数据库操作
 * - Swagger/OpenAPI：自动生成 API 文档
 * - MinIO：分布式对象存储系统（类似阿里云 OSS）
 * 
 * HTTP 方法约定（RESTful 风格）：
 * - GET：查询操作（获取轮播图列表、详情）
 * - POST：创建操作（上传图片、添加轮播图）
 * - PUT：更新操作（修改轮播图信息、切换状态）
 * - DELETE：删除操作（删除轮播图）
 * 
 * @author 智能学习平台开发团队
 * @version 1.0
 * @since 2025-01-01
 */
@Slf4j  // Lombok 注解：自动生成 private static final Logger log 对象
@RestController  // Spring MVC 注解：标识这是一个 RESTful 控制器，返回 JSON 数据而非视图
@RequestMapping("/api/banners")  // 基础路径映射，所有请求都以/api/banners 为前缀
@CrossOrigin  // 允许跨域访问（前后端分离时，前端可以跨域调用后端接口）
@Tag(name = "轮播图管理", description = "轮播图相关操作，包括图片上传、轮播图增删改查、状态管理等功能")
public class BannerController {
    
    // ============================================================================
    // 【依赖注入】
    // ============================================================================
    @Autowired  // Spring 的依赖注入注解：自动装配 BannerService 的实现类
    private BannerService bannerService;  // 服务层对象，封装了所有数据库操作和业务逻辑

    @Autowired
    private FileUploadService fileUploadService;  // 文件上传服务，上传图片到 MinIO


    // ============================================================================
    // 【接口 1：图片上传】
    // ============================================================================
    /**
     * 上传轮播图图片到 MinIO 服务器
     * 
     * 业务流程：
     * 1. 接收前端上传的图片文件（MultipartFile 类型）
     * 2. 验证文件格式和大小（通常限制 jpg/png/gif，最大 5MB）
     * 3. 生成唯一的文件名（防止重名冲突）
     * 4. 调用 FileUploadService 将文件上传到 MinIO
     * 5. 返回图片的可访问 URL 地址
     * 
     * 为什么需要单独的图片上传接口？
     * - 图片是二进制文件，不能直接放在 JSON 中传输
     * - 先上传图片获得 URL，再将 URL 保存到数据库
     * - 符合单一职责原则，图片管理和数据管理分离
     * 
     * @param file 图片文件，由前端通过 multipart/form-data 格式上传
     * @return Result<String> 返回图片的访问 URL，例如："http://minio-server/images/banner_20250101_123.jpg"
     * 
     * HTTP 请求示例：
     * POST /api/banners/upload-image
     * Content-Type: multipart/form-data
     * file: [二进制图片数据]
     * 
     * 响应示例：
     * {
     *   "code": 200,
     *   "message": "图片上传成功",
     *   "data": "http://minio-server/images/banner_20250101_123.jpg"
     * }
     */
    @PostMapping("/upload-image")  // 映射 POST 请求到 /api/banners/upload-image
    @Operation(summary = "上传轮播图图片", description = "将图片文件上传到 MinIO 服务器，返回可访问的图片 URL")
    public Result<String> uploadBannerImage(
            @Parameter(description = "要上传的图片文件，支持 jpg、png、gif 等格式，大小限制 5MB")
            @RequestParam("file") MultipartFile file) {

        Map<String, Object> uploadResult = fileUploadService.uploadFile(file, "banners/");
        String imageUrl = uploadResult.get("url").toString();
        log.info("轮播图图片上传成功: {}", imageUrl);
        return Result.success(imageUrl, "图片上传成功");
    }
    
    // ============================================================================
    // 【接口 2：查询启用的轮播图（前台使用）】
    // ============================================================================
    /**
     * 获取所有状态为"启用"的轮播图
     * 
     * 应用场景：
     * - 前台首页展示轮播广告图
     * - 只展示 isActive=true 的轮播图
     * - 按 ID 升序排列（保证展示顺序稳定）
     * 
     * 为什么要区分前台和后台接口？
     * - 前台只需要启用的数据，避免暴露禁用的内容
     * - 后台需要看到所有数据，方便管理员管理
     * - 符合最小权限原则，提高安全性
     * 
     * @return Result<List<Banner>> 返回轮播图列表
     * 
     * SQL 查询示例：
     * SELECT * FROM banners WHERE is_active = true ORDER BY id ASC;
     * 
     * 响应示例：
     * {
     *   "code": 200,
     *   "message": "操作成功",
     *   "data": [
     *     {
     *       "id": 1,
     *       "title": "智能考试系统介绍",
     *       "imageUrl": "http://...",
     *       "isActive": true,
     *       ...
     *     }
     *   ]
     * }
     */
    @GetMapping("/active")  // 映射 GET 请求到 /api/banners/active
    @Operation(summary = "获取启用的轮播图", description = "获取状态为启用的轮播图列表，供前台首页展示使用")
    public Result<List<Banner>> getActiveBanners() {
        // 【步骤 1】构建查询条件
        // LambdaQueryWrapper：类型安全的条件构造器，编译时可以检查字段名是否正确
        LambdaQueryWrapper<Banner> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        // eq：等于条件，Banner::getIsActive 是方法引用，指向 isActive 字段
        lambdaQueryWrapper.eq(Banner::getIsActive, true);
        // orderByAsc：按 ID 升序排序，保证每次查询的顺序一致
        lambdaQueryWrapper.orderByAsc(BaseEntity::getId);
        
        // 【步骤 2】执行查询
        // bannerService.list()：MyBatis-Plus 提供的方法，等同于 SELECT * FROM banners
        // 注意：list() 方法可以传入条件构造器作为参数
        List<Banner> bannerList = bannerService.list(lambdaQueryWrapper);

        // 【步骤 3】记录日志（便于调试和监控）
        // log.info：INFO 级别的日志，记录重要的业务操作
        // {} 是占位符，会被后面的参数依次替换
        log.info("查询前台激活轮播图接口调用成功！查询数量为：{},具体的数据为：{}", bannerList.size(), bannerList);
        
        // 【步骤 4】返回结果
        // Result.success()：静态工厂方法，创建成功的响应对象
        return Result.success(bannerList);
    }
    
    // ============================================================================
    // 【接口 3：查询所有轮播图（后台使用）】
    // ============================================================================
    /**
     * 获取所有轮播图（包括启用和禁用的）
     * 
     * 应用场景：
     * - 管理后台的轮播图管理页面
     * - 管理员需要查看所有轮播图的状态
     * - 按 sortOrder 字段排序（自定义排序顺序）
     * 
     * 排序策略说明：
     * - sortOrder 越小越靠前（例如：1, 2, 3...）
     * - 管理员可以通过修改 sortOrder 来调整展示顺序
     * - 比按 ID 排序更灵活（ID 是自增的，无法调整）
     * 
     * @return Result<List<Banner>> 返回所有轮播图列表
     * 
     * SQL 查询示例：
     * SELECT * FROM banners ORDER BY sort_order ASC;
     */
    @GetMapping("/list")  // 映射 GET 请求到 /api/banners/list
    @Operation(summary = "获取所有轮播图", description = "获取所有轮播图列表，包括启用和禁用的，供管理后台使用")
    public Result<List<Banner>> getAllBanners() {
        // 【步骤 1】构建查询条件
        LambdaQueryWrapper<Banner> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        // 按 sortOrder 升序排序（数字小的排前面）
        lambdaQueryWrapper.orderByAsc(Banner::getSortOrder);
        
        // 【步骤 2】执行查询
        // 不传条件则查询所有记录
        List<Banner> bannerList = bannerService.list(lambdaQueryWrapper);
        
        // 【步骤 3】记录日志
        log.info("查询所有后台需要的轮播信息业务执行成功！结果为：{}", bannerList);
        
        // 【步骤 4】返回结果（带自定义成功消息）
        return Result.success(bannerList, "查询所有 banner 信息成功！");
    }
    
    // ============================================================================
    // 【接口 4：根据 ID 查询轮播图详情】
    // ============================================================================
    /**
     * 根据主键 ID 获取单个轮播图的详细信息
     * 
     * 应用场景：
     * - 管理后台编辑轮播图前，先加载详情
     * - 查看某个轮播图的完整信息
     * 
     * 路径变量 vs 请求参数：
     * - @PathVariable：从 URL 路径中提取参数（如 /api/banners/1）
     * - @RequestParam：从查询字符串获取参数（如 /api/banners?id=1）
     * - RESTful 风格推荐使用 PathVariable
     * 
     * @param id 轮播图的主键 ID，通过 URL 路径传递
     * @return Result<Banner> 返回单个轮播图对象
     * 
     * HTTP 请求示例：
     * GET /api/banners/1
     * 
     * 响应示例：
     * {
     *   "code": 200,
     *   "message": "操作成功",
     *   "data": {
     *     "id": 1,
     *     "title": "智能考试系统介绍",
     *     "description": "...",
     *     "imageUrl": "...",
     *     "linkUrl": "...",
     *     "sortOrder": 1,
     *     "isActive": true
     *   }
     * }
     */


    @GetMapping("/{id}")  // 映射 GET 请求，{id} 是路径变量占位符
    @Operation(summary = "根据 ID 获取轮播图", description = "根据轮播图 ID 获取单个轮播图的详细信息")
    public Result<Banner> getBannerById(
            @Parameter(description = "轮播图 ID")  // Swagger 注解：参数描述
            //是一个Swagger注解，用于为API方法的参数添加描述信息，使生成的API文档更加清晰易懂，有助于前后端开发人员之间的协作。
            @PathVariable Long id) {  // @PathVariable：将 URL 中的{id}绑定到 id 参数
        
        // 【步骤 1】根据 ID 查询
        // getById(id)：MyBatis-Plus 提供的方法，等同于 SELECT * FROM banners WHERE id = ?
        Banner banner = bannerService.getById(id);
        
        // 【步骤 2】记录日志
        log.info("查询 id={} 的轮播图接口调用成功！查询结果为：{}", id, banner);
        
        // 【步骤 3】返回结果
        return Result.success(banner);
    }
    
    // ============================================================================
    // 【接口 5：添加轮播图】
    // ============================================================================
    /**
     * 创建新的轮播图记录
     * 
     * 应用场景：
     * - 管理后台新增轮播图
     * - 需要先上传图片获得 URL，再调用此接口保存数据
     * 
     * 请求体参数说明：
     * - @RequestBody：从 HTTP 请求体（JSON）中读取数据
     * - 前端发送 JSON 格式的数据，Spring 自动反序列化为 Banner 对象
     * - 需要提供的字段：title, imageUrl, linkUrl, sortOrder, isActive 等
     * 
     * 前置条件：
     * - 图片已经上传并获得 URL
     * - 前端已经收集好所有表单数据
     * 
     * @param banner 轮播图对象（从 JSON 反序列化）
     * @return Result<String> 返回操作结果消息
     * 
     * HTTP 请求示例：
     * POST /api/banners/add
     * Content-Type: application/json
     * {
     *   "title": "双 11 活动宣传",
     *   "description": "...",
     *   "imageUrl": "http://...",
     *   "linkUrl": "http://...",
     *   "sortOrder": 1,
     *   "isActive": true
     * }
     * 
     * TODO: 需要实现的逻辑：
     * 1. 参数验证（必填字段检查）
     * 2. 设置默认值（isActive=true, sortOrder=最大排序 +1）
     * 3. 调用 bannerService.save(banner) 保存
     * 4. 返回成功消息
     */
    @PostMapping("/add")  // 映射 POST 请求到 /api/banners/add
    @Operation(summary = "添加轮播图", description = "创建新的轮播图，需要提供图片 URL、标题、跳转链接等信息")
    public Result<String> addBanner(@RequestBody Banner banner) {
        if (banner.getTitle() == null || banner.getTitle().trim().isEmpty()) {
            return Result.error("标题不能为空");
        }
        if (banner.getImageUrl() == null || banner.getImageUrl().trim().isEmpty()) {
            return Result.error("图片URL不能为空");
        }
        if (banner.getIsActive() == null) {
            banner.setIsActive(true);
        }
        if (banner.getSortOrder() == null) {
            banner.setSortOrder(0);
        }
        bannerService.save(banner);
        log.info("轮播图添加成功: id={}, title={}", banner.getId(), banner.getTitle());
        return Result.success("轮播图添加成功");
    }
    
    // ============================================================================
    // 【接口 6：更新轮播图】
    // ============================================================================
    /**
     * 更新已有轮播图的信息
     * 
     * 应用场景：
     * - 管理后台修改轮播图的标题、图片、链接等
     * - 调整轮播图的排序顺序
     * 
     * 更新策略：
     * - 全量更新：提供完整的 Banner 对象，所有字段都会被更新
     * - 根据 ID 匹配要更新的记录
     * - null 值的字段也会被更新为 NULL（如果需要部分更新，应该使用 Map）
     * 
     * 注意事项：
     * - banner 对象必须包含 id 字段，否则不知道更新哪条记录
     * - 图片更新需要先上传新图片，再更新 imageUrl 字段
     * 
     * @param banner 轮播图对象（包含 id 和要更新的字段）
     * @return Result<String> 返回操作结果消息
     * 
     * HTTP 请求示例：
     * PUT /api/banners/update
     * Content-Type: application/json
     * {
     *   "id": 1,
     *   "title": "updated title",
     *   "imageUrl": "...",
     *   "sortOrder": 2
     * }
     * 
     * TODO: 需要实现的逻辑：
     * 1. 验证 id 是否存在
     * 2. 调用 bannerService.updateById(banner)
     * 3. 返回成功消息
     */
    @PutMapping("/update")  // 映射 PUT 请求到 /api/banners/update
    @Operation(summary = "更新轮播图", description = "更新轮播图的信息，包括图片、标题、跳转链接、排序等")
    public Result<String> updateBanner(@RequestBody Banner banner) {
        if (banner.getId() == null) {
            return Result.error("缺少必需的 id 参数");
        }
        Banner existBanner = bannerService.getById(banner.getId());
        if (existBanner == null) {
            return Result.error("轮播图不存在");
        }
        bannerService.updateById(banner);
        log.info("轮播图更新成功: id={}, title={}", banner.getId(), banner.getTitle());
        return Result.success("轮播图更新成功");
    }
    
    // ============================================================================
    // 【接口 7：删除轮播图】
    // ============================================================================
    /**
     * 根据 ID 删除指定的轮播图记录
     * 
     * 应用场景：
     * - 管理后台删除不需要的轮播图
     * 
     * 删除策略选择：
     * - 物理删除：直接从数据库删除记录（当前实现）
     * - 逻辑删除：标记 is_deleted=1，不真正删除（推荐，可恢复）
     * 
     * 关联资源处理：
     * - 如果图片存储在 MinIO，是否需要同时删除图片？
     * - 如果有外键约束，需要先处理子表数据
     * 
     * @param id 轮播图 ID
     * @return Result<String> 返回删除结果消息
     * 
     * HTTP 请求示例：
     * DELETE /api/banners/delete/1
     * 
     * SQL 示例：
     * DELETE FROM banners WHERE id = 1;
     */
    @DeleteMapping("/delete/{id}")  // 映射 DELETE 请求到 /api/banners/delete/{id}
    @Operation(summary = "删除轮播图", description = "根据 ID 删除指定的轮播图")
    public Result<String> deleteBanner(
            @Parameter(description = "轮播图 ID") 
            @PathVariable Long id) {
        
        // 【步骤 1】执行删除操作
        // removeById(id)：MyBatis-Plus 提供的删除方法
        bannerService.removeById(id);
        
        // 【步骤 2】记录日志
        log.info("删除 id={} 的轮播图成功！！", id);
        
        // 【步骤 3】返回结果
        return Result.success("删除成功");
    }



    // ============================================================================
    // 【接口 8：切换轮播图状态】
    // ============================================================================
    /**
     * 启用或禁用指定的轮播图
     * 
     * 应用场景：
     * - 临时禁用某个轮播图（如下线过期的活动）
     * - 重新启用之前禁用的轮播图
     * 
     * 为什么不用更新接口而单独提供状态切换接口？
     * - 高频操作：状态切换很常用，独立接口更方便
     * - 语义清晰：toggle 比 update 更明确表达意图
     * - 简化参数：只需要传 id 和 isActive，不需要其他字段
     * 
     * 业务规则：
     * - isActive=true：轮播图在前台显示
     * - isActive=false：轮播图在前台隐藏
     * - 可以随时切换状态，无需审核
     * 
     * @param id 轮播图 ID
     * @param isActive 是否启用（true=启用，false=禁用）
     * @return Result<String> 返回操作结果消息
     * 
     * HTTP 请求示例：
     * PUT /api/banners/toggle/1?isActive=false
     * 
     * SQL 示例：
     * UPDATE banners SET is_active = false WHERE id = 1;
     */
    @PutMapping("/toggle/{id}")  // 映射 PUT 请求到 /api/banners/toggle/{id}
    @Operation(summary = "切换轮播图状态", description = "启用或禁用指定的轮播图，禁用后不会在前台显示")
    public Result<String> toggleBannerStatus(
            @Parameter(description = "轮播图 ID") @PathVariable Long id, 
            @Parameter(description = "是否启用，true 为启用，false 为禁用") @RequestParam Boolean isActive) {
        
        // 【步骤 1】构建更新条件
        // LambdaUpdateWrapper：用于构建 UPDATE 语句的条件和设置值
        LambdaUpdateWrapper<Banner> updateWrapper = new LambdaUpdateWrapper<>();
        // eq：设置 WHERE 条件，id = ?
        updateWrapper.eq(Banner::getId, id);
        // set：设置要更新的字段和值，is_active = ?
        updateWrapper.set(Banner::getIsActive, isActive);
        
        // 【步骤 2】执行更新
        // update(wrapper)：根据条件更新记录
        bannerService.update(updateWrapper);
        
        // 【步骤 3】记录日志
        log.info("id={} 轮播图，状态修改成功！修改后的状态为：{}", id, isActive);
        
        // 【步骤 4】返回结果
        return Result.success(null);  // data 为 null，因为不需要返回数据
    }

}
