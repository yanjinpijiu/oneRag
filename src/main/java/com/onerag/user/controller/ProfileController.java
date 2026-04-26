package com.onerag.user.controller;

import com.onerag.user.dto.ChangePasswordReq;
import com.onerag.user.dto.CommonResponse;
import com.onerag.user.dto.ProfileUpdateReq;
import com.onerag.user.dto.UserResp;
import com.onerag.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人中心接口。
 */
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;

    @GetMapping
    public CommonResponse<UserResp> profile() {
        return CommonResponse.ok(userService.currentUser());
    }

    @PutMapping
    public CommonResponse<UserResp> updateProfile(@RequestBody ProfileUpdateReq req) {
        return CommonResponse.ok(userService.updateProfile(req));
    }

    @PutMapping("/password")
    public CommonResponse<Void> changePassword(@RequestBody @Valid ChangePasswordReq req) {
        userService.changePassword(req);
        return CommonResponse.ok(null);
    }
}
