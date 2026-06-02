package com.atguigu.exam.service.impl;

import com.atguigu.exam.common.BusinessException;
import com.atguigu.exam.common.ErrorCode;
import com.atguigu.exam.entity.Category;
import com.atguigu.exam.entity.Question;
import com.atguigu.exam.mapper.CategoryMapper;
import com.atguigu.exam.mapper.QuestionMapper;
import com.atguigu.exam.service.CategoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ============================================================================
 * 【类级别注释】
 * ============================================================================
 * 分类服务实现类 - 业务逻辑层
 * 
 * 核心功能：
 * 1. 分类查询：获取所有分类列表（包含题目数量统计）
 * 2. 树形构建：将平铺的分类数据构建成树形结构
 * 3. 分类管理：增删改查业务的完整实现
 * 4. 数据校验：确保数据的完整性和一致性
 * 
 * 继承关系说明：
 * - 继承 ServiceImpl<CategoryMapper, Category>
 *   - ServiceImpl 是 MyBatis-Plus 提供的服务基类
 *   - 封装了通用的 CRUD 方法（save、update、delete、getById 等）
 *   - 泛型参数：<Mapper 类型，实体类型>
 * - 实现 CategoryService 接口
 *   - 定义业务方法的规范
 *   - Controller 层通过接口调用 Service
 * 
 * 技术栈：
 * - Spring @Service：标识这是一个服务层 Bean
 * - MyBatis-Plus：简化数据库操作
 * - Stream API：Java 8 的流式处理，简化集合操作
 * - Lambda 表达式：类型安全的条件构造
 * 
 * @author light
 * @version 1.0
 * @since 2025-01-01
 */
