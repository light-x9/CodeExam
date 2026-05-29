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
     */
    @PostMapping("/start")
    @Operation(summary = "开始考试", description = "学生开始考试，创建考试记录并返回试卷内容")
    public Result<ExamRecord> startExam(@RequestBody StartExamVo startExamVo) {
        ExamRecord record = examService.startExam(startExamVo);
        return Result.success(record, "考试开始成功");
    }

    /**
     * 提交答案 - 学生提交考试答案
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
     */
    @PostMapping("/{examRecordId}/grade")
    @Operation(summary = "自动批阅", description = "客观题即时判分并返回结果，简答题后台异步AI评分，状态为'批阅中'时可轮询刷新获取最终结果")
    public Result<ExamRecord> gradeExam(
            @Parameter(description = "考试记录ID") @PathVariable Integer examRecordId) {
        ExamRecord record = examService.gradeExam(examRecordId);
        if ("批阅中".equals(record.getStatus())) {
            return Result.success(record, "客观题已批阅完成，主观题已提交AI异步批阅，请稍后刷新查看最终成绩");
        }
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
     * 获取当前登录用户的考试记录列表
     * 只返回当前用户自己的记录，不包含其他用户
     */
    @GetMapping("/records")
    @Operation(summary = "获取我的考试记录", description = "获取当前登录用户的所有考试记录列表，包含基本信息和成绩")
    public Result<List<ExamRecord>> getMyRecords() {
        List<ExamRecord> records = examService.getMyRecords();
        return Result.success(records);
    }
}