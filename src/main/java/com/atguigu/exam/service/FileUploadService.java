package com.atguigu.exam.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 文件上传服务
 * 支持 MinIO 对象存储
 */
public interface FileUploadService {

    /**
     * 上传文件到 MinIO
     *
     * @param file   上传的文件
     * @param subDir 子目录路径（如 "banners/"、"videos/original/"、"videos/covers/"）
     * @return Map 包含 url（访问地址）、objectName（MinIO对象名）、size（文件字节数）
     */
    Map<String, Object> uploadFile(MultipartFile file, String subDir);

    /**
     * 删除 MinIO 中的文件
     *
     * @param objectName MinIO 对象名（uploadFile 返回的 objectName）
     * @return true 删除成功，false 失败
     */
    boolean deleteFile(String objectName);
}