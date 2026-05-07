package com.atguigu.exam.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码工具类 — 用于生成 BCrypt 密文
 * 
 * 使用方法：直接右键 → Run 'PasswordUtil.main()'
 * 控制台会打印加密后的密文，复制到 SQL 里执行即可
 */
public class PasswordUtil {

    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

        String adminPwd = encoder.encode("admin123");
        String zhangsanPwd = encoder.encode("123456");

        System.out.println("========== BCrypt 密码密文（复制下面两行到 SQL 里执行）==========");
        System.out.println();
        System.out.println("-- admin 的密码 admin123 加密后");
        System.out.println("UPDATE users SET password = '" + adminPwd + "' WHERE username = 'admin';");
        System.out.println();
        System.out.println("-- zhangsan 的密码 123456 加密后");
        System.out.println("UPDATE users SET password = '" + zhangsanPwd + "' WHERE username = 'zhangsan';");
        System.out.println();
        System.out.println("================================================================");
    }
}
