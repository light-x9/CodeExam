package com.atguigu.exam.service;

import com.atguigu.exam.entity.PaperQuestion;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 试卷-题目关联服务接口
 * 功能：定义试卷与题目多对多关联关系的业务操作规范
 * 继承 MyBatis-Plus 的 IService，自动获得单表 CRUD 能力
 */
public interface PaperQuestionService extends IService<PaperQuestion> {

}