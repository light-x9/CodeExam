package com.atguigu.exam.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.atguigu.exam.common.BusinessException;
import com.atguigu.exam.common.ErrorCode;
import com.atguigu.exam.entity.PaperQuestion;
import com.atguigu.exam.entity.Question;
import com.atguigu.exam.entity.QuestionAnswer;
import com.atguigu.exam.entity.QuestionChoice;
import com.atguigu.exam.mapper.PaperQuestionMapper;
import com.atguigu.exam.mapper.QuestionAnswerMapper;
import com.atguigu.exam.mapper.QuestionChoiceMapper;
import com.atguigu.exam.mapper.QuestionMapper;
import com.atguigu.exam.service.KimiAiService;
import com.atguigu.exam.service.QuestionScoreAsyncService;
import com.atguigu.exam.service.QuestionService;
import com.atguigu.exam.utils.ExcelUtil;
import com.atguigu.exam.utils.JsonSchemaValidator;
import com.atguigu.exam.utils.RedisUtils;
import com.atguigu.exam.vo.AiGenerateRequestVo;
import com.atguigu.exam.vo.QuestionImportVo;
import com.atguigu.exam.vo.QuestionQueryVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 题目Service实现类
 * 实现题目相关的业务逻辑
 */
@Slf4j
@Service
public class QuestionServiceImpl extends ServiceImpl<QuestionMapper, Question> implements QuestionService {
    @Autowired
    private QuestionMapper questionMapper;
    @Autowired
    private QuestionAnswerMapper questionAnswerMapper;
    @Autowired
    private QuestionChoiceMapper questionChoiceMapper;
    @Autowired
    private PaperQuestionMapper paperQuestionMapper;
    // 注入 Kimi AI 服务实例，用于调用 Kimi API 接口
    @Autowired
    private KimiAiService kimiAiService;
    // 注入 Redis 工具类，用于操作 Redis 缓存
    @Autowired
    private RedisUtils redisUtils;
    // 注入异步任务服务，用线程池替代 new Thread()
    @Autowired
    private QuestionScoreAsyncService questionScoreAsyncService;

    @Override
    //分页查询题目列表
    public void queryquestionListByPage(Page<Question> pageBean, QuestionQueryVo questionQueryVo) {
        pageBean.setRecords(questionMapper.customPage(pageBean, questionQueryVo));
    }

    //根据ID查询题目详情
    @Override
    public Question queryQuestionById(Long id) {
        Question question = getById(id);
        if (question == null) {
            throw new BusinessException(ErrorCode.QUESTION_NOT_FOUND,
                    "id=" + id + " 的题目不存在");
        }

        QuestionAnswer questionAnswer = questionAnswerMapper.selectOne(new LambdaQueryWrapper<QuestionAnswer>().eq(QuestionAnswer::getQuestionId, id));
        question.setAnswer(questionAnswer);

        if ("CHOICE".equals(question.getType())) {
            List<QuestionChoice> questionChoices = questionChoiceMapper.selectList(
                    new LambdaQueryWrapper<QuestionChoice>()
                            .eq(QuestionChoice::getQuestionId, id)
                            .orderByAsc(QuestionChoice::getSort));
            question.setChoices(questionChoices);
        }
        //5. 异步更新Redis热榜分数（使用线程池，不再 new Thread）
        questionScoreAsyncService.incrementQuestionScore(question.getId());
        return question;
    }


