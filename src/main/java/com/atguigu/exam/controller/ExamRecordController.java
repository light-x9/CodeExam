package com.atguigu.exam.controller;

import com.atguigu.exam.common.Result;
import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.service.ExamRecordService;
import com.atguigu.exam.service.ExamService;
import com.atguigu.exam.vo.ExamRankingVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 考试记录控制器 - 处理考试记录管理相关的HTTP请求
 * 包括考试记录查询、分页展示、成绩排行榜等功能
 */
@Slf4j
@RestController
@RequestMapping("/api/exam-records")
@Tag(name = "考试记录管理", description = "考试记录相关操作，包括记录查询、成绩管理、排行榜展示等功能")
public class ExamRecordController {

    @Autowired
    private ExamRecordService examRecordService;

    @Autowired
    private ExamService examService;

    /**
     * 分页查询考试记录
     * 管理员后台成绩管理页面调用此接口
     */
    @GetMapping("/list")
    @Operation(summary = "分页查询考试记录", description = "支持多条件筛选的考试记录分页查询，包括按姓名、状态、时间范围等筛选")
    public Result<Page<ExamRecord>> getExamRecords(
            @Parameter(description = "当前页码，从1开始", example = "1") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页显示数量", example = "20") @RequestParam(defaultValue = "20") Integer size,
            @Parameter(description = "学生姓名筛选条件") @RequestParam(required = false) String studentName,
            @Parameter(description = "学号筛选条件") @RequestParam(required = false) String studentNumber,
            @Parameter(description = "考试状态，0-进行中，1-已完成，2-已批阅") @RequestParam(required = false) Integer status,
            @Parameter(description = "开始日期，格式：yyyy-MM-dd") @RequestParam(required = false) String startDate,
            @Parameter(description = "结束日期，格式：yyyy-MM-dd") @RequestParam(required = false) String endDate
    ) {
        // 构建查询条件（简单实现：按学生姓名模糊查询）
        LambdaQueryWrapper<ExamRecord> wrapper = new LambdaQueryWrapper<>();
        if (studentName != null && !studentName.isEmpty()) {
            wrapper.like(ExamRecord::getStudentName, studentName);
        }
        // 按创建时间倒序：最新记录在前
        wrapper.orderByDesc(ExamRecord::getCreateTime);

        Page<ExamRecord> pageResult = examRecordService.page(
                new Page<>(page, size), wrapper);

        return Result.success(pageResult);
    }

    /**
     * 根据ID获取考试记录详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取考试记录详情", description = "根据记录ID获取考试记录的详细信息，包括试卷内容和答题情况")
    public Result<ExamRecord> getExamRecordById(
            @Parameter(description = "考试记录ID") @PathVariable Integer id) {

        ExamRecord record = examService.getExamRecordDetail(id);
        return Result.success(record);
    }

    /**
     * 删除考试记录
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除考试记录", description = "根据ID删除指定的考试记录")
    public Result<Void> deleteExamRecord(
            @Parameter(description = "考试记录ID") @PathVariable Integer id) {

        boolean removed = examRecordService.removeById(id);
        if (removed) {
            log.info("考试记录 {} 已删除", id);
            return Result.success("删除成功");
        }
        return Result.error("删除失败：记录不存在");
    }

    /**
     * 获取考试排行榜
     * 按分数降序排列
     */
    @GetMapping("/ranking")
    @Operation(summary = "获取考试排行榜", description = "获取考试成绩排行榜，支持按试卷筛选和限制显示数量")
    public Result<List<ExamRecord>> getExamRanking(
            @Parameter(description = "试卷ID，可选，不传则显示所有试卷的排行") @RequestParam(required = false) Integer paperId,
            @Parameter(description = "显示数量限制，可选，不传则返回所有记录") @RequestParam(required = false) Integer limit
    ) {
        LambdaQueryWrapper<ExamRecord> wrapper = new LambdaQueryWrapper<>();
        // 只展示已批阅的记录
        wrapper.eq(ExamRecord::getStatus, "已批阅");
        if (paperId != null) {
            wrapper.eq(ExamRecord::getExamId, paperId);
        }
        wrapper.orderByDesc(ExamRecord::getScore);
        // 限制数量
        if (limit != null && limit > 0) {
            wrapper.last("LIMIT " + limit);
        }

        List<ExamRecord> records = examRecordService.list(wrapper);
        return Result.success(records);
    }
    /**
     * ????????
     */
    @DeleteMapping("/batch")
    @Operation(summary = "????????")
    public Result<String> batchDeleteExamRecords(@RequestBody List<Integer> ids) {
        int successCount = 0;
        List<String> failures = new ArrayList<>();
        for (Integer id : ids) {
            boolean removed = examRecordService.removeById(id);
            if (removed) {
                successCount++;
            } else {
                failures.add("ID=" + id + ": ?????");
            }
        }
        String message = "???? " + successCount + " ???";
        if (!failures.isEmpty()) {
            message += "?" + failures.size() + " ???: " + String.join("; ", failures);
        }
        return Result.success(message);
    }


}