package com.atguigu.exam.controller;

import com.atguigu.exam.common.Result;
import com.atguigu.exam.entity.Paper;
import com.atguigu.exam.service.PaperQuestionService;
import com.atguigu.exam.service.PaperService;
import com.atguigu.exam.vo.AiPaperVo;
import com.atguigu.exam.vo.PaperVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 试卷控制器 - 处理试卷管理相关的HTTP请求
 * 包括试卷的CRUD操作、AI智能组卷、状态管理等功能
 */
@CrossOrigin
@Slf4j
@RestController  // REST控制器，返回JSON数据
@RequestMapping("/api/papers")  // 试卷API路径前缀
@Tag(name = "试卷管理", description = "试卷相关操作，包括创建、查询、更新、删除，以及AI智能组卷功能")  // Swagger API分组
public class PaperController {
    @Autowired
    private PaperService paperService;
    @Autowired
    private PaperQuestionService paperQuestionService;

    /**
     * 获取所有试卷列表接口
     * 功能：支持按试卷名称模糊搜索、按试卷状态精准筛选的列表查询，是后台管理系统最典型的条件查询接口
     * 请求方式：GET
     * 请求路径：/list
     *
     * @param name   试卷名称（非必传），支持模糊匹配，用于搜索包含指定关键词的试卷
     * @param status 试卷状态（非必传），可选值：DRAFT(草稿)/PUBLISHED(已发布)/STOPPED(已停用)
     *               用于精准筛选对应状态的试卷
     * @return 统一返回结果Result，封装查询到的试卷列表数据
     * @RequestParam(required = false) 表示该参数不是必须传递，前端不传则不执行该条件筛选
     */
    @GetMapping("/list")
    @Operation(summary = "获取试卷列表", description = "支持按名称模糊搜索和状态筛选的试卷列表查询")
    public Result<List<Paper>> listPapers(
            @Parameter(description = "试卷名称，支持模糊查询") @RequestParam(required = false) String name,
            @Parameter(description = "试卷状态，可选值：DRAFT/PUBLISHED/STOPPED") @RequestParam(required = false) String status
    ) {
        // --------------------------
        // 1. 构建MyBatis-Plus动态查询条件构造器
        // LambdaQueryWrapper：MyBatis-Plus提供的类型安全的查询构造器
        // 优势：通过Lambda引用实体类字段，避免硬编码数据库字段名，编译期就能校验字段合法性
        // --------------------------
        LambdaQueryWrapper<Paper> queryWrapper = new LambdaQueryWrapper<>();

        // --------------------------
        // 2. 动态拼接查询条件（核心：condition条件判断）
        // 设计思想：只有当前端传递了非空的参数时，才会把该条件加入SQL查询，实现动态SQL
        // 第一个参数 boolean condition：条件成立才会执行后面的查询规则，避免空参数导致的无效查询
        // --------------------------
        // 试卷名称模糊查询：name不为空时，拼接SQL的 like '%name%' 条件
        queryWrapper.like(!ObjectUtils.isEmpty(name), Paper::getName, name);
        // 试卷状态精准查询：status不为空时，拼接SQL的 status = 'xxx' 等值条件
        queryWrapper.eq(!ObjectUtils.isEmpty(status), Paper::getStatus, status);

        // --------------------------
        // 3. 调用业务层执行数据库查询
        // paperService.list()：MyBatis-Plus的IService接口提供的通用查询方法
        // 传入我们构建好的查询构造器，自动生成SQL并执行，返回符合条件的试卷实体列表
        // --------------------------
        List<Paper> paperList = paperService.list(queryWrapper);

        // --------------------------
        // 4. 记录接口调用日志
        // 作用：记录本次查询的返回结果，便于后续排查问题、审计接口调用情况
        // --------------------------
        log.info("查询试卷接口调用结束，查询数据为：{}", paperList);

        // --------------------------
        // 5. 封装统一结果返回给前端
        // Result.success()：项目全局统一的返回格式，封装状态码、成功标识和业务数据
        // 前端可以统一解析该格式，展示列表数据
        // --------------------------
        return Result.success(paperList);
    }