    /**
     * 保存题目
     * <p>
     * 核心功能：
     * 1. 题目标题唯一性校验（同一类型下不能有相同标题）
     * 2. 保存题目主体信息到 question 表
     * 3. 保存答案信息到 question_answer 表
     * 4. 如果是选择题，保存选项到 question_choice 表
     * 5. 自动拼接选择题的正确答案字符串（如"A,B,C"）
     * <p>
     * 事务控制：
     * - @Transactional(rollbackFor = Exception.class)：所有异常都会回滚
     * - 保证题目、答案、选项要么全部保存成功，要么全部失败
     * - 避免数据不一致的情况
     * <p>
     * 选择题答案拼接逻辑详解：
     * - 遍历所有选项
     * - 找到 isCorrect=true 的选项
     * - 将选项的序号转换为字母（0->A, 1->B, 2->C...）
     * - 多个正确答案用逗号分隔（如"A,B,D"）
     *
     * @param question 题目对象
     *                 必填字段：type（类型）、title（标题）
     *                 可选字段：answer（答案对象）、choices（选项列表）
     *                 <p>
     *                 SQL 执行示例：
     *                 1. SELECT count(*) FROM questions WHERE type=? AND title=?;
     *                 2. INSERT INTO questions (type, title, ...) VALUES (?, ?, ...);
     *                 3. INSERT INTO question_answer (question_id, answer, keywords) VALUES (?, ?, ?);
     *                 4. INSERT INTO question_choice (question_id, content, is_correct, sort) VALUES (?, ?, ?, ?);
     */
    @Transactional(rollbackFor = Exception.class)  // 事务注解：任何异常都会回滚，保证数据一致性
    @Override
    public void saveQuestion(Question question) {
        // ━━━━━━━━ 【学习日志】Service 层入口 ━━━━━━━━
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║  [4] QuestionServiceImpl.saveQuestion() - 业务逻辑层   ║");
        System.out.println("╠══════════════════════════════════════════════════════╣");
        System.out.println("║  线程：" + Thread.currentThread().getName() + "（拦截器→AOP→Controller→Service，始终同一线程！）");
        System.out.println("║  UserContext.get() = " + com.atguigu.exam.context.UserContext.get());
        System.out.println("║  ★ Service 层也无需传 userId，直接从 ThreadLocal 取！");
        System.out.println("║  ★ 这就是 ThreadLocal 的威力：一次 set，整个请求链路共享");
        System.out.println("╚══════════════════════════════════════════════════════╝");
        System.out.println();

        // 【步骤 1】校验标题唯一性
        // 构建查询条件：检查同一类型下是否有相同标题的题目
        LambdaQueryWrapper<Question> queryWrapper = new LambdaQueryWrapper<>();
        // eq：等于条件，type = ?
        queryWrapper.eq(Question::getType, question.getType());
        // eq：等于条件，title = ?
        queryWrapper.eq(Question::getTitle, question.getTitle());

        // count()：统计符合条件的记录数
        long count = count(queryWrapper);

        // 如果已存在，抛出异常阻止保存
        if (count > 0) {
            throw new BusinessException(ErrorCode.QUESTION_TITLE_DUPLICATE,
                    question.getType() + " 类型下已存在题目《" + question.getTitle() + "》");
        }

        // 【步骤 2】保存题目主体信息
        // save()：继承自 ServiceImpl 的方法，执行 INSERT 操作
        // 保存后，question.getId() 会被自动填充数据库生成的主键 ID
        save(question);

        // 【步骤 3】处理答案信息
        // 从题目对象中获取答案对象
        QuestionAnswer answer = question.getAnswer();

        // 如果答案对象为 null，创建一个新的实例
        if (answer == null) {
            answer = new QuestionAnswer();
        }

        // 设置外键关联：将答案与题目绑定
        answer.setQuestionId(question.getId());

        // 【步骤 4】处理选择题的特殊逻辑
        if ("CHOICE".equals(question.getType())) {
            // 获取所有选项列表
            List<QuestionChoice> questionChoices = question.getChoices();

            // StringBuilder：用于高效拼接字符串
            // 相比 String 的 + 操作，StringBuilder 性能更好（尤其在循环中）
            StringBuilder sb = new StringBuilder();

            // 遍历所有选项
            for (int i = 0; i < questionChoices.size(); i++) {
                // 获取当前选项
                QuestionChoice choice = questionChoices.get(i);

                // 设置选项的排序号（从 0 开始递增）
                choice.setSort(i);

                // 设置外键关联：将选项与题目绑定
                choice.setQuestionId(question.getId());

                // 【步骤 4.1】保存选项到数据库
                // insert()：MyBatis-Plus 的插入方法
                questionChoiceMapper.insert(choice);

                // 【步骤 4.2】拼接正确答案字符串
                // 判断当前选项是否是正确选项（isCorrect=true）
                if (choice.getIsCorrect()) {
                    // 关键逻辑详解：
                    // ┌──────────────────────────────────────┐
                    // │ 问题：为什么需要 if (sb.length() > 0) 判断？ │
                    // ├──────────────────────────────────────┤
                    // │ 场景 1：第一个正确选项                    │
                    // │ - sb.length() = 0                    │
                    // │ - 不添加逗号                           │
                    // │ - 直接追加字母（如"A"）                  │
                    // │ - 结果："A"                           │
                    // │                                      │
                    // │ 场景 2：第二个正确选项                │
                    // │ - sb.length() > 0（已有"A"）          │
                    // │ - 先添加逗号（","）                   │
                    // │ - 再追加字母（如"B"）                 │
                    // │ - 结果："A,B"                        │
                    // │                                      │
                    // │ 场景 3：第三个正确选项                │
                    // │ - sb.length() > 0（已有"A,B"）        │
                    // │ - 先添加逗号（","）                   │
                    // │ - 再追加字母（如"D"）                 │
                    // │ - 结果："A,B,D"                      │
                    // └──────────────────────────────────────┘

                    // 如果 StringBuilder 已经有内容了（不是第一个正确选项）
                    if (sb.length() > 0) {
                        // 添加逗号分隔符
                        // 作用：分隔多个正确答案，如"A,B,D"
                        sb.append(",");
                    }

                    // 【步骤 4.3】将选项序号转换为字母
                    // 算法原理：
                    // - 字符 'A' 的 ASCII 码值是 65
                    // - 字符 'B' 的 ASCII 码值是 66
                    // - 字符 'C' 的 ASCII 码值是 67
                    // - ...
                    // 
                    // 计算过程示例：
                    // i=0: (char)('A' + 0) = (char)(65 + 0) = (char)65 = 'A'
                    // i=1: (char)('A' + 1) = (char)(65 + 1) = (char)66 = 'B'
                    // i=2: (char)('A' + 2) = (char)(65 + 2) = (char)67 = 'C'
                    // i=3: (char)('A' + 3) = (char)(65 + 3) = (char)68 = 'D'
                    // 
                    // 类型转换说明：
                    // - 'A' 是 char 类型，参与运算时会自动提升为 int
                    // - 'A' + i 的结果是 int 类型
                    // - (char) 是强制类型转换，将 int 转回 char
                    sb.append((char) ('A' + i));
                }
            }

            // 【步骤 4.4】将拼接好的答案字符串设置到答案对象
            // 示例：
            // - 单选题：sb.toString() = "A"
            // - 多选题：sb.toString() = "A,B,D"
            answer.setAnswer(sb.toString());
        }

        // 【步骤 5】保存答案信息
        // 将答案对象插入到 question_answer 表
        questionAnswerMapper.insert(answer);
    }


