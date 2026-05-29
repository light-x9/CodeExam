package com.atguigu.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 考试记录表 - 存储学生的考试过程和结果数据
 * 
 * ==================== 为什么冗余存储 studentNo 和 studentName？ ====================
 * 
 * 真实业务中，考试记录通常冗余保存用户信息，原因是：
 * 
 * 1. 历史追溯：学生毕业后改名，但历史成绩记录应保持不变
 * 2. 查询性能：直接查 exam_records 就能看到学号+姓名，无需 JOIN users 表
 * 3. 数据稳定性：用户表删除了，成绩记录依然有效（考试是既成事实）
 */
@TableName(value ="exam_records")
@Data
@Schema(description = "考试记录信息")
public class ExamRecord extends BaseEntity {

    @Schema(description = "试卷ID，关联的考试试卷", example = "1")
    private Integer examId;

    @Schema(description = "用户ID（登录考生），关联 user 表", example = "1")
    private Long userId;

    @Schema(description = "学号/工号（冗余存储，来自 users 表）", example = "20230001")
    private String studentNo;

    @Schema(description = "考生姓名（冗余存储，来自 users 表）", example = "张三")
    private String studentName;

    @Schema(description = "考试得分", example = "85")
    private Integer score;

    @Schema(description = "考试开始时间", example = "2024-01-15 09:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startTime;

    @Schema(description = "考试结束时间", example = "2024-01-15 11:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime endTime;

    @Schema(description = "考试状态", example = "已批阅",
            allowableValues = {"进行中", "已完成", "已批阅", "批阅中"})
    private String status;

    @Schema(description = "窗口切换次数，用于监控考试过程中的异常行为", example = "2")
    private Integer windowSwitches;

    @Schema(description = "详细的答题记录列表，包含每题的答案和得分情况")
    @TableField(exist = false)
    private List<AnswerRecord> answerRecords;

    @Schema(description = "关联的试卷信息，包含试卷详细内容和题目")
    @TableField(exist = false)
    private Paper paper;

}