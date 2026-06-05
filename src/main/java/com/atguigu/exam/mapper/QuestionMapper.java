package com.atguigu.exam.mapper;


import com.atguigu.exam.entity.Question;
import com.atguigu.exam.vo.QuestionQueryVo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 题目Mapper接口
 * 继承MyBatis Plus的BaseMapper，提供基础的CRUD操作
 */
@Mapper
public interface QuestionMapper extends BaseMapper<Question> {


    /**
     * 获取每个分类的题目数量
     @return
     包含分类ID和题目数量的结果列表
     */
    @Select("SELECT category_id, COUNT(*) as count FROM questions where is_deleted = 0  GROUP BY category_id ; "
    )
    List<Map<Long, Object>> getCategoryQuestionCount();

    List<Question> customPage(Page<Question> pageBean, QuestionQueryVo questionQueryVo);

    /**
     * ???????ID???id????????
     * ??????????????ID
     * 
     * ?????id?
     * - ????????ID???????
     * - SELECT id ? SELECT * ??????????
     * @return ????ID???
     */
    List<Long> selectAllIds();
}
