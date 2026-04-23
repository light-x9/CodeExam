package com.atguigu.exam.service;

import com.atguigu.exam.entity.Category;

import java.util.List;

public interface CategoryService {

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
}