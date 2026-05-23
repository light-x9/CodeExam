package com.atguigu.exam.service.impl;


import com.atguigu.exam.common.BusinessException;
import com.atguigu.exam.common.ErrorCode;
import com.atguigu.exam.entity.Paper;
import com.atguigu.exam.entity.PaperQuestion;
import com.atguigu.exam.mapper.PaperMapper;
import com.atguigu.exam.service.PaperQuestionService;
import com.atguigu.exam.service.PaperService;
import com.atguigu.exam.vo.PaperVo;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


/**
 * 试卷服务实现类
 */
@Slf4j
@Service
public class PaperServiceImpl extends ServiceImpl<PaperMapper, Paper> implements PaperService {

    @Autowired
         private PaperQuestionService paperQuestionService;
    /**
     * 手动创建试卷（手动组卷）核心业务方法
     * 功能：接收前端传递的试卷VO，完成试卷创建、题目关联绑定的完整业务流程
     * 事务控制：@Transactional 保证多表操作的原子性，失败时自动回滚
     *
     * @param paperVo 前端传递的创建试卷参数VO（包含试卷基础信息+手动选中的题目ID-分值Map）
     *                paperVo.getQuestions()：Map<题目ID, 题目分值>，存储手动选中的题目和对应分值
     * @return 创建完成的试卷实体对象（包含数据库生成的主键ID）
     */
    @Override
    @Transactional // 开启事务，保证试卷表和中间表操作的原子性：要么都成功，要么都失败
    public Paper createPaper(PaperVo paperVo) {
        // --------------------------
        // 1. 构建试卷基础信息（VO转实体，设置默认业务字段）
        // --------------------------
        Paper paper = new Paper();
        // 将前端传递的VO参数（name/description/duration等）拷贝到Paper实体
        BeanUtils.copyProperties(paperVo, paper);
        // 设置试卷默认状态：DRAFT（草稿），表示还未发布，仅可编辑
        paper.setStatus("DRAFT");
        // --------------------------
        // 2. 处理「是否有题目」的分支逻辑
        // 场景1：前端未传入任何题目 → 试卷仅保存基础信息，无题目关联
        // --------------------------
        if (ObjectUtils.isEmpty(paperVo.getQuestions())) {
            // 无题目时，设置总分数为0
            paper.setTotalScore(BigDecimal.ZERO);
            // 无题目时，设置总题目数量为0
            paper.setQuestionCount(0);
            // 保存试卷基础信息（MyBatis-Plus save方法，自动生成主键ID）
            save(paper);
            // 记录警告日志：告知当前试卷无题目，仅可编辑，不能用于考试
            log.warn("当前试卷：{} 没有组装题目，只能用于试卷编辑，不能用于考试！！", paper);
            // 直接返回试卷对象，结束流程
            return paper;
        }
        // --------------------------
        // 场景2：前端传入了题目 → 计算题目数量和总分
        // --------------------------
        // 2.1 计算总题目数量：直接取Map的size（Map<题目ID, 分值>，key是题目ID，value是分值）
        paper.setQuestionCount(paperVo.getQuestions().size());
        // 2.2 计算总分数：使用Stream流将所有题目的分值累加（BigDecimal处理高精度金额/分数，避免精度丢失）
        // paperVo.getQuestions().values()：获取所有题目的分值集合
        // stream().reduce(BigDecimal::add)：将所有分值逐个累加，返回Optional<BigDecimal>（处理空集合场景）
        Optional<BigDecimal> totalScore = paperVo.getQuestions().values().stream()
                .reduce(BigDecimal::add);
        // 将累加后的总分设置到试卷实体（get()：因为前面已经判断过题目非空，所以一定有值）
        paper.setTotalScore(totalScore.get());
        // --------------------------
        // 3. 保存试卷基础信息到数据库（主表）
        // save()：MyBatis-Plus提供的通用保存方法，自动生成主键ID并回显到paper对象中
        // --------------------------
        save(paper);
        // 记录调试日志：确认试卷信息保存成功，便于后续排查问题
        log.debug("当前试卷勾选了题目信息，正常进行计算和保存！试卷对象信息为：{}", paper);
        // --------------------------
        // 4. 构建「试卷-题目」中间表关联数据
        // 核心：将前端传入的Map<题目ID, 分值> → 转换为PaperQuestion中间表实体列表
        // --------------------------
        List<PaperQuestion> paperQuestionList = paperVo.getQuestions().entrySet().stream()
                // 遍历Map的每一个entry（key=题目ID，value=题目分值）
                .map(entry -> {
                    PaperQuestion paperQuestion = new PaperQuestion();
                    // 设置试卷ID（从已保存的paper对象中获取，主键已回显）
                    paperQuestion.setPaperId(paper.getId().intValue());
      // 设置题目ID（Map的key）
                    paperQuestion.setQuestionId(entry.getKey().longValue());
                    // 设置题目在试卷中的分值（Map的value）
                    paperQuestion.setScore(entry.getValue());
                    return paperQuestion;
                })
                .collect(Collectors.toList());
        if (paperQuestionList.isEmpty()) {
            throw new BusinessException(ErrorCode.PAPER_CREATE_FAILED, "试卷题目关联数据为空");
        }
        boolean saveResult = paperQuestionService.saveBatch(paperQuestionList);
        if (!saveResult) {
            throw new BusinessException(ErrorCode.PAPER_CREATE_FAILED, "保存试卷题目关联失败");
        }
        log.info("手动组卷成功，创建的试卷对象信息为：{}", paper);
        return paper;
    }
}
