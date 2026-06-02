package com.atguigu.exam.controller;

import com.atguigu.exam.common.Result;
import com.atguigu.exam.entity.Category;
import com.atguigu.exam.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ============================================================================
 * 【类级别注释】
 * ============================================================================
 * 分类控制器 - RESTful API 设计
 * 
 * 核心功能：
 * 1. 分类查询：获取所有分类列表（包含题目数量统计）
 * 2. 树形结构：获取分类的层级树形结构（支持多级分类）
 * 3. CRUD 操作：创建、读取、更新、删除分类
 * 4. 层级管理：支持父子分类关系，实现树形分类体系
 * 
 * 应用场景：
 * - 前台：选择题目的分类（如 Java 基础、数据库、前端开发等）
 * - 后台：管理员维护分类体系，调整分类结构
 * 
 * 分类树结构设计：
 * - parentId = 0：表示顶级分类（一级分类）
 * - parentId = 其他值：表示子分类（二级、三级...）
 * - children 字段：存储子分类列表，构建树形结构
 * 
 * 技术要点：
 * - 使用 MyBatis-Plus 进行数据库操作
 * - 使用 Swagger 生成 API 文档
 * - 统一返回 Result<T>格式
 * 
 * @author light
 * @version 1.0
 * @since 2025-01-01
 */
@Slf4j  // Lombok 注解：自动生成 private static final Logger log 对象，用于日志输出
@CrossOrigin("*")  // 允许跨域访问，*表示允许所有来源的跨域请求
@RestController  // Spring MVC 注解：标识这是一个 RESTful 控制器，返回 JSON 数据而非视图
@RequestMapping("/api/categories")  // 基础路径映射，所有请求都以/api/categories 为前缀
// @Tag 注解：定义 API 文档中的标签，用于分类不同的 API 操作
// 这里将分类管理相关的 API 操作归为一个标签，方便在 Swagger 文档中查看
@Tag(name = "分类管理", description = "题目分类相关操作，包括分类的增删改查、树形结构管理等功能")
public class CategoryController {

    // ============================================================================
    // 【依赖注入】
    // ============================================================================
    @Autowired  // Spring 的依赖注入注解：自动装配 CategoryService 的实现类
    private CategoryService categoryService;  // 服务层对象，封装了所有数据库操作和业务逻辑
    
    // ============================================================================
    // 【接口 1：获取分类列表（含题目数量）】
    // ============================================================================
    /**
     * 获取所有分类列表（包含每个分类下的题目数量统计）
     * 
     * 应用场景：
     * - 管理后台的分类管理页面，展示所有分类及其题目数量
     * - 可以看到每个分类下有多少道题目，便于管理员了解分类使用情况
     * 
     * 与 tree 接口的区别：
     * - 此接口：平铺展示所有分类，不体现层级关系，但包含题目数量
     * - tree 接口：树形展示层级结构，但不包含题目数量
     * 
     * 数据处理流程：
     * 1. Controller 调用 Service 的 getAllCategories() 方法
     * 2. Service 层查询所有分类
     * 3. Service 层统计每个分类下的题目数量（关联 question 表）
     * 4. 将统计结果设置到 Category 对象的 count 字段
     * 5. 返回给 Controller，再返回给前端
     * 
     * @return Result<List<Category>> 返回分类列表，每个分类包含 count 字段（题目数量）
     * 
     * SQL 查询示例：
     * SELECT c.*, COUNT(q.id) as count 
     * FROM categories c 
     * LEFT JOIN questions q ON c.id = q.category_id 
     * GROUP BY c.id 
     * ORDER BY c.sort ASC;
     * 
     * 响应示例：
     * {
     *   "code": 200,
     *   "message": "操作成功",
     *   "data": [
     *     {
     *       "id": 1,
     *       "name": "Java 基础",
     *       "parentId": 0,
     *       "sort": 1,
     *       "count": 25  // 该分类下有 25 道题目
     *     },
     *     {
     *       "id": 2,
     *       "name": "集合框架",
     *       "parentId": 1,
     *       "sort": 1,
     *       "count": 10  // 该分类下有 10 道题目
     *     }
     *   ]
     * }
     */
    @GetMapping  // 映射 GET 请求到 /api/categories（默认路径）
    //@Operation 注解：定义 API 文档中的操作，包含 summary 和 description
    @Operation(summary = "获取分类列表", description = "获取所有题目分类列表，包含每个分类下的题目数量统计")
    public Result<List<Category>> getCategories() {
        // 【步骤 1】调用 Service 层获取处理后的数据
        // getAllCategories() 方法会：
        // 1. 查询所有分类
        // 2. 统计每个分类的题目数量
        // 3. 将数量设置到 Category.count 字段
        List<Category> allCategories = categoryService.getAllCategories();
        
        // 【步骤 2】封装成统一响应格式返回
        // Result.success() 创建成功的响应对象，data 字段包含分类列表
        return Result.success(allCategories);
    }

