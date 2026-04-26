package com.onerag.user.controller;

import com.onerag.user.dto.AuthLoginReq;
import com.onerag.user.dto.AuthLoginResp;
import com.onerag.user.dto.AuthRegisterReq;
import com.onerag.user.dto.CommonResponse;
import com.onerag.user.dto.UserResp;
import com.onerag.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public CommonResponse<UserResp> register(@RequestBody @Valid AuthRegisterReq req) {
        return CommonResponse.ok(userService.register(req));
    }

    @PostMapping("/login")
    public CommonResponse<AuthLoginResp> login(@RequestBody @Valid AuthLoginReq req) {
        return CommonResponse.ok(userService.login(req));
    }

    @PostMapping("/logout")
    public CommonResponse<Void> logout() {
        userService.logout();
        return CommonResponse.ok(null);
    }

    @GetMapping("/me")
    public CommonResponse<UserResp> me() {
        return CommonResponse.ok(userService.currentUser());
    }
}
