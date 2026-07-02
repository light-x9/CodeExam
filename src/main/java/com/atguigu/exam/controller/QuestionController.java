package com.atguigu.exam.controller;

import com.atguigu.exam.common.CacheConstants;
import com.atguigu.exam.common.Result;
import com.atguigu.exam.entity.Question;
import com.atguigu.exam.service.QuestionService;
import com.atguigu.exam.utils.ExcelUtil;
import com.atguigu.exam.utils.RedisUtils;
import com.atguigu.exam.vo.QuestionQueryVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/questions")
@CrossOrigin(origins = "*")
@Tag(name = "question", description = "question")
public class QuestionController {

    private static final Logger log = LoggerFactory.getLogger(QuestionController.class);

    @Autowired
    private QuestionService questionService;
    @Autowired
    private RedisUtils redisUtils;

    @GetMapping("/list")
    @Operation(summary = "list", description = "list")
    public Result<Page<Question>> getQuestionList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword) {

        QuestionQueryVo queryVo = new QuestionQueryVo();
        queryVo.setType(type);
        queryVo.setDifficulty(difficulty);
        queryVo.setCategoryId(categoryId);
        queryVo.setKeyword(keyword);

        Page<Question> pageBean = new Page<>(page, size);
        questionService.queryquestionListByPage(pageBean, queryVo);
        return Result.success(pageBean);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取题目详情", description = "根据ID获取题目详细信息，包含选项和答案")
    public Result<Question> getQuestionDetail(
            @Parameter(description = "题目ID") @PathVariable Long id) {
        try {
            Question question = questionService.queryQuestionById(id);
            return Result.success(question);
        } catch (Exception e) {
            log.error("获取题目详情失败, id={}", id, e);
            return Result.error("获取题目详情失败: " + e.getMessage());
        }
    }

    @GetMapping("/popular")
    @Operation(summary = "popular", description = "popular")
    public Result<List<Question>> getPopularQuestions(
            @RequestParam(defaultValue = "10") Integer size) {

        try {
            // 拉取更多 ID（3 倍），防止 top N 全是已删除题目导致返回空
            int fetchSize = Math.max(size * 3, 30);
            Set<Object> rawIds = redisUtils.zReverseRange(
                    CacheConstants.POPULAR_QUESTIONS_KEY, 0, fetchSize - 1);

            if (rawIds == null || rawIds.isEmpty()) {
                return Result.success(new ArrayList<>(), "no data");
            }

            List<Long> questionIds = rawIds.stream()
                    .map(id -> Long.valueOf(id.toString()))
                    .collect(Collectors.toList());

            // listByIds 会通过 MyBatis-Plus 逻辑删除自动过滤 is_deleted=1 的记录
            List<Question> questions = questionService.listByIds(questionIds);

            // 按 Redis 热度排序，只取前 size 个有效题目
            Map<Long, Question> questionMap = questions.stream()
                    .collect(Collectors.toMap(Question::getId, q -> q));
            
            List<Question> sortedQuestions = questionIds.stream()
                    .map(questionMap::get)
                    .filter(q -> q != null)
                    .limit(size)
                    .collect(Collectors.toList());

            return Result.success(sortedQuestions);

        } catch (Exception e) {
            log.error("error", e);
            return Result.error("error: " + e.getMessage());
        }
    }

    @PostMapping("/popular/refresh")
    @Operation(summary = "refresh", description = "refresh")
    public Result<Integer> refreshPopularQuestions() {
        try {
            redisUtils.delete(CacheConstants.POPULAR_QUESTIONS_KEY);
            return Result.success(0, "ok");
        } catch (Exception e) {
            return Result.error("error: " + e.getMessage());
        }
    }

    @GetMapping("/template")
    @Operation(summary = "template", description = "template")
    public ResponseEntity<byte[]> downloadTemplate() throws IOException {
        byte[] template = ExcelUtil.generateTemplate();
        return ResponseEntity.ok()
                .header("content-disposition", "attachment;filename=template.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(template);
    }

    
    /**
     * 删除题目 - 根据ID删除单个题目
     *
     * 说明：
     * 1. 检查该题目是否已被试卷关联，关联则禁止删除
     * 2. 级联删除题目选项和答案，设置isDeleted = 1
     * 3. 删除后同步清理Redis热门题目缓存中的无效ID
     *
     * 前端删除是实质删除方式，选择确认后调用后端私接实现
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除题目", description = "根据题目ID删除题目，级联删除关联选项和答案，前端删除通循后端私接实现")
    public Result<Void> deleteQuestion(
            @Parameter(description = "题目ID") @PathVariable Long id) {
        questionService.removeQuestion(id);
        // 删除题目后，同步移除 Redis 热门题目 ZSet 中的无效 ID
        redisUtils.zRemove(CacheConstants.POPULAR_QUESTIONS_KEY, id);
        return Result.success(null, "题目删除成功");
    }

    /**
     * 更新题目 - 根据ID更新题目完整信息（含选项、答案）
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新题目", description = "根据题目ID更新题目信息，含选项和答案")
    public Result<Void> updateQuestion(
            @Parameter(description = "题目ID") @PathVariable Long id,
            @RequestBody Question question) {
        question.setId(id);
        questionService.updateQuestion(question);
        return Result.success(null, "题目更新成功");
    }
}
