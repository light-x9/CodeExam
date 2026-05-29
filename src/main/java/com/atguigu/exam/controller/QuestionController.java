package com.atguigu.exam.controller;

import com.atguigu.exam.annotation.OperationLog;
import com.atguigu.exam.annotation.RequireRole;
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
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@RestController
@RequestMapping("/api/questions")
@CrossOrigin(origins = "*")
@Tag(name = "题目管理", description = "题目相关的增删改查操作，包括分页查询、随机获取、热门推荐等功能")
public class QuestionController {
    @Autowired
    private QuestionService questionService;
    @Autowired
    private RedisUtils redisUtils;

    @GetMapping("/list")
    @Operation(summary = "分页查询题目列表", description = "支持按分类、难度、题型、关键字进行多条件筛选的分页查询")
    public Result<Page<Question>> getQuestionList(
            @Parameter(description = "当前页码，从1开始，默认第1页") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页显示数量，默认20条") @RequestParam(defaultValue = "20") Integer size,
            @Parameter(description = "题目类型：CHOICE/JUDGE/TEXT") @RequestParam(required = false) String type,
            @Parameter(description = "难度等级：EASY/MEDIUM/HARD") @RequestParam(required = false) String difficulty,
            @Parameter(description = "分类ID") @RequestParam(required = false) Long categoryId,
            @Parameter(description = "搜索关键字") @RequestParam(required = false) String keyword) {

        QuestionQueryVo queryVo = new QuestionQueryVo();
        queryVo.setType(type);
        queryVo.setDifficulty(difficulty);
        queryVo.setCategoryId(categoryId);
        queryVo.setKeyword(keyword);

        Page<Question> pageBean = new Page<>(page, size);
        questionService.queryquestionListByPage(pageBean, queryVo);
        return Result.success(pageBean);
    }

    /**
     * 获取热门题目 - 从 Redis ZSet 中读取访问次数最多的题目
     * 
     * ==================== 修复说明 ====================
     * 之前这个接口直接返回 Result.error("获取热门题目失败")
     * 现在改为从 Redis ZSet question:popular 中读取真实数据
     * 
     * 数据来源：用户查看题目详情 (queryQuestionById) 时会异步更新 Redis
     * 数据格式：Redis ZSet，key=question:popular，member=题目ID，score=访问次数
     */
    @GetMapping("/popular")
    @Operation(summary = "获取热门题目", description = "获取访问次数最多的热门题目，用于首页推荐展示")
    public Result<List<Question>> getPopularQuestions(
            @Parameter(description = "返回题目数量", example = "10") @RequestParam(defaultValue = "10") Integer size) {

        try {
            // 1. 从 Redis ZSet 中获取热门题目 ID（按分数从高到低）
            Set<Object> popularIds = redisUtils.zReverseRange(
                    CacheConstants.POPULAR_QUESTIONS_KEY, 0, size - 1);

            if (popularIds == null || popularIds.isEmpty()) {
                log.info("Redis 中暂无热门题目数据");
                return Result.success(new ArrayList<>(), "暂无热门题目");
            }

            // 2. 将 ID 转为 Long 列表
            List<Long> questionIds = popularIds.stream()
                    .map(id -> Long.valueOf(id.toString()))
                    .collect(Collectors.toList());

            // 3. 从数据库批量查询题目（只返回基本信息，不加载答案）
            List<Question> questions = questionService.listByIds(questionIds);

            // 4. 按 Redis 中的排名顺序排序（保持热度顺序）
            Map<Long, Question> questionMap = questions.stream()
                    .collect(Collectors.toMap(Question::getId, q -> q));
            
            List<Question> sortedQuestions = questionIds.stream()
                    .map(questionMap::get)
                    .filter(q -> q != null)
                    .collect(Collectors.toList());

            log.info("获取热门题目成功，返回 {} 条", sortedQuestions.size());
            return Result.success(sortedQuestions);

        } catch (Exception e) {
            log.error("获取热门题目失败", e);
            return Result.error("获取热门题目失败: " + e.getMessage());
        }
    }

    /**
     * 刷新热门题目缓存 - 管理员功能
     */
    @PostMapping("/popular/refresh")
    @Operation(summary = "刷新热门题目缓存", description = "管理员功能，重置或初始化热门题目的访问计数")
    public Result<Integer> refreshPopularQuestions() {
        try {
            // 删除旧的 ZSet，让系统重新积累
            redisUtils.delete(CacheConstants.POPULAR_QUESTIONS_KEY);
            log.info("热门题目缓存已刷新");
            return Result.success(0, "热门题目缓存已刷新");
        } catch (Exception e) {
            log.error("刷新热门题目缓存失败", e);
            return Result.error("刷新失败: " + e.getMessage());
        }
    }

    @GetMapping("/template")
    @Operation(summary = "下载Excel导入模板", description = "下载题目批量导入的Excel模板文件")
    public ResponseEntity<byte[]> downloadTemplate() throws IOException {
        byte[] template = ExcelUtil.generateTemplate();
        System.out.println("生成的Excel字节长度：" + template.length);
        return ResponseEntity.ok()
                .header("content-disposition", "attachment;filename=template.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(template);
    }
}