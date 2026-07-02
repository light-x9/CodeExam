package com.atguigu.exam.service;

import com.atguigu.exam.entity.Category;

import com.baomidou.mybatisplus.extension.service.IService;
import java.util.List;

public interface CategoryService extends IService<Category> {

    // 查询所有分类
    List<Category> getAllCategories();

    // 查询分类树
    List<Category> getCategoryTree();

    // 保存分类
    void saveCategory(Category category);

    // 更新分类
    void updateCategory(Category category);

    // 删除
    void deleteCategory(Long id);

    /**
     * 根据分类名称和题目类型，解析该类型下对应的分类ID
     * 例：名称"数据库" + 类型"JUDGE" → 找到判断题父节点下的"数据库"分类ID
     * @param categoryName 分类名称
     * @param questionType 题目类型（CHOICE/JUDGE/TEXT）
     * @return 对应类型下的分类ID，找不到则返回null
     */
    Long resolveCategoryIdByType(String categoryName, String questionType);
}
