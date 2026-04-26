package com.onerag.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.onerag.user.dto.CommonResponse;
import com.onerag.user.dto.PageResp;
import com.onerag.user.dto.UserCreateReq;
import com.onerag.user.dto.UserQueryReq;
import com.onerag.user.dto.UserResp;
import com.onerag.user.dto.UserUpdateReq;
import com.onerag.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理接口（管理员）。
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public CommonResponse<PageResp<UserResp>> page(UserQueryReq req) {
        requireAdmin();
        return CommonResponse.ok(userService.pageUsers(req));
    }

    @GetMapping("/{userId}")
    public CommonResponse<UserResp> detail(@PathVariable String userId) {
        requireAdmin();
        return CommonResponse.ok(userService.getByUserId(userId));
    }

    @PostMapping
    public CommonResponse<UserResp> create(@RequestBody @Valid UserCreateReq req) {
        requireAdmin();
        return CommonResponse.ok(userService.createUser(req));
    }

    @PutMapping("/{userId}")
    public CommonResponse<UserResp> update(@PathVariable String userId, @RequestBody UserUpdateReq req) {
        requireAdmin();
        return CommonResponse.ok(userService.updateUser(userId, req));
    }

    @DeleteMapping("/{userId}")
    public CommonResponse<Void> delete(@PathVariable String userId) {
        requireAdmin();
        userService.deleteUser(userId);
        return CommonResponse.ok(null);
    }

    private void requireAdmin() {
        StpUtil.checkLogin();
        String role = String.valueOf(StpUtil.getTokenSession().get("role"));
        if (!"admin".equalsIgnoreCase(role)) {
            throw new IllegalArgumentException("无权限访问");
        }
    }
}