    /**
     * 手动创建试卷（手动组卷）接口
     * 功能：接收前端传递的试卷基础信息、手动选中的题目列表等参数，完成试卷创建与题目关联绑定
     * 请求方式：POST
     * 请求路径：/create
     *
     * @param paperVo 前端传递的创建试卷入参VO（Value Object）
     *                作用：封装创建试卷的所有业务参数（如试卷名称、考试时长、总分、手动选中的题目ID列表等）
     *                设计意义：和数据库实体Paper解耦，避免前端直接操作数据库映射实体，保证接口入参的安全性和灵活性
     * @return 统一返回结果Result，封装创建成功后的试卷完整实体数据，以及前端展示的成功提示
     * @RequestBody 表示参数从请求体中获取，格式为JSON
     */
    @CrossOrigin("*")
    @PostMapping
    @Operation(summary = "手动创建试卷", description = "通过手动选择题目、填写试卷基础信息的方式完成手动组卷，创建新试卷")
    public Result<Paper> createPaper(@RequestBody PaperVo paperVo) {

        // --------------------------
        // 1. 调用业务层，执行手动组卷的核心业务逻辑
        // Service层内部会完成的核心操作：
        // ① 入参合法性校验（试卷名称非空、题目列表非空、分值合法性等）
        // ② VO转数据库实体，保存试卷基础信息到paper试卷表
        // ③ 批量保存「试卷-题目」的关联关系到中间表（多对多关系）
        // ④ 事务控制：任意步骤失败都会触发数据回滚，保证试卷和题目关联数据的一致性
        // --------------------------
        Paper paper = paperService.createPaper(paperVo);

        // --------------------------
        // 2. 记录接口调用成功日志
        // 作用：记录本次创建的试卷完整信息，用于后续业务审计、问题排查，确认组卷结果
        // --------------------------
        log.info("手动组卷成功，创建的试卷对象信息为：{}", paper);

        // --------------------------
        // 3. 封装统一成功结果，返回给前端
        // Result.success()：项目全局统一的返回格式，传入两个参数：
        // 第一个参数：业务数据（创建完成的试卷实体，前端可用于跳转详情页）
        // 第二个参数：前端展示的成功提示文案
        // --------------------------
        return Result.success(paper, "试卷创建成功");
    }


    /**
     * 更新试卷
     *
     * @param id      试卷ID
     * @param paperVo 试卷更新数据
     * @return 操作结果
     */
    @PutMapping("/{id}")  // 处理PUT请求
    @Operation(summary = "更新试卷信息", description = "更新试卷的基本信息和题目配置")  // API描述
    public Result<Paper> updatePaper(
            @Parameter(description = "试卷ID") @PathVariable Integer id,
            @RequestBody PaperVo paperVo) {
        return Result.success(null, "试卷更新成功");
    }


    /**
     * AI智能组卷（新版）
     *
     * @param aiPaperVo 包含试卷信息和组卷规则的数据
     * @return 创建好的试卷
     */
    @PostMapping("/ai")  // 处理POST请求
    @Operation(summary = "AI智能组卷", description = "基于设定的规则（题型分布、难度配比等）使用AI自动生成试卷")  // API描述
    public Result<Paper> createPaperWithAI(@RequestBody AiPaperVo aiPaperVo) {
        return Result.success(null, "AI智能组卷成功");
    }

    /**
     * 获取试卷详情（包含题目）
     */
    @GetMapping("/{id}")  // 处理GET请求
    @Operation(summary = "获取试卷详情", description = "获取试卷的详细信息，包括试卷基本信息和包含的所有题目")  // API描述
    public Result<Paper> getPaperById(@Parameter(description = "试卷ID") @PathVariable Integer id) {
        return Result.success(null);
    }

    /**
     * 更新试卷状态（发布/停止）
     *
     * @param id     试卷ID
     * @param status 新的状态
     * @return 操作结果
     */
    @PostMapping("/{id}/status")  // 处理POST请求
    @Operation(summary = "更新试卷状态", description = "修改试卷状态：发布试卷供学生考试或停止试卷禁止考试")  // API描述
    public Result<Void> updatePaperStatus(
            @Parameter(description = "试卷ID") @PathVariable Integer id,
            @Parameter(description = "新的状态，可选值：PUBLISHED/STOPPED") @RequestParam String status) {
        return Result.success(null, "状态更新成功");
    }

    /**
     * 删除试卷
     *
     * @param id 试卷ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")  // 处理DELETE请求
    @Operation(summary = "删除试卷", description = "删除指定的试卷，注意：已发布的试卷不能删除")  // API描述
    public Result<Void> deletePaper(@Parameter(description = "试卷ID") @PathVariable Integer id) {
        // 检查试卷是否存在  // 验证试卷存在性

        return Result.error("试卷删除失败");
    }
} 