    /**
     * 更新题目信息
     * 包含题目标题唯一性校验、题目信息更新、选择题选项与答案同步维护
     *
     * @param question 待更新的题目对象，包含题目基本信息、选项列表和答案对象
     */
    @Override
    public void updateQuestion(Question question) {
        // 1. 校验标题唯一性：排除当前题目自身，查询同标题的其他题目数量
        // 构建查询条件：题目标题与传入对象一致 且 题目ID不等于当前对象ID
        LambdaQueryWrapper<Question> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Question::getTitle, question.getTitle());
        queryWrapper.ne(Question::getId, question.getId());
        // 执行查询，获取符合条件的题目总数
        long count = count(queryWrapper);
        // 若存在同标题题目，抛出运行时异常终止更新流程
        if (count > 0) {
            throw new BusinessException(ErrorCode.QUESTION_TITLE_DUPLICATE,
                    "题目标题《" + question.getTitle() + "》已被其他题目使用");
        }
        // 2. 更新题目基础信息
        // 调用MyBatis-Plus提供的方法，根据题目ID更新题目表中的字段
        updateById(question);
        // 3. 获取关联的答案对象
        // 从题目对象中获取对应的答案实体，用于后续答案内容的更新操作
        QuestionAnswer answer = question.getAnswer();
        // 4. 处理选择题场景：同步更新选项和答案内容
        if ("CHOICE".equals(question.getType())) {
            // 从题目对象中获取所有选项集合
            List<QuestionChoice> questionChoices = question.getChoices();
            // 4.1 删除原有选项：根据题目ID删除数据库中已存在的所有选项记录
            // 构建删除条件：选项表中question_id等于当前题目ID
            questionChoiceMapper.delete(new LambdaQueryWrapper<QuestionChoice>()
                    .eq(QuestionChoice::getQuestionId, question.getId()));
            // 4.2 拼接正确答案标识：遍历选项，将正确选项的字母（A/B/C...）拼接成字符串
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < questionChoices.size(); i++) {
                QuestionChoice choice = questionChoices.get(i);
                // 重置选项ID和时间戳：避免主键冲突，让数据库自动生成新主键
                choice.setId(null);
                choice.setCreateTime(null);
                choice.setUpdateTime(null);
                // 设置选项排序号：按遍历顺序从0开始递增
                choice.setSort(i);
                // 绑定选项与当前题目：将选项的外键设置为当前题目ID
                choice.setQuestionId(question.getId());
                // 插入新选项到数据库：执行INSERT语句保存选项记录
                questionChoiceMapper.insert(choice);
                // 判断当前选项是否为正确选项
                if (choice.getIsCorrect()) {
                    // 若拼接字符串已有内容，先添加逗号分隔符
                    if (sb.length() > 0) {
                        sb.append(",");
                    }
                    // 将选项序号转换为对应字母（0→A，1→B...）并拼接到答案字符串
                    sb.append((char) ('A' + i));
                }
            }
            // 4.3 将拼接好的正确答案字符串设置到答案对象中
            answer.setAnswer(sb.toString());
        }
        // 5. 更新答案信息
        // 根据答案ID更新答案表中的记录，同步题目答案内容
        questionAnswerMapper.updateById(answer);
    }


    /**
     * 删除题目信息
     *
     * @param id 要删除的题目ID
     */
