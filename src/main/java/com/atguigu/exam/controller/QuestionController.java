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

    @GetMapping("/popular")
    @Operation(summary = "popular", description = "popular")
    public Result<List<Question>> getPopularQuestions(
            @RequestParam(defaultValue = "10") Integer size) {

        try {
            Set<Object> popularIds = redisUtils.zReverseRange(
                    CacheConstants.POPULAR_QUESTIONS_KEY, 0, size - 1);

            if (popularIds == null || popularIds.isEmpty()) {
                return Result.success(new ArrayList<>(), "no data");
            }

            List<Long> questionIds = popularIds.stream()
                    .map(id -> Long.valueOf(id.toString()))
                    .collect(Collectors.toList());

            List<Question> questions = questionService.listByIds(questionIds);

            Map<Long, Question> questionMap = questions.stream()
                    .collect(Collectors.toMap(Question::getId, q -> q));
            
            List<Question> sortedQuestions = questionIds.stream()
                    .map(questionMap::get)
                    .filter(q -> q != null)
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
     * ɾ����Ŀ - ����IDɾ��������Ŀ
     *
     * ���̣�
     * 1. �����Ŀ�Ƿ��Ծ����ã������ֹɾ��
     * 2. �߼�ɾ����Ŀ�����isDeleted = 1��
     * 3. ɾ��������ѡ��ʹ�
     *
     * ǰ������ɾ��ʵ�ַ�ʽ��ѡ�ж����Ŀ��ѭ��������ô˽ӿ�
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "ɾ����Ŀ", description = "������ĿIDɾ����Ŀ���ѱ��Ծ����õ���Ŀ����ɾ������ǰ������ɾ��ͨ��ѭ�����ô˽ӿ�ʵ��")
    public Result<Void> deleteQuestion(
            @Parameter(description = "��ĿID") @PathVariable Long id) {
        questionService.removeQuestion(id);
        return Result.success(null, "��Ŀɾ���ɹ�");
    }
}