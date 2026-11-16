package com.example.springai.config;

import com.example.springai.biz.entity.SysUser;
import com.example.springai.biz.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final SysUserService sysUserService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        SysUser existing = sysUserService.findByUsername("admin");
        if (existing == null) {
            SysUser admin = new SysUser();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setDisplayName("系统管理员");
            admin.setIsActive(true);
            sysUserService.create(admin);
            log.info("初始管理员用户已创建: admin / admin123");
        }
    }
}