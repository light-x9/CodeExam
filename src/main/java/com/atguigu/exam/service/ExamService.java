package com.atguigu.exam.service;

import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.vo.StartExamVo;
import com.atguigu.exam.vo.SubmitAnswerVo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 考试服务接口
 */
public interface ExamService extends IService<ExamRecord> {

    /**
     * 开始考试：校验试卷状态，创建考试记录，返回试卷详情
     */
    ExamRecord startExam(StartExamVo startExamVo);

    /**
     * 提交答案：保存所有答题，标记考试已完成
     */
    void submitAnswers(Integer examRecordId, List<SubmitAnswerVo> answers);

    /**
     * 自动批阅：客观题自动判对错，计算总分，主观题提交异步AI批改
     */
    ExamRecord gradeExam(Integer examRecordId);

    /**
     * 根据ID获取考试记录（含答题详情和试卷信息）
     */
    ExamRecord getExamRecordDetail(Integer id);

    /**
     * 获取所有考试记录
     */
    List<ExamRecord> getRecords();

    /**
     * 获取当前登录用户的考试记录（只返回自己的）
     */
    List<ExamRecord> getMyRecords();
}