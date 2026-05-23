package com.atguigu.exam.controller;


import com.atguigu.exam.common.Result;
import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.service.ExamService;
import com.atguigu.exam.vo.StartExamVo;
import com.atguigu.exam.vo.SubmitAnswerVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 考试控制器 - 处理考试流程相关的HTTP请求
 * 包括开始考试、提交答案、AI批阅、成绩查询等功能
 */
@RestController
@RequestMapping("/api/exams")
@CrossOrigin(origins = "*")
@Tag(name = "考试管理", description = "考试流程相关操作，包括开始考试、答题提交、AI批阅、成绩查询等功能")
public class ExamController {

    @Autowired
    private ExamService examService;

    /**
     * 开始考试 - 创建新的考试记录
     * @param startExamVo 开始考试请求DTO
     * @return 考试记录
     */
    @PostMapping("/start")
    @Operation(summary = "开始考试", description = "学生开始考试，创建考试记录并返回试卷内容")
    public Result<ExamRecord> startExam(@RequestBody StartExamVo startExamVo) {
        ExamRecord record = examService.startExam(startExamVo);
        return Result.success(record, "考试开始成功");
    }

    /**
     * 提交答案 - 学生提交考试答案
     * @param examRecordId 考试记录ID
     * @param answers      答案列表
     */
    @PostMapping("/{examRecordId}/submit")
    @Operation(summary = "提交考试答案", description = "学生提交考试答案，系统记录答题情况")
    public Result<Void> submitAnswers(
            @Parameter(description = "考试记录ID") @PathVariable Integer examRecordId,
            @RequestBody List<SubmitAnswerVo> answers) {
        examService.submitAnswers(examRecordId, answers);
        return Result.success("答案提交成功");
    }

    /**
     * AI自动批阅 - 触发试卷智能批阅
     * @param examRecordId 考试记录ID
     */
    @PostMapping("/{examRecordId}/grade")
    @Operation(summary = "自动批阅", description = "自动批阅客观题并计算总分，简答题留待人工审批")
    public Result<ExamRecord> gradeExam(
            @Parameter(description = "考试记录ID") @PathVariable Integer examRecordId) {
        ExamRecord record = examService.gradeExam(examRecordId);
        return Result.success(record, "试卷批阅完成");
    }

    /**
     * 根据ID获取考试记录详情 - 查询具体考试结果
     */
    @GetMapping("/{id}")
    @Operation(summary = "查询考试记录详情", description = "获取指定考试记录的详细信息，包括答题情况和得分")
    public Result<ExamRecord> getExamRecordById(
            @Parameter(description = "考试记录ID") @PathVariable Integer id) {
        ExamRecord record = examService.getExamRecordDetail(id);
        return Result.success(record);
    }

    /**
     * 获取考试记录列表 - 查询所有考试记录
     */
    @GetMapping("/records")
    @Operation(summary = "获取考试记录列表", description = "获取所有考试记录列表，包含基本信息和成绩")
    public Result<List<ExamRecord>> getMyRecords() {
        List<ExamRecord> records = examService.getRecords();
        return Result.success(records);
    }
} 