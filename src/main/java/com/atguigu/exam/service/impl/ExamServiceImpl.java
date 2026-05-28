package com.atguigu.exam.service.impl;

import com.atguigu.exam.common.BusinessException;
import com.atguigu.exam.common.ErrorCode;
import com.atguigu.exam.entity.*;
import com.atguigu.exam.mapper.AnswerRecordMapper;
import com.atguigu.exam.mapper.ExamRecordMapper;
import com.atguigu.exam.mapper.QuestionAnswerMapper;
import com.atguigu.exam.service.ExamService;
import com.atguigu.exam.service.PaperService;
import com.atguigu.exam.vo.StartExamVo;
import com.atguigu.exam.vo.SubmitAnswerVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * 考试服务实现类
 */
@Service
@Slf4j
public class ExamServiceImpl extends ServiceImpl<ExamRecordMapper, ExamRecord> implements ExamService {

    @Autowired
    private PaperService paperService;
    @Autowired
    private AnswerRecordMapper answerRecordMapper;
    @Autowired
    private QuestionAnswerMapper questionAnswerMapper;

    /**
     * 开始考试：校验试卷已发布 → 创建考试记录 → 返回试卷题目
     */
    @Override
    @Transactional
    public ExamRecord startExam(StartExamVo startExamVo) {
        // 1. 获取试卷详情
        Paper paper = paperService.getPaperWithQuestions(startExamVo.getPaperId());
        // 2. 只有已发布的试卷才能参加考试
        if (!"PUBLISHED".equals(paper.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "试卷未发布，无法开始考试");
        }
        // 3. 创建考试记录
        ExamRecord record = new ExamRecord();
        record.setExamId(startExamVo.getPaperId());
        record.setStudentName(startExamVo.getStudentName());
        record.setStartTime(LocalDateTime.now());
        record.setStatus("进行中");
        record.setWindowSwitches(0);
        save(record);
        // 4. 返回试卷题目（不返回答案）
        record.setPaper(paper);
        log.info("考生 {} 开始考试，考试记录ID：{}，试卷：{}", startExamVo.getStudentName(), record.getId(), paper.getName());
        return record;
    }

    /**
     * 提交答案：批量保存答题记录 → 标记考试已完成
     */
    @Override
    @Transactional
    public void submitAnswers(Integer examRecordId, List<SubmitAnswerVo> answers) {
        // 1. 校验考试记录存在且状态正确
        ExamRecord record = getById(examRecordId);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "考试记录不存在");
        }
        if (!"进行中".equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "考试状态异常，当前状态：" + record.getStatus());
        }
        // 2. 批量保存答题记录
        List<AnswerRecord> answerRecords = new ArrayList<>();
        for (SubmitAnswerVo answer : answers) {
            answerRecords.add(new AnswerRecord(examRecordId, answer.getQuestionId(), answer.getUserAnswer()));
        }
        for (AnswerRecord answerRecord : answerRecords) {
            answerRecordMapper.insert(answerRecord);
        }
        // 3. 标记考试已完成
        record.setStatus("已完成");
        record.setEndTime(LocalDateTime.now());
        updateById(record);
        log.info("考试记录 {} 提交答案完成，共 {} 道题", examRecordId, answers.size());
    }

    /**
     * 自动批阅：客观题自动判对错 + 计算总分，简答题标记为待人工评阅
     */
    @Override
    @Transactional
    public ExamRecord gradeExam(Integer examRecordId) {
        // 1. 校验考试记录存在
        ExamRecord record = getById(examRecordId);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "考试记录不存在");
        }
        // 2. 获取试卷题目及答案
        Paper paper = paperService.getPaperWithQuestions(record.getExamId());
        if (paper.getQuestions() == null || paper.getQuestions().isEmpty()) {
            throw new BusinessException(ErrorCode.PAPER_QUESTION_EMPTY);
        }
        // 3. 获取该次考试的所有答题记录
        List<AnswerRecord> answerRecords = answerRecordMapper.selectList(
                new LambdaQueryWrapper<AnswerRecord>().eq(AnswerRecord::getExamRecordId, examRecordId));
        // 4. 构建题目ID → Question 的映射
        Map<Long, Question> questionMap = paper.getQuestions().stream()
                .collect(Collectors.toMap(Question::getId, q -> q));
        // 5. 逐题判分
        int totalScore = 0;
        for (AnswerRecord ar : answerRecords) {
            Question question = questionMap.get(ar.getQuestionId().longValue());
            if (question == null) {
                ar.setIsCorrect(0);
                ar.setScore(0);
                continue;
            }
            String type = question.getType();
            if ("CHOICE".equals(type) || "JUDGE".equals(type)) {
                // 客观题：对比标准答案
                QuestionAnswer correctAnswer = question.getAnswer();
                if (correctAnswer != null && correctAnswer.getAnswer() != null
                        && correctAnswer.getAnswer().equalsIgnoreCase(ar.getUserAnswer())) {
                    ar.setIsCorrect(1);
                    ar.setScore(question.getScore());
                } else {
                    ar.setIsCorrect(0);
                    ar.setScore(0);
                }
            } else {
                // 简答题：标记为待人工评阅
                ar.setIsCorrect(2);
                ar.setScore(0);
            }
            totalScore += ar.getScore() != null ? ar.getScore() : 0;
            answerRecordMapper.updateById(ar);
        }
        // 6. 更新考试记录
        record.setScore(totalScore);
        record.setStatus("已批阅");
        updateById(record);
        // 7. 回填答题记录列表
        record.setAnswerRecords(answerRecords);
        log.info("考试记录 {} 批阅完成，总分：{}", examRecordId, totalScore);
        return record;
    }

    /**
     * 获取考试记录详情（含答题明细 + 试卷信息）
     */
    @Override
    public ExamRecord getExamRecordDetail(Integer id) {
        ExamRecord record = getById(id);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "考试记录不存在");
        }
        // 加载答题记录
        List<AnswerRecord> answerRecords = answerRecordMapper.selectList(
                new LambdaQueryWrapper<AnswerRecord>().eq(AnswerRecord::getExamRecordId, id));
        record.setAnswerRecords(answerRecords);
        // 加载试卷信息
        Paper paper = paperService.getPaperWithQuestions(record.getExamId());
        record.setPaper(paper);
        return record;
    }

    /**
     * 获取所有考试记录
     */
    @Override
    public List<ExamRecord> getRecords() {
        return list();
    }
} 