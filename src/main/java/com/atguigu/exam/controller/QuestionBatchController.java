package com.atguigu.exam.controller;


import com.atguigu.exam.common.Result;
import com.atguigu.exam.service.QuestionService;
import com.atguigu.exam.utils.ExcelUtil;
import com.atguigu.exam.vo.AiGenerateRequestVo;
import com.atguigu.exam.vo.QuestionImportVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 题目批量管理控制器 - 处理题目批量操作相关的HTTP请求
 * 包括Excel导入、AI生成题目、批量验证等功能
 */
@Slf4j  // 日志注解
@RestController  // REST控制器，返回JSON数据
@RequestMapping("/api/questions/batch")  // 题目批量操作API路径前缀
@CrossOrigin(origins = "*")  // 允许跨域访问
@Tag(name = "题目批量操作", description = "题目批量管理相关操作，包括Excel导入、AI生成题目、批量验证等功能")  // Swagger API分组
public class QuestionBatchController {
 @Autowired
private QuestionService questionService;
    /**
     * 下载Excel导入模板
     * @return Excel模板文件
     */
    @GetMapping("/template")  // 处理GET请求
    @Operation(summary = "下载Excel导入模板", description = "下载题目批量导入的Excel模板文件")  // API描述
    public ResponseEntity<byte[]> downloadTemplate() {

        return null;
    }





    /**
     * 预览Excel文件内容（不入库）
     * @param file Excel文件
     * @return 解析出的题目列表
     */
    @PostMapping("/preview-excel")  // 处理POST请求
    @Operation(summary = "预览Excel文件内容", description = "解析并预览Excel文件中的题目内容，不会导入到数据库")  // API描述
    public Result<List<QuestionImportVo>> previewExcel(
            @Parameter(description = "Excel文件，支持.xls和.xlsx格式") @RequestParam("file") MultipartFile file) throws IOException {
        // 调用业务层方法，解析Excel文件并返回题目预览列表
        List<QuestionImportVo> questionImportVoList = questionService.previewExcel(file);

        // 记录接口调用结束日志，输出解析后的题目数据（便于排查问题）
        log.info("生成预览数据接口调用结束！数据为：{}", questionImportVoList);

        // 封装成功结果并返回给前端
        return Result.success(questionImportVoList);
    }



    /**
     * 从Excel文件批量导入题目
     * @param file Excel文件
     * @return 导入结果
     */
    @PostMapping("/import-excel")  // 处理POST请求
    @Operation(summary = "从Excel文件批量导入题目", description = "解析Excel文件并将题目批量导入到数据库")  // API描述
    public Result<String> importFromExcel(
            @Parameter(description = "Excel文件，包含题目数据") @RequestParam("file") MultipartFile file) {
      return null;
    }

    /**
     * AI智能生成题目 - 控制器接口方法
     * 功能：接收前端传递的生成要求，调用AI服务生成题目，返回预览数据【不存入数据库】
     * 请求方式：POST
     * 请求路径：/ai-generate
     *
     * @param request 前端传递的AI生成参数（包含：题目主题、生成数量、难度、题型等）
     *                @RequestBody  表示参数从请求体中获取（JSON格式）
     *                @Validated    开启参数校验，保证主题、数量等参数合法
     * @return 统一返回结果，封装生成好的题目列表（List<QuestionImportVo>）
     */
    @PostMapping("/ai-generate")
    @Operation(summary = "AI智能生成题目", description = "根据主题、数量、难度要求，调用AI自动生成题目，仅预览不入库")
    public Result<List<QuestionImportVo>> generateQuestionsByAi(@RequestBody @Validated AiGenerateRequestVo request) {

        // 1. 调用业务层方法，执行AI生成题目核心逻辑
        // 内部流程：构建提示词 → 调用Kimi接口 → 解析返回结果 → 封装成题目VO列表
        List<QuestionImportVo> questionImportVoList = questionService.aiGenerateQuestions(request);

        // 2. 打印日志，记录本次AI生成的关键信息，方便后续排查问题
        // 日志内容：生成主题、期望生成数量、实际生成数量
        log.info("使用ai生成题目调用完毕，需要生成题目的标题为：{}，希望生成题目数量为：{}，最终生成题目数量为：{}",
                request.getTopic(),      // 生成题目的主题/知识点
                request.getCount(),      // 前端要求生成的数量
                questionImportVoList.size()); // 实际AI生成并解析成功的题目数量

        // 3. 将生成好的题目列表封装成统一成功结果，返回给前端进行预览展示
        return Result.success(questionImportVoList);
    }



