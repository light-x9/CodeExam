package com.atguigu.exam.service.impl;


import com.atguigu.exam.common.BusinessException;
import com.atguigu.exam.common.ErrorCode;
import com.atguigu.exam.entity.Paper;
import com.atguigu.exam.entity.PaperQuestion;
import com.atguigu.exam.entity.Question;
import com.atguigu.exam.entity.QuestionAnswer;
import com.atguigu.exam.entity.QuestionChoice;
import com.atguigu.exam.mapper.PaperMapper;
import com.atguigu.exam.mapper.QuestionAnswerMapper;
import com.atguigu.exam.mapper.QuestionChoiceMapper;
import com.atguigu.exam.mapper.QuestionMapper;
import com.atguigu.exam.service.PaperQuestionService;
import com.atguigu.exam.service.PaperService;
import com.atguigu.exam.service.QuestionService;
import com.atguigu.exam.vo.AiPaperVo;
import com.atguigu.exam.vo.PaperVo;
import com.atguigu.exam.vo.RuleVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;


/**
 * 试卷服务实现类
 */
@Slf4j
@Service
public class PaperServiceImpl extends ServiceImpl<PaperMapper, Paper> implements PaperService {

    @Autowired
    private PaperQuestionService paperQuestionService;
    @Autowired
    private QuestionService questionService;
    @Autowired
    private QuestionMapper questionMapper;
    @Autowired
    private QuestionAnswerMapper questionAnswerMapper;
    @Autowired
    private QuestionChoiceMapper questionChoiceMapper;
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

    /**
     * 获取试卷详情（包含题目列表）
     */
    @Override
    public Paper getPaperWithQuestions(Integer id) {
        // 1. 查询试卷基本信息
        Paper paper = getById(id);
        if (paper == null) {
            throw new BusinessException(ErrorCode.PAPER_NOT_FOUND, "id=" + id + " 的试卷不存在");
        }
        // 2. 查询该试卷关联的所有题目ID
        List<PaperQuestion> paperQuestionList = paperQuestionService.list(
                new LambdaQueryWrapper<PaperQuestion>().eq(PaperQuestion::getPaperId, id));
        if (!paperQuestionList.isEmpty()) {
            // 3. 收集题目ID列表，批量查询题目详情
            List<Long> questionIds = paperQuestionList.stream()
                    .map(PaperQuestion::getQuestionId)
                    .collect(Collectors.toList());
            List<Question> questions = questionService.listByIds(questionIds);
            // 4. 为每道题目加载答案和选项（listByIds 不会自动填充 @TableField(exist=false) 的字段）
            for (Question q : questions) {
                // 加载题目答案
                QuestionAnswer answer = questionAnswerMapper.selectOne(
                        new LambdaQueryWrapper<QuestionAnswer>().eq(QuestionAnswer::getQuestionId, q.getId()));
                q.setAnswer(answer);
                // 选择题：加载选项列表
                if ("CHOICE".equals(q.getType())) {
                    List<QuestionChoice> choices = questionChoiceMapper.selectList(
                            new LambdaQueryWrapper<QuestionChoice>().eq(QuestionChoice::getQuestionId, q.getId()));
                    q.setChoices(choices);
                }
            }
            // 5. 为每道题目设置在本试卷中的分值
            Map<Long, BigDecimal> scoreMap = paperQuestionList.stream()
                    .collect(Collectors.toMap(PaperQuestion::getQuestionId, PaperQuestion::getScore));
            for (Question q : questions) {
                q.setPaperScore(scoreMap.get(q.getId()));
            }
            paper.setQuestions(questions);
        }
        return paper;
    }

