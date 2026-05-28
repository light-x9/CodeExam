package com.atguigu.exam.service.impl;

import com.atguigu.exam.common.BusinessException;
import com.atguigu.exam.common.ErrorCode;
import com.atguigu.exam.config.properties.MinioProperties;
import com.atguigu.exam.service.FileUploadService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketPolicyArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 文件上传服务实现 — 基于 MinIO 对象存储
 */
@Service
@Slf4j
public class FileUploadServiceImpl implements FileUploadService {

    // ==================== 文件类型白名单 ====================

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "bmp"
    );

    private static final Set<String> VIDEO_EXTENSIONS = Set.of(
            "mp4", "avi", "mkv", "mov", "webm", "flv", "wmv"
    );

    /** 所有允许上传的扩展名 */
    private static final Set<String> ALLOWED_EXTENSIONS;

    static {
        Set<String> all = new HashSet<>();
        all.addAll(IMAGE_EXTENSIONS);
        all.addAll(VIDEO_EXTENSIONS);
        ALLOWED_EXTENSIONS = Collections.unmodifiableSet(all);
    }

    // ==================== 文件大小限制 ====================

    /** 图片最大 10MB */
    private static final long IMAGE_MAX_SIZE = 10 * 1024 * 1024L;

    /** 视频最大 500MB */
    private static final long VIDEO_MAX_SIZE = 500 * 1024 * 1024L;

    // ==================== 依赖注入 ====================

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private MinioProperties minioProperties;

    // ==================== 公开方法 ====================

    @Override
    public Map<String, Object> uploadFile(MultipartFile file, String subDir) {
        validateNotEmpty(file);
        String extension = extractExtension(file);
        validateExtension(extension);
        validateSize(file, extension);

        String objectName = generateObjectName(subDir, extension);

        try (InputStream inputStream = file.getInputStream()) {
            ensureBucketExists();

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(objectName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            String accessUrl = minioProperties.getAccessUrlPrefix() + objectName;

            log.info("文件上传成功: objectName={}, size={}B", objectName, file.getSize());

            Map<String, Object> result = new HashMap<>();
            result.put("url", accessUrl);
            result.put("objectName", objectName);
            result.put("size", file.getSize());
            return result;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("MinIO 上传失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "文件上传失败: " + e.getMessage());
        }
    }

    @Override
    public boolean deleteFile(String objectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(objectName)
                            .build()
            );
            log.info("文件删除成功: objectName={}", objectName);
            return true;
        } catch (Exception e) {
            log.error("MinIO 删除文件失败: objectName={}, error={}", objectName, e.getMessage());
            return false;
        }
    }

    // ==================== 私有校验方法 ====================

    private void validateNotEmpty(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }
    }

    private String extractExtension(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.contains(".")) {
            throw new BusinessException(ErrorCode.FILE_EXTENSION_NOT_ALLOWED, "无法识别文件类型");
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    private void validateExtension(String extension) {
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ErrorCode.FILE_EXTENSION_NOT_ALLOWED,
                    "不支持的文件类型: ." + extension + "，允许的类型: " + String.join(", ", ALLOWED_EXTENSIONS));
        }
    }

    private void validateSize(MultipartFile file, String extension) {
        long size = file.getSize();
        if (IMAGE_EXTENSIONS.contains(extension) && size > IMAGE_MAX_SIZE) {
            throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED,
                    "图片大小不能超过 " + (IMAGE_MAX_SIZE / 1024 / 1024) + "MB，当前文件大小: " + (size / 1024 / 1024) + "MB");
        }
        if (VIDEO_EXTENSIONS.contains(extension) && size > VIDEO_MAX_SIZE) {
            throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED,
                    "视频大小不能超过 " + (VIDEO_MAX_SIZE / 1024 / 1024) + "MB，当前文件大小: " + (size / 1024 / 1024) + "MB");
        }
    }

    // ==================== 私有工具方法 ====================

    /**
     * 生成 MinIO 对象名
     * 格式：{subDir}{yyyy/MM/dd}/{UUID}.{ext}
     */
    private String generateObjectName(String subDir, String extension) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String path = subDir;
        if (!path.endsWith("/")) {
            path += "/";
        }
        return path + datePath + "/" + uuid + "." + extension;
    }

    private boolean bucketPublicSet = false;

    /**
     * 确保存储桶存在，不存在则自动创建，并设置公开读权限
     */
    private void ensureBucketExists() {
        try {
            boolean exists = minioClient.bucketExists(
                    io.minio.BucketExistsArgs.builder().bucket(minioProperties.getBucket()).build()
            );
            if (!exists) {
                minioClient.makeBucket(
                        io.minio.MakeBucketArgs.builder().bucket(minioProperties.getBucket()).build()
                );
                log.info("自动创建 MinIO 存储桶: {}", minioProperties.getBucket());
            }

            // 首次调用时设置 bucket 为公开读，浏览器才能直接加载图片
            if (!bucketPublicSet) {
                String policyJson = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetObject\"],\"Resource\":[\"arn:aws:s3:::"
                        + minioProperties.getBucket() + "/*\"]}]}";
                minioClient.setBucketPolicy(
                        SetBucketPolicyArgs.builder()
                                .bucket(minioProperties.getBucket())
                                .config(policyJson)
                                .build()
                );
                bucketPublicSet = true;
                log.info("已设置存储桶公开读权限: {}", minioProperties.getBucket());
            }
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "MinIO 连接异常: " + e.getMessage());
        }
    }
}
