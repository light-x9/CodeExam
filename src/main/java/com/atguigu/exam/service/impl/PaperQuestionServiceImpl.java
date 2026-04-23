package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.PaperQuestion;
import com.atguigu.exam.mapper.PaperQuestionMapper;
import com.atguigu.exam.service.PaperQuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 试卷-题目关联服务实现类
 * 功能：处理试卷与题目之间的多对多关联关系（一张试卷包含多个题目，一个题目可被多张试卷引用）
 * 继承 MyBatis-Plus 封装的 ServiceImpl，自动获得单表 CRUD 能力
 * 实现 PaperQuestionService 接口，定义业务方法规范
 */
@Service
public class PaperQuestionServiceImpl extends ServiceImpl<PaperQuestionMapper, PaperQuestion> implements PaperQuestionService {

}