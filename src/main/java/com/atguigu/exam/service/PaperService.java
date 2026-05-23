package com.atguigu.exam.service;

import com.atguigu.exam.entity.Paper;
import com.atguigu.exam.vo.PaperVo;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 试卷服务接口
 */
public interface PaperService extends IService<Paper> {

    /**
     * 创建试卷
     * @param paperVo 试卷信息
     * @return 创建的试卷
     */
    Paper createPaper(PaperVo paperVo);

    /**
     * 获取试卷详情（包含题目列表）
     * @param id 试卷ID
     * @return 试卷实体（含关联题目）
     */
    Paper getPaperWithQuestions(Integer id);

    /**
     * 更新试卷信息和题目配置
     * @param id 试卷ID
     * @param paperVo 更新数据
     */
    void updatePaper(Integer id, PaperVo paperVo);

    /**
     * 更新试卷状态
     * @param id 试卷ID
     * @param status 新状态
     */
    void updatePaperStatus(Integer id, String status);

    /**
     * 删除试卷（含关联题目记录）
     * @param id 试卷ID
     */
    void deletePaper(Integer id);

    /**
     * AI智能组卷：根据规则从题库随机抽题组卷
     * @param aiPaperVo 组卷参数（名称、规则等）
     * @return 创建的试卷
     */
    Paper createPaperWithAi(com.atguigu.exam.vo.AiPaperVo aiPaperVo);
}