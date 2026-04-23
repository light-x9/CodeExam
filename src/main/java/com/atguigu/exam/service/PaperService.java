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
}