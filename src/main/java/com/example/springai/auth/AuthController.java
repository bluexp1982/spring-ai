package com.example.springai.auth;

import com.example.springai.common.Result;
import com.example.springai.biz.entity.SysUser;
import com.example.springai.biz.service.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "认证管理", description = "后台管理员登录认证")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysUserService sysUserService;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Operation(summary = "管理员登录", description = "使用用户名和密码登录，返回 JWT token")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        SysUser user = sysUserService.findByUsername(request.getUsername());

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return Result.failed(401, "用户名或密码错误");
        }

        if (!user.getIsActive()) {
            return Result.failed(403, "账户已被禁用");
        }

        String token = jwtTokenProvider.generateToken(user.getUsername());
        LoginResponse response = new LoginResponse(
                token,
                "Bearer",
                86400000L,
                user.getUsername(),
                user.getDisplayName()
        );

        return Result.success(response);
    }
}