    // ============================================================================
    // 【接口 2：获取分类树形结构】
    // ============================================================================
    /**
     * 获取分类的树形层级结构
     * 
     * 应用场景：
     * - 前台选择题目的分类时，需要树形结构展示（如树形下拉框）
     * - 后台管理分类时，树形展示更直观，可以看到父子关系
     * 
     * 树形结构构建原理：
     * 1. 查询所有分类（平铺数据）
     * 2. 遍历所有分类，找出 parentId=0 的作为根节点（一级分类）
     * 3. 再次遍历，将子分类添加到父分类的 children 列表中
     * 4. 最终形成树形结构（一级 -> 二级 -> 三级...）
     * 
     * 递归结构说明：
     * Category 类中包含 List<Category> children 字段
     * - 一级分类的 children 包含所有二级分类
     * - 二级分类的 children 包含所有三级分类
     * - 以此类推，形成递归结构
     * 
     * @return Result<List<Category>> 返回树形结构的分类列表
     * 
     * 响应示例（JSON 格式的树形结构）：
     * {
     *   "code": 200,
     *   "message": "操作成功",
     *   "data": [
     *     {
     *       "id": 1,
     *       "name": "编程语言",
     *       "parentId": 0,
     *       "children": [
     *         {
     *           "id": 2,
     *           "name": "Java",
     *           "parentId": 1,
     *           "children": [
     *             {
     *               "id": 5,
     *               "name": "Java 基础",
     *               "parentId": 2,
     *               "children": null
     *             }
     *           ]
     *         },
     *         {
     *           "id": 3,
     *           "name": "Python",
     *           "parentId": 1,
     *           "children": null
     *         }
     *       ]
     *     }
     *   ]
     * }
     * 
     * 前端如何使用？
     * - Vue/React 的树形组件（如 Element UI 的 Tree 组件）
     * - 递归渲染树形结构
     * - 支持展开/折叠节点
     */
    @GetMapping("/tree")  // 映射 GET 请求到 /api/categories/tree
    @Operation(summary = "获取分类树形结构", description = "获取题目分类的树形层级结构，用于前端树形组件展示")
    public Result<List<Category>> getCategoryTree() {
        // 【步骤 1】调用 Service 层获取树形结构
        // getCategoryTree() 方法会：
        // 1. 查询所有分类
        // 2. 构建父子关系（通过 parentId 关联）
        // 3. 将子分类放入父分类的 children 列表
        // 4. 返回只包含一级分类（根节点）的列表，但每个根节点包含完整的子树
        List<Category> categoryTreeList = categoryService.getCategoryTree();
        
        // 【步骤 2】返回结果
        return Result.success(categoryTreeList);
    }

    // ============================================================================
    // 【接口 3：添加分类】
    // ============================================================================
    /**
     * 创建新的分类
     * 
     * 应用场景：
     * - 管理员添加新的题目分类
     * - 可以添加一级分类（parentId=0）
     * - 可以添加子分类（parentId=父分类 ID）
     * 
     * 必填字段说明：
     * - name：分类名称（必填），如"Java 基础"
     * - parentId：父分类 ID（可选），默认为 0 表示一级分类
     * - sort：排序序号（可选），数字越小越靠前，默认可以设为 1
     * 
     * 业务规则：
     * - 分类名称不能为空
     * - 分类名称在同一父分类下不能重复
     * - parentId 必须指向存在的分类或为 0
     * 
     * @param category 分类对象（从 JSON 反序列化）
     *                 示例：{"name": "数据结构", "parentId": 0, "sort": 1}
     * @return Result<Void> 不返回具体数据，只返回成功消息
     * 
     * HTTP 请求示例：
     * POST /api/categories
     * Content-Type: application/json
     * {
     *   "name": "人工智能",
     *   "parentId": 0,      // 0 表示一级分类
     *   "sort": 1           // 排序序号
     * }
     * 
     * 或者添加子分类：
     * {
     *   "name": "机器学习",
     *   "parentId": 5,      // 5 是"计算机科学"分类的 ID
     *   "sort": 1
     * }
     * 
     * TODO: Service 层需要实现的逻辑：
     * 1. 验证分类名称是否为空
     * 2. 检查同级分类中是否有重名
     * 3. 验证 parentId 是否有效
     * 4. 调用 save() 方法保存到数据库
     */
    @PostMapping  // 映射 POST 请求到 /api/categories
    @Operation(summary = "添加新分类", description = "创建新的题目分类，支持设置父分类实现层级结构")
    public Result<Void> addCategory(@RequestBody Category category) {
        // 【步骤 1】调用 Service 层保存分类
        // @RequestBody 注解：从 HTTP 请求体（JSON）中读取数据并反序列化为 Category 对象
        // saveCategory() 方法会执行保存操作
        categoryService.saveCategory(category);
        
        // 【步骤 2】返回成功消息
        // Void 表示不返回具体数据，只需要知道操作成功即可
        return Result.success(null); 
    }