// 事务注解：所有异常都会触发事务回滚，保证数据一致性
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void removeQuestion(Long id) {
        // 1. 检查是否有试卷正在引用该题目，如果有则直接抛出异常，阻止删除
        // 构建查询条件：查询PaperQuestion表中questionId等于当前id的记录
        LambdaQueryWrapper<PaperQuestion> queryWrapper = new LambdaQueryWrapper<>();
        // 设置查询条件：PaperQuestion的questionId字段等于传入的id
        queryWrapper.eq(PaperQuestion::getQuestionId, id);
        // 执行查询，获取该题目被试卷引用的总次数
        Long count = paperQuestionMapper.selectCount(queryWrapper);
        // 如果引用次数大于0，说明该题目正在被使用，抛出异常终止删除操作
        if (count > 0) {
            throw new BusinessException(ErrorCode.QUESTION_REFERENCED,
                    "id=" + id + " 的题目被 " + count + " 个试卷引用，无法删除");
        }

        // 2. 执行题目本身的删除操作（基于MyBatis-Plus的通用方法，根据主键ID删除）
        removeById(id);

        // 3. 删除该题目关联的子数据：选项和答案，保证数据完整性，避免脏数据
        // 删除QuestionChoice表中所有关联当前题目ID的选项记录
        questionChoiceMapper.delete(new LambdaQueryWrapper<QuestionChoice>().eq(QuestionChoice::getQuestionId, id));
        // 删除QuestionAnswer表中所有关联当前题目ID的答案记录
        questionAnswerMapper.delete(new LambdaQueryWrapper<QuestionAnswer>().eq(QuestionAnswer::getQuestionId, id));

        // 4. 事务注解已保证操作原子性：要么全部成功，要么全部回滚，无需额外处理
    }


    /**
     * 根据文件生成预览数据集合
     *
     * @param file 上传的Excel文件
     * @return 解析后的题目预览数据列表
     * @throws IOException 若文件读取失败则抛出异常
     */
    @Override // 重写接口/父类中的方法
    public List<QuestionImportVo> previewExcel(MultipartFile file) throws IOException {
        // ====================== 1. 文件校验阶段 ======================
        // 校验 1：判断文件是否为空（无实际内容）
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.EXCEL_FILE_EMPTY);
        }

        // 获取上传文件的原始文件名（包含后缀）
        String filename = file.getOriginalFilename();
        // 校验 2：判断文件名是否为 null 以及文件格式是否为 .xls 或 .xlsx
        if (filename == null || (!filename.endsWith(".xls") && !filename.endsWith(".xlsx"))) {
            throw new BusinessException(ErrorCode.EXCEL_FORMAT_ERROR);
        }

        // ====================== 2. Excel 解析阶段 ======================
        List<QuestionImportVo> questionImportVoList = ExcelUtil.parseExcel(file);

        return questionImportVoList;
    }

    /**
     * 批量导入题目（服务降级实现：单题失败不影响整体导入）
     *
     * @param questions 题目导入DTO列表（来自Excel预览或AI生成）
     * @return 导入结果统计字符串
     */
    @Override
    public String importQuestions(List<QuestionImportVo> questions) {
        // 1. 传入数据非空判断：若列表为空，直接返回提示
        if (ObjectUtils.isEmpty(questions)) {
            return "批量导入结束，本次没有题目导入！传递的数据为空！";
        }
        // 2. 定义服务降级的计数器：记录成功导入的题目数量
        int successNumber = 0;

        // 3. 遍历每一道题目，逐个处理并入库
        for (QuestionImportVo questionImportVo : questions) {
            try {
                // --------------------------
                // 3.1 VO -> 题目实体类（Question）属性拷贝
                // --------------------------
                Question question = new Question();
                // 批量拷贝同名属性：源对象questionImportVo → 目标对象question
                BeanUtils.copyProperties(questionImportVo, question);
                // --------------------------
                // 3.2 处理选择题类型：VO选项 → 题目选项实体类（QuestionChoice）
                // --------------------------
                if ("CHOICE".equals(question.getType())) {
                    // 初始化选项列表，容量与传入选项数量一致
                    List<QuestionChoice> questionChoices = new ArrayList<>(questionImportVo.getChoices().size());
                    // 遍历VO中的选项DTO，逐个转换为数据库实体类
                    for (QuestionImportVo.ChoiceImportDto importVoChoice : questionImportVo.getChoices()) {
                        QuestionChoice questionChoice = new QuestionChoice();
                        // 拷贝选项内容、是否正确、排序号
                        questionChoice.setContent(importVoChoice.getContent());
                        questionChoice.setIsCorrect(importVoChoice.getIsCorrect());
                        questionChoice.setSort(importVoChoice.getSort());
                        // 添加到选项列表
                        questionChoices.add(questionChoice);
                    }
                    // 将选项列表关联到题目对象
                    question.setChoices(questionChoices);
                }
                // --------------------------
                // 3.3 处理答案和关键词：VO → 题目答案实体类（QuestionAnswer）
                // --------------------------
                QuestionAnswer questionAnswer = new QuestionAnswer();
                String answerStr = questionImportVo.getAnswer();
                if ("JUDGE".equals(question.getType())) {
                    questionAnswer.setAnswer(answerStr != null ? answerStr.toUpperCase() : "");
                } else {
                    questionAnswer.setAnswer(answerStr != null ? answerStr : "");
                }
                questionAnswer.setKeywords(questionImportVo.getKeywords());
                question.setAnswer(questionAnswer);
                // --------------------------
                // 3.4 调用保存业务：将完整题目数据（含选项、答案）入库
                // --------------------------
                saveQuestion(question);
                // 成功计数+1
                successNumber++;
            } catch (Exception e) {
                // 服务降级：单题保存失败时，仅打印错误日志，不中断整个批量流程
                log.error("保存【{}】题目的时候失败了！", questionImportVo.getTitle());
            }
        }
        // 4. 拼接结果反馈：统计成功导入数量和总数据量
        String result = String.format("题目批量导入接口调用结束，共计导入 %s 条，数据一共 %s 条！", successNumber, questions.size());
        return result;
    }


    /**
     * AI批量生成题目（预览接口，数据不入库）
     *
     * 容错解析机制：
     * 1. 多层回退提取 JSON（markdown代码块 → 纯花括号）
     * 2. 截断 JSON 恢复（AI 达到 max_tokens 时输出被截断的场景）
     * 3. JSON Schema 校验（字段类型/必填/枚举值约束）
     * 4. 单题分值边界校验
     *
     * @param request AI生成请求参数（包含主题、数量、难度、题型等配置）
     * @return 生成的题目列表（可直接用于后续导入入库）
     */
    @Override
    public List<QuestionImportVo> aiGenerateQuestions(AiGenerateRequestVo request) {

        // 1. 构建 AI 提示词（Prompt）
        String prompt = kimiAiService.buildPrompt(request);
        log.debug("ai 出题的条件为：{}，生成对应的提示词为：{}", request, prompt);

        // 2. 调用 Kimi AI 模型获取结果（内部已含 3 次重试）
        String response;
        try {
            log.info("开始调用 Kimi API，主题={}, 数量={}", request.getTopic(), request.getCount());
            response = kimiAiService.callKimiAI(prompt);
            log.info("Kimi API 调用成功，返回内容长度={}", response != null ? response.length() : 0);
        } catch (BusinessException e) {
            // BusinessException 直接向上抛
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.AI_GENERATE_FAILED, "AI生成题目过程中被中断");
        } catch (Exception e) {
            // 兜底：捕获所有未预期的异常，记录完整类型和堆栈
            log.error("调用 Kimi API 时发生未预期的异常，类型={}, 消息={}", e.getClass().getName(), e.getMessage(), e);
            throw new BusinessException(ErrorCode.AI_GENERATE_FAILED,
                    "AI调用异常: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }

        // 3. 多层回退提取 JSON 字符串
        String resultJson = extractJsonFromAiResponse(response);
        if (resultJson == null) {
            log.error("AI 返回结果无法提取 JSON，原始响应前200字符: {}",
                    response != null ? response.substring(0, Math.min(200, response.length())) : "null");
            throw new BusinessException(ErrorCode.AI_RESPONSE_FORMAT_ERROR);
        }

        // 4. 解析 JSON 为题目列表
        JSONObject jsonObject;
        try {
            jsonObject = JSONObject.parseObject(resultJson);
        } catch (Exception e) {
            log.error("AI 返回的 JSON 解析失败: {}，原始JSON前200字符: {}",
                    e.getMessage(), resultJson.substring(0, Math.min(200, resultJson.length())));
            throw new BusinessException(ErrorCode.AI_RESPONSE_FORMAT_ERROR);
        }

        // 4.1 JSON Schema 二次校验 —— 拦截字段类型/必填/枚举值错误
        String schemaError = JsonSchemaValidator.validateAndDescribe(resultJson);
        if (schemaError != null) {
            log.warn("AI 返回 JSON 未通过 Schema 校验: {}，尝试继续解析（容错）", schemaError);
            // 不中断流程，Schema 校验作为监控手段记录偏离情况
        }

        // 5. 提取并转换 questions 数组
        JSONArray questions = jsonObject.getJSONArray("questions");
        if (questions == null || questions.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_EMPTY);
        }

        List<QuestionImportVo> questionImportVoList = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            try {
                QuestionImportVo vo = parseQuestionItem(questions.getJSONObject(i), request);
                if (vo != null) {
                    questionImportVoList.add(vo);
                }
            } catch (Exception e) {
                // 单题解析失败不中断整体，记录日志继续处理剩余题目
                log.error("解析第{}道AI生成题目失败: {}", i + 1, e.getMessage());
            }
        }

        if (ObjectUtils.isEmpty(questionImportVoList)) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_EMPTY);
        }

        log.info("AI 出题完成: 生成 {} 道题目（原始题目数 {}）",
                questionImportVoList.size(), questions.size());
        return questionImportVoList;
    }

    /**
     * 从 AI 返回的原始文本中提取 JSON 字符串
     *
     * 容错策略（三层回退）：
     * 1. 提取 ```json ... ``` 代码块
     * 2. 提取 ``` ... ``` 普通代码块
     * 3. 提取第一个 { 到最后一个 } 之间的内容
     * 4. 截断恢复：如果 AI 输出被 max_tokens 截断导致 JSON 不完整，
     *    尝试补全缺失的括号后解析
     */
    private String extractJsonFromAiResponse(String content) {
        if (content == null || content.trim().isEmpty()) {
            return null;
        }

        // 第1层：```json ... ```
        int jsonStart = content.indexOf("```json");
        if (jsonStart != -1) {
            int contentStart = content.indexOf('\n', jsonStart);
            if (contentStart == -1) {
                contentStart = jsonStart + "```json".length();
            }
            int jsonEnd = content.indexOf("```", contentStart);
            if (jsonEnd != -1) {
                return content.substring(contentStart, jsonEnd).trim();
            }
            // ```json 存在但缺少结束标记 → 可能是 max_tokens 截断
            // 尝试提取从 contentStart 到字符串末尾的内容，并进行截断恢复
            String truncated = content.substring(contentStart).trim();
            log.warn("AI 返回可能被截断（缺少结束```标记），尝试恢复截断的 JSON");
            return recoverTruncatedJson(truncated);
        }

        // 第2层：``` ... ``` 普通代码块
        int codeStart = content.indexOf("```");
        if (codeStart != -1) {
            int contentStart = content.indexOf('\n', codeStart);
            if (contentStart == -1) {
                contentStart = codeStart + 3;
            }
            int codeEnd = content.indexOf("```", contentStart);
            if (codeEnd != -1) {
                return content.substring(contentStart, codeEnd).trim();
            }
            String truncated = content.substring(contentStart).trim();
            log.warn("AI 返回可能被截断（普通代码块缺少结束标记），尝试恢复");
            return recoverTruncatedJson(truncated);
        }

        // 第3层：直接提取花括号包裹的 JSON
        int braceStart = content.indexOf('{');
        int braceEnd = content.lastIndexOf('}');
        if (braceStart != -1 && braceEnd != -1 && braceEnd > braceStart) {
            return content.substring(braceStart, braceEnd + 1).trim();
        }

        return null;
    }

    /**
     * 截断 JSON 恢复 —— AI 达到 max_tokens 限制时输出不完整的 JSON，
     * 尝试补全缺失的括号/引号，尽量挽救已生成的题目数据
     *
     * 策略：从截断点向前查找，补全缺失的 ] } " 等闭合符号，
     * 利用 FastJSON 的宽松解析能力做最终校验
     */
    private String recoverTruncatedJson(String truncated) {
        if (truncated == null || truncated.trim().isEmpty()) {
            return null;
        }
        // 统计未闭合的括号
        int braceCount = 0;   // { }
        int bracketCount = 0; // [ ]
        boolean inString = false;
        for (int i = 0; i < truncated.length(); i++) {
            char c = truncated.charAt(i);
            if (c == '"' && (i == 0 || truncated.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
            if (!inString) {
                if (c == '{') braceCount++;
                else if (c == '}') braceCount--;
                else if (c == '[') bracketCount++;
                else if (c == ']') bracketCount--;
            }
        }
        // 如果正在字符串中间，先补一个引号
        StringBuilder recovered = new StringBuilder(truncated);
        if (inString) {
            recovered.append('"');
        }
        // 逆序补全括号
        for (int i = 0; i < bracketCount; i++) {
            recovered.append(']');
        }
        for (int i = 0; i < braceCount; i++) {
            recovered.append('}');
        }

        String recoveredStr = recovered.toString();
        // 用 FastJSON 验证补全后的 JSON 是否可解析
        try {
            JSONObject.parseObject(recoveredStr);
            log.info("截断 JSON 恢复成功，补全了 {} 个 ] 和 {} 个 }}", bracketCount, braceCount);
            return recoveredStr;
        } catch (Exception e) {
            log.warn("截断 JSON 恢复失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析单道 AI 生成的题目为 QuestionImportVo
     * 包含分值边界校验 —— AI 可能返回异常大的分值（如 10000）
     */
    private QuestionImportVo parseQuestionItem(JSONObject item, AiGenerateRequestVo request) {
        QuestionImportVo vo = new QuestionImportVo();

        // 基础字段
        vo.setTitle(item.getString("title"));
        vo.setType(item.getString("type"));
        vo.setMulti(item.getBoolean("multi"));
        vo.setCategoryId(request.getCategoryId());
        vo.setDifficulty(item.getString("difficulty"));

        // 分值边界校验：限制在 1~100 之间
        Integer rawScore = item.getInteger("score");
        if (rawScore == null || rawScore < 1) {
            vo.setScore(5); // 默认 5 分
            log.warn("AI 生成题目分值异常（{}）, 使用默认值 5", rawScore);
        } else if (rawScore > 100) {
            vo.setScore(100);
            log.warn("AI 生成题目分值过大（{}）, 限制为 100", rawScore);
        } else {
            vo.setScore(rawScore);
        }

        vo.setAnalysis(item.getString("analysis"));
        vo.setAnswer(item.getString("answer"));

        // 选择题：解析选项列表
        if ("CHOICE".equals(vo.getType())) {
            JSONArray choices = item.getJSONArray("choices");
            if (choices != null && !choices.isEmpty()) {
                List<QuestionImportVo.ChoiceImportDto> choiceList = new ArrayList<>();
                for (int j = 0; j < choices.size(); j++) {
                    JSONObject choiceObj = choices.getJSONObject(j);
                    QuestionImportVo.ChoiceImportDto dto = new QuestionImportVo.ChoiceImportDto();
                    dto.setContent(choiceObj.getString("content"));
                    dto.setIsCorrect(choiceObj.getBoolean("isCorrect"));
                    dto.setSort(choiceObj.getInteger("sort"));
                    choiceList.add(dto);
                }
                vo.setChoices(choiceList);
            }
        }

        return vo;
    }
}