    /**
     * 更新试卷信息和题目配置
     * 已发布的试卷不允许编辑
     */
    @Override
    @Transactional
    public void updatePaper(Integer id, PaperVo paperVo) {
        // 1. 查询试卷是否存在
        Paper existingPaper = getById(id);
        if (existingPaper == null) {
            throw new BusinessException(ErrorCode.PAPER_NOT_FOUND, "id=" + id + " 的试卷不存在");
        }
        // 2. 已发布的试卷不允许编辑
        if ("PUBLISHED".equals(existingPaper.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已发布的试卷不能编辑，请先停止试卷");
        }
        // 3. 更新试卷基础信息（保留原状态）
        Paper paper = new Paper();
        BeanUtils.copyProperties(paperVo, paper);
        paper.setId(id.longValue());
        paper.setStatus(existingPaper.getStatus());
        // 4. 删除旧的试卷-题目关联
        paperQuestionService.remove(
                new LambdaQueryWrapper<PaperQuestion>().eq(PaperQuestion::getPaperId, id));
        // 5. 重新计算题目数量和总分，建立新的关联
        if (!ObjectUtils.isEmpty(paperVo.getQuestions())) {
            paper.setQuestionCount(paperVo.getQuestions().size());
            Optional<BigDecimal> totalScore = paperVo.getQuestions().values().stream()
                    .reduce(BigDecimal::add);
            paper.setTotalScore(totalScore.get());
            // 批量保存新的关联记录
            List<PaperQuestion> paperQuestionList = paperVo.getQuestions().entrySet().stream()
                    .map(entry -> new PaperQuestion(id, entry.getKey().longValue(), entry.getValue()))
                    .collect(Collectors.toList());
            paperQuestionService.saveBatch(paperQuestionList);
        } else {
            paper.setQuestionCount(0);
            paper.setTotalScore(BigDecimal.ZERO);
        }
        // 6. 更新试卷
        updateById(paper);
        log.info("试卷更新成功，试卷ID：{}", id);
    }

    /**
     * 更新试卷状态
     * 合法流转：
     *   DRAFT    → PUBLISHED （启用/发布）
     *   PUBLISHED → STOPPED   （停用）
     *   STOPPED   → PUBLISHED （重新启用）
     *   STOPPED   → DRAFT     （退回草稿编辑）
     * 禁止流转：
     *   DRAFT → STOPPED（未发布不能直接停用）
     *   PUBLISHED → DRAFT（已发布不能退回草稿，应先停用）
     */
    @Override
    public void updatePaperStatus(Integer id, String status) {
        // 1. 查询试卷是否存在
        Paper paper = getById(id);
        if (paper == null) {
            throw new BusinessException(ErrorCode.PAPER_NOT_FOUND, "id=" + id + " 的试卷不存在");
        }
        String currentStatus = paper.getStatus();
        // 2. 校验目标状态值合法性
        if (!"PUBLISHED".equals(status) && !"STOPPED".equals(status) && !"DRAFT".equals(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的状态值：" + status + "，可选值：DRAFT/PUBLISHED/STOPPED");
        }
        // 3. 不允许无变化的状态更新
        if (currentStatus.equals(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "试卷当前已经是" + status + "状态");
        }
        // 4. 校验状态流转是否合法
        if ("DRAFT".equals(currentStatus) && "STOPPED".equals(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "草稿状态的试卷不能直接停用，请先发布");
        }
        if ("PUBLISHED".equals(currentStatus) && "DRAFT".equals(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已发布的试卷不能直接退回草稿，请先停用");
        }
        // 5. 发布时校验：空试卷不能发布
        if ("PUBLISHED".equals(status) && (paper.getQuestionCount() == null || paper.getQuestionCount() == 0)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "空试卷不能发布，请先添加题目");
        }
        // 6. 更新状态
        paper.setStatus(status);
        updateById(paper);
        log.info("试卷状态变更：{} → {}，试卷ID：{}", currentStatus, status, id);
    }

    /**
     * 删除试卷
     * 已发布的试卷不能删除，需要先停止
     */
    @Override
    @Transactional
    public void deletePaper(Integer id) {
        // 1. 查询试卷是否存在
        Paper paper = getById(id);
        if (paper == null) {
            throw new BusinessException(ErrorCode.PAPER_NOT_FOUND, "id=" + id + " 的试卷不存在");
        }
        // 2. 已发布的试卷不能删除
        if ("PUBLISHED".equals(paper.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已发布的试卷不能删除，请先停止试卷");
        }
        // 3. 删除试卷-题目关联记录
        paperQuestionService.remove(
                new LambdaQueryWrapper<PaperQuestion>().eq(PaperQuestion::getPaperId, id));
        // 4. 删除试卷本身（逻辑删除，由MyBatis-Plus的@TableLogic控制）
        removeById(id);
        log.info("试卷删除成功，试卷ID：{}", id);
    }

    /**
     * AI智能组卷：根据规则从题库随机抽题组卷
     */
    @Override
    @Transactional
    public Paper createPaperWithAi(AiPaperVo aiPaperVo) {
        // 1. 校验规则列表非空
        if (ObjectUtils.isEmpty(aiPaperVo.getRules())) {
            throw new BusinessException(ErrorCode.PAPER_QUESTION_EMPTY, "AI组卷规则不能为空");
        }
        // 2. 构建试卷实体
        Paper paper = new Paper();
        paper.setName(aiPaperVo.getName());
        paper.setDescription(aiPaperVo.getDescription());
        paper.setDuration(aiPaperVo.getDuration());
        paper.setStatus("DRAFT");
        // 3. 根据每条规则，从题库随机抽取题目
        List<PaperQuestion> allPaperQuestions = new ArrayList<>();
        int totalQuestionCount = 0;
        BigDecimal totalScore = BigDecimal.ZERO;
        for (RuleVo rule : aiPaperVo.getRules()) {
            // 构建查询条件：按题型筛选，可选按分类筛选
            LambdaQueryWrapper<Question> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Question::getType, rule.getType().name());
            if (!ObjectUtils.isEmpty(rule.getCategoryIds())) {
                queryWrapper.in(Question::getCategoryId, rule.getCategoryIds());
            }
            List<Question> candidates = questionMapper.selectList(queryWrapper);
            if (candidates.size() < rule.getCount()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        rule.getType() + " 类型题目不足，题库仅有 " + candidates.size() + " 道，需要 " + rule.getCount() + " 道");
            }
            // 随机抽取指定数量的题目
            Collections.shuffle(candidates);
            List<Question> selected = candidates.subList(0, rule.getCount());
            // 4. 计算该规则对应的分数
            BigDecimal scorePerQuestion = new BigDecimal(rule.getScore());
            for (Question q : selected) {
                allPaperQuestions.add(new PaperQuestion(null, q.getId(), scorePerQuestion));
            }
            totalQuestionCount += rule.getCount();
            totalScore = totalScore.add(scorePerQuestion.multiply(new BigDecimal(rule.getCount())));
        }
        // 5. 设置试卷总分和题目数量
        paper.setQuestionCount(totalQuestionCount);
        paper.setTotalScore(totalScore);
        // 6. 保存试卷
        save(paper);
        // 7. 保存试卷-题目关联（回填paperId）
        for (PaperQuestion pq : allPaperQuestions) {
            pq.setPaperId(paper.getId().intValue());
        }
        paperQuestionService.saveBatch(allPaperQuestions);
        log.info("AI智能组卷成功，试卷ID：{}，共{}道题，总分{}", paper.getId(), totalQuestionCount, totalScore);
        return paper;
    }
}