    // ============================================================================
    // 【接口 4：更新分类】
    // ============================================================================
    /**
     * 更新已有分类的信息
     * 
     * 应用场景：
     * - 修改分类名称（如修正错别字）
     * - 调整分类的排序顺序
     * - 修改分类的父分类（移动分类到其他层级）
     * 
     * 可更新的字段：
     * - name：分类名称
     * - parentId：父分类 ID（可以调整分类的层级）
     * - sort：排序序号
     * 
     * 注意事项：
     * - category 对象必须包含 id 字段，否则不知道更新哪条记录
     * - 修改 parentId 时要小心，避免形成循环引用（如 A 是 B 的父，B 又是 A 的父）
     * - 不能将父分类设置为自己的子分类
     * 
     * @param category 分类对象（必须包含 id 字段）
     *                 示例：{"id": 5, "name": "Java 核心技术", "parentId": 1, "sort": 2}
     * @return Result<Void> 返回成功消息
     * 
     * HTTP 请求示例：
     * PUT /api/categories
     * Content-Type: application/json
     * {
     *   "id": 5,                    // 必需：要更新的分类 ID
     *   "name": "updated name",     // 新的分类名称
     *   "parentId": 1,              // 新的父分类 ID
     *   "sort": 2                   // 新的排序序号
     * }
     * 
     * TODO: Service 层需要实现的逻辑：
     * 1. 验证 id 是否存在
     * 2. 检查分类是否存在
     * 3. 验证新的 parentId 是否有效（避免循环引用）
     * 4. 调用 updateById() 方法更新
     */
    @PutMapping  // 映射 PUT 请求到 /api/categories
    @Operation(summary = "更新分类信息", description = "修改分类的名称、描述、排序等信息")
    public Result<Void> updateCategory(@RequestBody Category category) {
        // 【步骤 1】调用 Service 层更新分类
        // updateCategory() 方法会：
        // 1. 根据 id 查找分类
        // 2. 更新 name、parentId、sort 等字段
        // 3. 保存到数据库
        categoryService.updateCategory(category);
        
        // 【步骤 2】记录日志（便于调试和审计）
        // 日志内容：在哪个父分类下，更新了哪个子分类
        // categoryId 和 categoryName 是从 category 对象中获取的
        log.info("在{}父分类下，更新{}子分类成功！", category.getParentId(), category.getName());
        
        // 【步骤 3】返回成功消息
        return Result.success("更新分类接口调用成功！");
    }

    // ============================================================================
    // 【接口 5：删除分类】
    // ============================================================================
    /**
     * 删除指定的分类
     * 
     * 应用场景：
     * - 管理员删除不需要的分类
     * 
     * 删除前的检查（非常重要！）：
     * 1. 检查分类下是否有题目
     *    - 如果有题目，不能删除（否则题目就没有分类了）
     *    - 应该提示："该分类下存在题目，无法删除"
     * 2. 检查是否有子分类
     *    - 如果有子分类，不能直接删除
     *    - 应该先删除或移动所有子分类
     * 
     * 删除策略选择：
     * - 物理删除：直接从数据库删除（当前实现）
     *   DELETE FROM categories WHERE id = ?;
     * - 逻辑删除：标记 is_deleted=1（推荐，更安全）
     *   UPDATE categories SET is_deleted = 1 WHERE id = ?;
     * 
     * 级联删除问题：
     * - 如果要删除的分类有子分类，是否需要同时删除子分类？
     * - 当前设计：需要先手动删除或移动子分类
     * 
     * @param id 分类的主键 ID，通过 URL 路径传递
     * @return Result<Void> 返回删除结果消息
     * 
     * HTTP 请求示例：
     * DELETE /api/categories/5
     * 
     * 可能的错误情况：
     * 1. 分类不存在 -> 返回 404 或错误消息
     * 2. 分类下有题目 -> 返回错误消息："该分类下存在题目，无法删除"
     * 3. 分类有子分类 -> 返回错误消息："请先删除或移动子分类"
     * 
     * TODO: Service 层需要实现的逻辑：
     * 1. 根据 id 查找分类
     * 2. 检查分类是否存在
     * 3. 统计该分类下的题目数量
     * 4. 如果有题目，抛出异常或返回错误
     * 5. 检查是否有子分类
     * 6. 如果有子分类，提示先处理子分类
     * 7. 调用 removeById() 方法删除
     */
    @DeleteMapping("/{id}")  // 映射 DELETE 请求到 /api/categories/{id}
    @Operation(summary = "删除分类", description = "删除指定的题目分类，注意：删除前需确保分类下没有题目")
    public Result<Void> deleteCategory(
            @Parameter(description = "分类 ID")  // Swagger 注解：参数描述
            @PathVariable Long id) {  // @PathVariable：将 URL 中的{id}绑定到 id 参数
        
        // 【步骤 1】调用 Service 层删除分类
        // deleteCategory(id) 方法会执行删除操作
        // Service 层应该先进行检查（是否有题目、是否有子分类）
        categoryService.deleteCategory(id);
        
        // 【步骤 2】返回成功消息
        // null 表示不返回具体数据
        return Result.success(null);
    }
}