    /**
     * 批量导入题目（通用接口，支持Excel导入或AI生成后的确认导入）
     * @param questions 题目导入DTO列表（前端传递的结构化题目数据）
     * @return 导入结果（成功/失败提示信息）
     */
// 处理POST请求，接口路径为 /import-questions
    @PostMapping("/import-questions")
// Knife4j/Swagger接口文档注解：summary为接口摘要，description为详细功能描述
    @Operation(summary = "批量导入题目", description = "将题目列表批量导入到数据库，支持Excel解析后的导入或AI生成后的确认导入")
    public Result<String> importQuestions(@RequestBody List<QuestionImportVo> questions) {
        // 调用业务层方法，执行题目批量导入逻辑，返回导入结果信息
        String result = questionService.importQuestions(questions);
        // 记录导入结果日志，便于排查问题和追踪操作
        log.info(result);
        // 封装成功结果并返回给前端：
        // - 业务层返回的result作为数据内容
        // - 额外指定前端提示消息为"批量导入成功！"
        return Result.success(result, "批量导入成功！");
    }




    /**
     * 验证题目数据
     * @param questions 题目列表
     * @return 验证结果
     */
    @PostMapping("/validate")  // 处理POST请求
    @Operation(summary = "验证题目数据", description = "验证题目数据的完整性和格式正确性，返回验证结果和错误信息")  // API描述
    public Result<String> validateQuestions(@RequestBody List<QuestionImportVo> questions) {

        return Result.error("验证题目数据失败!");
    }
    
    /**
     * 验证单个题目数据
     * @param question 题目数据
     * @param index 题目序号
     * @return 错误信息，如果为null表示验证通过
     */
    private String validateSingleQuestion(QuestionImportVo question, int index) {
        // 验证基本字段
        if (question.getTitle() == null || question.getTitle().trim().isEmpty()) {
            return String.format("第%d题：题目内容不能为空", index);
        }
        
        if (question.getType() == null || question.getType().trim().isEmpty()) {
            return String.format("第%d题：题目类型不能为空", index);
        }
        
        if (!"CHOICE".equals(question.getType()) && !"JUDGE".equals(question.getType()) && !"TEXT".equals(question.getType())) {
            return String.format("第%d题：题目类型必须是CHOICE、JUDGE或TEXT", index);
        }
        
        // 验证选择题特有字段
        if ("CHOICE".equals(question.getType())) {
            if (question.getChoices() == null || question.getChoices().isEmpty()) {
                return String.format("第%d题：选择题必须有选项", index);
            }
            
            if (question.getChoices().size() < 2) {
                return String.format("第%d题：选择题至少需要2个选项", index);
            }
            
            boolean hasCorrectAnswer = question.getChoices().stream()
                    .anyMatch(choice -> choice.getIsCorrect() != null && choice.getIsCorrect());
            
            if (!hasCorrectAnswer) {
                return String.format("第%d题：选择题必须有正确答案", index);
            }
        } else {
            // 判断题和简答题需要答案
            if (question.getAnswer() == null || question.getAnswer().trim().isEmpty()) {
                return String.format("第%d题：%s必须有答案", index, 
                    "JUDGE".equals(question.getType()) ? "判断题" : "简答题");
            }
        }
        
        return null; // 验证通过
    }
} 