package com.atguigu.exam.entity;

/**
 * 考试记录状态枚举
 * <p>
 * 注意：数据库当前存储中文值（进行中/已完成/已批阅），
 * 此枚举统一管理状态常量，避免在代码中硬编码中文。
 * 后续数据库迁移后，可删除 getDbValue() 方法直接使用 name()。
 */
public enum ExamStatus {

    /** 考试进行中 */
    IN_PROGRESS("进行中"),
    /** 考试已完成，待批阅 */
    COMPLETED("已完成"),
    /** 已批阅完成 */
    GRADED("已批阅");

    private final String dbValue;

    ExamStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    /**
     * 获取数据库存储的值（兼容现存的字段值）
     */
    public String getDbValue() {
        return dbValue;
    }

    /**
     * 根据数据库值反查枚举
     */
    public static ExamStatus fromDbValue(String dbValue) {
        for (ExamStatus status : values()) {
            if (status.dbValue.equals(dbValue)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的考试状态: " + dbValue);
    }
}