@Slf4j  // Lombok 注解：自动生成 private static final Logger log 对象
@Service  // Spring 注解：标识这是一个服务层组件，由 Spring 容器管理
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {
    
    // ============================================================================
    // 【依赖注入】
    // ============================================================================
    @Autowired  // Spring 的依赖注入注解：自动装配 Mapper 接口的实现
    private CategoryMapper categoryMapper;  // 分类 Mapper，用于数据库操作
    
    @Autowired  // Spring 的依赖注入注解：自动装配 Mapper 接口的实现
    private QuestionMapper questionMapper;  // 题目 Mapper，用于查询题目数量
    
    // ============================================================================
    // 【方法 1：获取所有分类列表（含题目数量）】
    // ============================================================================
    /**
     * 获取所有分类列表（包含每个分类的题目数量统计）
     * 
     * 业务流程：
     * 1. 从数据库查询所有分类的基础信息（id、name、parentId、sort 等）
     * 2. 按 sort 字段升序排序（保证展示顺序一致）
     * 3. 批量查询每个分类的题目数量
     * 4. 将题目数量设置到 Category.count 字段
     * 5. 返回完整的分类列表
     * 
     * 性能优化：
     * - 使用批量查询而非循环查询（N+1 问题）
     * - 一次 SQL 查询获取所有分类的题目数量
     * - 避免在循环中执行 SQL，提高查询效率
     * 
     * @return List<Category> 分类列表，每个分类包含 count 字段（题目数量）
     * 
     * SQL 执行过程：
     * 1. SELECT * FROM categories ORDER BY sort ASC;
     * 2. SELECT category_id, COUNT(*) as count FROM questions GROUP BY category_id;
     * 
     * 返回数据示例：
     * [
     *   {
     *     "id": 1,
     *     "name": "Java 基础",
     *     "parentId": 0,
     *     "sort": 1,
     *     "count": 25  // 该分类下有 25 道题目
     *   },
     *   {
     *     "id": 2,
     *     "name": "集合框架",
     *     "parentId": 1,
     *     "sort": 1,
     *     "count": 10  // 该分类下有 10 道题目
     *   }
     * ]
     */

    @Override  // 标注这是实现接口的方法//
    public List<Category> getAllCategories() {
        // 【步骤 1】查询所有分类的基础信息
        // selectList()：MyBatis-Plus 提供的查询方法，返回符合条件的实体列表
        // LambdaQueryWrapper：类型安全的条件构造器
        // orderByAsc(Category::getSort)：按 sort 字段升序排序

        List<Category> categories = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>()
                        .orderByAsc(Category::getSort)
        );

        // 【步骤 2】为分类列表填充题目数量
        // fillQuestionCount() 方法会：
        // 1. 一次性查询所有分类的题目数量（批量查询，效率高）
        // 2. 将题目数量映射到每个 Category 对象的 count 字段
        fillQuestionCount(categories);

        // 【步骤 3】返回完整的分类列表
        return categories;
    }

    // ============================================================================
    // 【方法 2：获取分类树形结构】
    // ============================================================================
    /**
     * 获取分类的树形层级结构
     * 
     * 业务流程：
     * 1. 查询所有分类的基础信息（与 getAllCategories 相同）
     * 2. 为每个分类填充题目数量
     * 3. 构建树形结构（通过 buildTree 方法）
     * 4. 返回树形结构列表（只包含一级分类作为根节点）
     * 
     * 树形构建原理：
     * 1. 将所有分类按 parentId 分组 -> Map<parentId, List<Category>>
     * 2. 遍历每个分类，找到它的子分类（childrenMap.get(category.getId())）
     * 3. 将子分类设置到父分类的 children 字段
     * 4. 递归累加题目数量（父分类 count = 自身 count + 所有子分类 count 总和）
     * 5. 筛选出 parentId=0 的分类作为根节点返回
     * 
     * 应用场景：
     * - 前台：树形下拉框选择题目的分类
     * - 后台：树形表格展示分类层级关系
     * 
     * @return List<Category> 树形结构的分类列表（只包含一级分类）
     * 
     * 数据结构示例：
     * [
     *   {
     *     "id": 1,
     *     "name": "编程语言",
     *     "parentId": 0,
     *     "count": 100,  // 累加了所有子分类的题目
     *     "children": [
     *       {
     *         "id": 2,
     *         "name": "Java",
     *         "parentId": 1,
     *         "count": 50,
     *         "children": [
     *           {
     *             "id": 5,
     *             "name": "Java 基础",
     *             "parentId": 2,
     *             "count": 25,
     *             "children": null
     *           }
     *         ]
     *       }
     *     ]
     *   }
     * ]
     */
    @Override
    public List<Category> getCategoryTree() {
        // 【步骤 1】查询所有分类，并按 sort 排序
        List<Category> allCategories = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>()
                        .orderByAsc(Category::getSort)
        );

        // 【步骤 2】为每个分类填充其自身的题目数量
        // 注意：这里填充的是每个分类自身的题目数量，不包含子分类
        fillQuestionCount(allCategories);
        
        // 【步骤 3】构建树形结构
        // buildTree() 方法会：
        // 1. 按 parentId 分组所有分类
        // 2. 为每个分类设置 children 属性
        // 3. 递归累加题目数量到父分类
        // 4. 返回只包含一级分类（parentId=0）的列表
        List<Category> buildTree = buildTree(allCategories);
        
        // 【步骤 4】记录日志（便于调试）
        log.info("查询类别树状结构集合：{}", buildTree);
        
        // 【步骤 5】返回树形结构
        return buildTree;
    }

    // ============================================================================
    // 【方法 3：保存分类】
    // ============================================================================
    /**
     * 保存新的分类信息
     * 
     * 业务规则：
     * 1. 同一父分类下不能有重名的子分类
     *    - 例如： parentId=1 的分类下不能有两个都叫"Java 基础"的子分类
     *    - 但 parentId=1 和 parentId=2 可以都有叫"Java 基础"的子分类
     * 2. parentId=0 表示一级分类（顶级分类）
     * 3. sort 字段控制排序，数字越小越靠前
     * 
     * 验证逻辑：
     * 1. 查询同一 parentId 下是否有同名的分类
     * 2. 如果存在，抛出异常阻止保存
     * 3. 如果不存在，执行保存操作
     * 
     * 异常处理：
     * - 使用 RuntimeException 表示业务异常
     * - 异常消息会返回给前端，提示用户
     * - 事务会自动回滚，不会插入错误数据
     * 
     * @param category 要保存的分类对象
     *                 必填字段：name（分类名称）
     *                 可选字段：parentId（默认 0）、sort（默认 1）
     * 
     * SQL 执行：
     * 1. SELECT count(*) FROM categories WHERE parent_id = ? AND name = ?;
     * 2. INSERT INTO categories (name, parent_id, sort) VALUES (?, ?, ?);
     * 
     * TODO: 可以优化的地方
     * 1. parentId 为 null 时，应该默认为 0
     * 2. sort 为 null 时，应该自动设置为当前最大 sort+1
     * 3. 分类名称应该去除首尾空格
     */
    @Override
    public void saveCategory(Category category) {
        // 【步骤 1】检查同一父分类下是否已存在同名分类
        // LambdaQueryWrapper 构建查询条件
        LambdaQueryWrapper<Category> queryWrapper = new LambdaQueryWrapper<>();
        // eq：等于条件，parent_id = ?
        queryWrapper.eq(Category::getParentId, category.getParentId());
        // eq：等于条件，name = ?
        queryWrapper.eq(Category::getName, category.getName());
        
        // count()：统计符合条件的记录数
        long count = count(queryWrapper);
        
        // 【步骤 2】如果存在重复，抛出异常
        if (count > 0) {
            // String.formatted()：Java 15+ 的字符串格式化方法
            // 相当于 String.format()，但更简洁
            throw new BusinessException(ErrorCode.CATEGORY_DUPLICATE,
                    "父分类ID=" + category.getParentId() + " 下已存在子分类《" + category.getName() + "》");
        }
        
        // 【步骤 3】保存分类到数据库
        // save()：继承自 ServiceImpl 的方法，执行 INSERT 操作
        save(category);
    }

    // ============================================================================
    // 【方法 4：更新分类】
    // ============================================================================
    /**
     * 更新分类信息
     * 
     * 业务规则：
     * 1. 同一父分类下，除了自己之外，不能有其他分类重名
     *    - 允许：修改自己的名称（不改变名称）
     *    - 不允许：改成与其他兄弟分类相同的名称
     * 2. 可以修改 parentId（移动分类到其他父分类下）
     * 3. 可以修改 sort（调整排序位置）
     * 
     * 验证逻辑：
     * 1. 查询同一 parentId 下，排除自己之后，是否有其他分类同名
     * 2. eq(parentId)：确保在同一父分类下比较
     * 3. ne(id)：排除自己（not equal）
     * 4. eq(name)：检查名称是否相同
     * 5. 如果存在，说明有冲突，抛出异常
     * 
     * 注意事项：
     * - 修改 parentId 时要小心，避免形成循环引用
     * - 当前实现没有检查循环引用（可以改进）
     * 
     * @param category 要更新的分类对象
     *                 必填字段：id（分类 ID）、name、parentId
     * 
     * SQL 执行：
     * 1. SELECT * FROM categories WHERE parent_id = ? AND id != ? AND name = ?;
     * 2. UPDATE categories SET name=?, parent_id=?, sort=? WHERE id=?;
     * 
     * TODO: 可以优化的地方
     * 1. 检查 parentId 是否指向存在的分类
     * 2. 防止循环引用（如 A 是 B 的父，B 不能是 A 的父）
     * 3. 级联更新子分类的路径信息（如果有 path 字段）
     */
    @Override
    public void updateCategory(Category category) {
        // 【步骤 1】构建查询条件，检查是否有重名
        LambdaQueryWrapper<Category> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        // eq：在同一父分类下比较
        lambdaQueryWrapper.eq(Category::getParentId, category.getParentId());
        // ne：排除自己（not equal），允许自己和自己重名
        lambdaQueryWrapper.ne(Category::getId, category.getId());
        // eq：检查名称是否相同
        lambdaQueryWrapper.eq(Category::getName, category.getName());
        
        // 【步骤 2】获取 Mapper 并检查是否存在重复
        CategoryMapper categoryMapper = getBaseMapper();  // 获取基础的 Mapper 对象
        boolean exists = categoryMapper.exists(lambdaQueryWrapper);  // 是否存在符合条件的记录
        
        // 【步骤 3】如果存在重复，获取父分类名称，抛出更友好的异常消息
        if (exists) {
            // getById()：继承自 ServiceImpl 的方法，根据 ID 查询实体
            Category parent = getById(category.getParentId());
            
            // 抛出异常，提示在哪个父分类下有重名
            throw new BusinessException(ErrorCode.CATEGORY_DUPLICATE,
                    "父分类《" + parent.getName() + "》下已存在子分类《" + category.getName() + "》");
        }
        
        // 【步骤 4】执行更新
        // updateById()：继承自 ServiceImpl 的方法，根据 ID 更新实体
        // 会更新所有非 null 字段
        updateById(category);
    }

    // ============================================================================
    // 【方法 5：删除分类】
    // ============================================================================
    /**
     * 删除指定的分类
     * 
     * 业务规则（删除前的检查）：
     * 1. 不能删除一级分类（parentId=0 的分类）
     *    - 原因：一级分类下通常有子分类，删除会影响整个分类体系
     *    - 解决：先删除或移动所有子分类，再删除一级分类
     * 2. 分类下有关联的题目时，不能删除
     *    - 原因：题目会失去分类归属
     *    - 解决：先删除或迁移所有题目，再删除分类
     * 3. 以上检查都通过后，才能执行删除
     * 
     * 删除策略：
     * - 物理删除：直接从数据库删除（当前实现）
     *   DELETE FROM categories WHERE id = ?;
     * - 逻辑删除：标记 is_deleted=1（推荐，更安全）
     *   UPDATE categories SET is_deleted = 1 WHERE id = ?;
     * 
     * 事务控制：
     * - @Transactional 注解（虽然这里没有显式添加）
     * - 如果抛出异常，事务会自动回滚
     * - 确保不会出现部分删除的情况
     * 
     * @param id 要删除的分类 ID
     * 
     * SQL 执行：
     * 1. SELECT * FROM categories WHERE id = ?;
     * 2. SELECT COUNT(*) FROM questions WHERE category_id = ?;
     * 3. DELETE FROM categories WHERE id = ?;
     * 
     * 可能的异常情况：
     * 1. 分类不存在 -> getById(id) 返回 null，后续操作会抛 NullPointerException
     * 2. 是一级分类 -> 抛出"不能删除一级标题"
     * 3. 有题目关联 -> 抛出"关联了 X 道题目，无法删除"
     * 
     * TODO: 可以优化的地方
     * 1. 检查分类是否存在，不存在时给出友好提示
     * 2. 检查是否有子分类，有子分类时提示先处理
     * 3. 支持级联删除（同时删除子分类）
     * 4. 改为逻辑删除，增加 is_deleted 字段
     */
    @Override
    public void deleteCategory(Long id) {
        // 【步骤 1】查询要删除的分类
        // getById()：继承自 ServiceImpl 的方法，根据主键查询实体
        Category category = getById(id);
        
        // 【步骤 2】检查是否是一级分类（parentId=0）
        if (category.getParentId() == 0) {
            // 抛出异常，阻止删除一级分类
            throw new BusinessException(ErrorCode.CATEGORY_IS_ROOT);
        }
        
        // 【步骤 3】检查是否有关联的题目
        // 构建查询条件：category_id = ?
        LambdaQueryWrapper<Question> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(Question::getCategoryId, id);
        
        // 统计题目数量
        // selectCount()：MyBatis-Plus 提供的统计方法
        long count = questionMapper.selectCount(lambdaQueryWrapper);
        
        // 【步骤 4】如果有题目关联，抛出异常
        if (count > 0) {
            throw new BusinessException(ErrorCode.CATEGORY_HAS_QUESTIONS,
                    "分类《" + category.getName() + "》关联了 " + count + " 道题目，无法删除");
        }
        
        // 【步骤 5】以上检查都通过，执行删除
        // removeById()：继承自 ServiceImpl 的方法，根据 ID 删除实体
        removeById(id);
    }

    // ============================================================================
    // 【辅助方法 1：构建树形结构】
    // ============================================================================
    /**
     * 构建树形结构（私有辅助方法）
     * 
     * 算法思路（核心难点）：
     * 
     * 第一步：分组
     * ┌─────────────────────────────────────────────┐
     * │ 输入：平铺的所有分类                         │
     * │ [                                            │
     * │   {id:1, parentId:0, name:"A"},             │
     * │   {id:2, parentId:1, name:"B"},             │
     * │   {id:3, parentId:1, name:"C"},             │
     * │   {id:4, parentId:2, name:"D"}              │
     * │ ]                                            │
     * └─────────────────────────────────────────────┘
     * ↓ 使用 groupBy(parentId) 分组
     * ┌─────────────────────────────────────────────┐
     * │ childrenMap: Map<parentId, List<Category>>  │
     * │ {                                            │
     * │   0 -> [{id:1, parentId:0}],                │
     * │   1 -> [{id:2, parentId:1}, {id:3, ...}],   │
     * │   2 -> [{id:4, parentId:2}]                 │
     * │ }                                            │
     * └─────────────────────────────────────────────┘
     * 
     * 第二步：设置 children
     * 遍历每个分类，从 childrenMap 中找到它的子分类：
     * - id=1 的分类：childrenMap.get(1) -> [id:2, id:3]
     * - id=2 的分类：childrenMap.get(2) -> [id:4]
     * - id=3 的分类：childrenMap.get(3) -> []
     * - id=4 的分类：childrenMap.get(4) -> []
     * 
     * 第三步：递归累加题目数量
     * 从叶子节点向根节点累加：
     * - id=4 的 count = 自身 count
     * - id=2 的 count = 自身 count + id:4 的 count
     * - id=3 的 count = 自身 count
     * - id=1 的 count = 自身 count + id:2 的 count + id:3 的 count
     * 
     * 第四步：返回根节点
     * 筛选 parentId=0 的分类（顶级分类）
     * 
     * @param categories 所有分类的平铺列表
     * @return List<Category> 树形结构（只包含一级分类作为根节点）
     * 
     * Java 8 Stream API 详解：
     * - stream()：将集合转为流，支持函数式操作
     * - collect()：收集流的结果
     * - groupingBy()：按某个字段分组
     * - forEach()：遍历流的每个元素
     * - filter()：过滤元素
     * - mapToLong()：将对象映射为 Long 值
     * - sum()：汇总求和
     */
    private List<Category> buildTree(List<Category> categories) {
        // 【步骤 1】按 parentId 分组
        // 将所有的分类按照 parentId 进行分组
        // 结果是一个 Map：key 是 parentId，value 是该 parentId 下的所有子分类
        Map<Long, List<Category>> childrenMap = categories.stream()
                .collect(Collectors.groupingBy(Category::getParentId));

        /*
         * Stream API 详细解释：
         * 
         * 1. categories.stream()
         *    - 将 List<Category> 转换为 Stream<Category>
         *    - Stream 是 Java 8 引入的流式 API，用于处理集合
         *    - 类似"管道"，数据从一个操作流向另一个操作
         * 
         * 2. Collectors.groupingBy(Category::getParentId)
         *    - groupingBy 是分组收集器
         *    - Category::getParentId 是方法引用，提取每个分类的 parentId 作为分组的 key
         *    - value 是具有相同 parentId 的分类列表
         * 
         * 3. 结果示例：
         *    输入：[{id:1, parentId:0}, {id:2, parentId:1}, {id:3, parentId:1}]
         *    输出：{
         *      0 -> [{id:1, parentId:0}],
         *      1 -> [{id:2, parentId:1}, {id:3, parentId:1}]
         *    }
         * 
         * 方法引用 (::) 详解：
         * - Category::getParentId 等价于 lambda 表达式：c -> c.getParentId()
         * - :: 是方法引用运算符，更简洁
         * - 类似的还有：System.out::println 等价于 x -> System.out.println(x)
         */

        // 【步骤 2】遍历所有分类，为它们设置 children 属性，并递归累加题目数量
        categories.forEach(category -> {
            // 从 Map 中找到当前分类的所有子分类
            // childrenMap.get(category.getId())：获取以当前分类 ID 为 parentId 的子分类列表
            // childrenMap.getOrDefault()：如果找不到，返回空列表（避免 null 指针异常）
            List<Category> children = childrenMap.getOrDefault(category.getId(), new ArrayList<>());
            
            // 将子分类设置到当前分类的 children 字段
            category.setChildren(children);

            // 【步骤 2.1】汇总子分类的题目数量到父分类
            // 计算所有子分类的题目数量总和
            long childrenQuestionCount = children.stream()
                    .mapToLong(c -> c.getCount() != null ? c.getCount() : 0L)
                    .sum();
            /*
             * 嵌套 Stream 详解：
             * 
             * 1. children.stream()
             *    - 将子分类列表转为流
             * 
             * 2. mapToLong(c -> c.getCount() != null ? c.getCount() : 0L)
             *    - mapToLong：将每个 Category 对象映射为 long 值（题目数量）
             *    - 三元运算符：如果 count 为 null，视为 0
             *    - 结果是一个 LongStream（long 值的流）
             * 
             * 3. sum()
             *    - 汇总 LongStream 中的所有值
             *    - 返回子分类题目数量的总和
             * 
             * 示例：
             * children = [
             *   {name: "Java 基础", count: 25},
             *   {name: "集合框架", count: 10},
             *   {name: "多线程", count: 15}
             * ]
             * childrenQuestionCount = 25 + 10 + 15 = 50
             */

            // 【步骤 2.2】获取当前分类自身的题目数量
            long selfQuestionCount = category.getCount() != null ? category.getCount() : 0L;
            
            // 【步骤 2.3】设置父分类的总题目数量
            // 父分类的总数 = 自身的题目数 + 所有子分类的题目数总和
            // 这样设计的好处：父分类显示的是该分支下所有题目的总数
            category.setCount(selfQuestionCount + childrenQuestionCount);
        });
        /*
         * forEach 详解：
         * 
         * 1. categories.forEach(category -> {...})
         *    - forEach 是 Stream 或 Collection 的遍历方法
         *    - 等价于增强 for 循环：for (Category category : categories) {...}
         *    - 但 forEach 更函数式，适合与 Stream 配合使用
         * 
         * 2. Lambda 表达式 (category -> {...})
         *    - category 是参数
         *    - -> 是箭头运算符
         *    - {...} 是方法体
         *    - 等价于匿名方法
         */

        // 【步骤 3】筛选出所有顶级分类（parentId 为 0），它们是树的根节点
        /*
         * 为什么要筛选 parentId=0 的分类？
         * 
         * 1. 树形结构的特点：
         *    - 只有一个根节点（或多个根节点，形成森林）
         *    - 本系统中，parentId=0 表示一级分类，即根节点
         *    - 其他分类都是根节点的子孙节点
         * 
         * 2. 返回数据的结构：
         *    - 如果返回所有分类，会有大量重复数据
         *    - 只返回根节点，通过 children 可以访问到所有子孙节点
         *    - 前端树形组件只需要根节点列表
         * 
         * 3. 示例：
         *    输入：[一级 A, 二级 B, 二级 C, 三级 D]
         *    处理后：
         *    [
         *      {
         *        一级 A,
         *        children: [
         *          {二级 B, children: [...]},
         *          {二级 C, children: [...]}
         *        ]
         *      }
         *    ]
         *    返回的列表只包含一级 A
         */
        return categories.stream()
                .filter(c -> c.getParentId() == 0)  // 过滤：只保留 parentId=0 的分类
                .collect(Collectors.toList());       // 收集为 List
    }

    // ============================================================================
    // 【辅助方法 2：填充分类题目数量】
    // ============================================================================
    /**
     * 填充分类的题目数量
     * 
     * 性能优化关键：
     * - 使用批量查询而非循环查询
     * - 一次 SQL 查询所有分类的题目数量
     * - 避免 N+1 问题（N 个分类就执行 N+1 次 SQL）
     * 
     * 传统方式（性能差）：
     * for (Category category : categories) {
     *     Long count = questionMapper.countByCategoryId(category.getId());
     *     category.setCount(count);
     * }
     * // 如果有 100 个分类，就要执行 100 次 SQL！
     * 
     * 优化方式（性能好）：
     * 1. 一条 SQL 查询所有分类的题目数量
     * 2. 将结果放入 Map<categoryId, count>
     * 3. 遍历分类，从 Map 中获取数量并设置
     * // 只执行 1 次 SQL！
     * 
     * @param categories 需要填充题目数量的分类列表
     * 
     * SQL 执行：
     * SELECT category_id, COUNT(*) as count 
     * FROM questions 
     * WHERE is_deleted = 0 
     * GROUP BY category_id;
     * 
     * 结果示例：
     * [
     *   {category_id: 1, count: 25},
     *   {category_id: 2, count: 10},
     *   {category_id: 3, count: 15}
     * ]
     * 
     * 转换为 Map 后：
     * {
     *   1 -> 25,
     *   2 -> 10,
     *   3 -> 15
     * }
     */
    private void fillQuestionCount(List<Category> categories) {
        // 【步骤 1】批量查询所有分类的题目数量
        // getCategoryQuestionCount()：自定义的 SQL 查询方法
        // 返回 List<Map>，每个 Map 包含 category_id 和 count 两个字段
        List<Map<Long, Object>> questionCountList = questionMapper.getCategoryQuestionCount();

        // 【步骤 2】将查询结果转换为 Map<categoryId, count>
        // 这样可以通过 categoryId 快速查找对应的题目数量
        // 时间复杂度：O(1)，避免每次都要遍历列表
        Map<Long, Long> questionCountMap = questionCountList.stream()
                .collect(Collectors.toMap(
                        // key 映射函数：从 Map 中提取 category_id 作为 key
                        map -> Long.valueOf(map.get("category_id").toString()),
                        // value 映射函数：从 Map 中提取 count 作为 value
                        map -> Long.valueOf(map.get("count").toString())
                ));
        /*
         * toMap 收集器详解：
         * 
         * Collectors.toMap(keyMapper, valueMapper)
         * 
         * 1. keyMapper：如何提取 key
         *    map -> Long.valueOf(map.get("category_id").toString())
         *    - map.get("category_id")：从 Map 中获取 category_id 字段
         *    - .toString()：转为字符串（因为 SQL 返回的可能是 Object 类型）
         *    - Long.valueOf()：转为 Long 类型
         * 
         * 2. valueMapper：如何提取 value
         *    map -> Long.valueOf(map.get("count").toString())
         *    - 同上，提取 count 字段并转为 Long
         * 
         * 3. 结果示例：
         *    输入：[{category_id:1, count:25}, {category_id:2, count:10}]
         *    输出：{1->25, 2->10}
         * 
         * 为什么需要转换？
         * - SQL 返回的是 List<Map<Long, Object>>，不方便使用
         * - 转换为 Map<Long, Long>后，可以直接通过 categoryId 获取 count
         * - questionCountMap.get(1) -> 25
         */

        // 【步骤 3】遍历分类列表，为每个分类设置对应的题目数量
        categories.forEach(category -> {
            // getOrDefault：从 Map 中获取值，如果不存在则返回默认值 0L
            // 这样即使某个分类没有题目，也不会出现 null
            category.setCount(questionCountMap.getOrDefault(category.getId(), 0L));
        });
        /*
         * getOrDefault 详解：
         * 
         * Map.getOrDefault(key, defaultValue)
         * 
         * 1. 作用：
         *    - 如果 key 存在，返回对应的 value
         *    - 如果 key 不存在，返回 defaultValue
         * 
         * 2. 优势：
         *    - 避免先判断 containsKey，代码更简洁
         *    - 避免 get() 返回 null 导致空指针
         * 
         * 3. 示例：
         *    questionCountMap = {1->25, 2->10}
         *    questionCountMap.get(1) -> 25
         *    questionCountMap.get(3) -> null（不存在）
         *    questionCountMap.getOrDefault(3, 0L) -> 0L（返回默认值）
         * 
         * 4. 业务场景：
         *    - 有些分类可能还没有题目
         *    - SQL 查询结果中不会有这些分类
         *    - 使用 getOrDefault 确保 count 为 0 而不是 null
         */
    }
}