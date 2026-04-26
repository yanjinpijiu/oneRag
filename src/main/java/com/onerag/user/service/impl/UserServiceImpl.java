package com.onerag.user.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.onerag.user.dao.entity.UserDO;
import com.onerag.user.dao.mapper.UserMapper;
import com.onerag.user.dto.AuthLoginReq;
import com.onerag.user.dto.AuthLoginResp;
import com.onerag.user.dto.AuthRegisterReq;
import com.onerag.user.dto.ChangePasswordReq;
import com.onerag.user.dto.PageResp;
import com.onerag.user.dto.ProfileUpdateReq;
import com.onerag.user.dto.UserCreateReq;
import com.onerag.user.dto.UserQueryReq;
import com.onerag.user.dto.UserResp;
import com.onerag.user.dto.UserUpdateReq;
import com.onerag.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    @Override
    public UserResp register(AuthRegisterReq req) {
        checkUsernameNotExists(req.getUsername(), null);
        checkEmailPhoneUnique(req.getEmail(), req.getPhone(), null);

        UserDO user = UserDO.builder()
                .userId(UUID.randomUUID().toString())
                .username(req.getUsername().trim())
                .passwordHash(hash(req.getPassword()))
                .nickname(req.getNickname())
                .email(emptyToNull(req.getEmail()))
                .phone(emptyToNull(req.getPhone()))
                .status(1)
                .role("user")
                .build();
        userMapper.insert(user);
        return toResp(user);
    }

    @Override
    public AuthLoginResp login(AuthLoginReq req) {
        UserDO user = getByUsername(req.getUsername());
        if (user == null || !user.getPasswordHash().equals(hash(req.getPassword()))) {
            throw new IllegalArgumentException("账号或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new IllegalArgumentException("用户已禁用");
        }
        user.setLastLoginTime(new Date());
        userMapper.updateById(user);

        StpUtil.login(user.getUserId());
        StpUtil.getTokenSession().set("role", user.getRole());

        return AuthLoginResp.builder()
                .token(StpUtil.getTokenValue())
                .userId(user.getUserId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .role(user.getRole())
                .build();
    }

    @Override
    public void logout() {
        StpUtil.checkLogin();
        StpUtil.logout();
    }

    @Override
    public UserResp currentUser() {
        return toResp(getCurrentUserEntity());
    }

    @Override
    public PageResp<UserResp> pageUsers(UserQueryReq req) {
        Page<UserDO> page = new Page<>(Math.max(req.getPageNo(), 1), Math.max(req.getPageSize(), 1));
        LambdaQueryWrapper<UserDO> wrapper = new LambdaQueryWrapper<>();
        if (req.getKeyword() != null && !req.getKeyword().isBlank()) {
            wrapper.and(w -> w.like(UserDO::getUsername, req.getKeyword())
                    .or().like(UserDO::getNickname, req.getKeyword())
                    .or().like(UserDO::getEmail, req.getKeyword())
                    .or().like(UserDO::getPhone, req.getKeyword()));
        }
        if (req.getStatus() != null) {
            wrapper.eq(UserDO::getStatus, req.getStatus());
        }
        if (req.getRole() != null && !req.getRole().isBlank()) {
            wrapper.eq(UserDO::getRole, req.getRole());
        }
        wrapper.orderByDesc(UserDO::getCreateTime);

        Page<UserDO> result = userMapper.selectPage(page, wrapper);
        List<UserResp> records = result.getRecords().stream().map(this::toResp).collect(Collectors.toList());
        return PageResp.<UserResp>builder()
                .pageNo(result.getCurrent())
                .pageSize(result.getSize())
                .total(result.getTotal())
                .records(records)
                .build();
    }

    @Override
    public UserResp getByUserId(String userId) {
        UserDO user = getByUserIdInternal(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        return toResp(user);
    }

    @Override
    public UserResp createUser(UserCreateReq req) {
        checkUsernameNotExists(req.getUsername(), null);
        checkEmailPhoneUnique(req.getEmail(), req.getPhone(), null);
        UserDO user = UserDO.builder()
                .userId(UUID.randomUUID().toString())
                .username(req.getUsername().trim())
                .passwordHash(hash(req.getPassword()))
                .nickname(req.getNickname())
                .email(emptyToNull(req.getEmail()))
                .phone(emptyToNull(req.getPhone()))
                .avatar(emptyToNull(req.getAvatar()))
                .status(req.getStatus() == null ? 1 : req.getStatus())
                .role(req.getRole() == null || req.getRole().isBlank() ? "user" : req.getRole())
                .build();
        userMapper.insert(user);
        return toResp(user);
    }

    @Override
    public UserResp updateUser(String userId, UserUpdateReq req) {
        UserDO user = getByUserIdInternal(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        checkEmailPhoneUnique(req.getEmail(), req.getPhone(), userId);
        if (req.getNickname() != null) {
            user.setNickname(req.getNickname());
        }
        if (req.getEmail() != null) {
            user.setEmail(emptyToNull(req.getEmail()));
        }
        if (req.getPhone() != null) {
            user.setPhone(emptyToNull(req.getPhone()));
        }
        if (req.getAvatar() != null) {
            user.setAvatar(emptyToNull(req.getAvatar()));
        }
        if (req.getStatus() != null) {
            user.setStatus(req.getStatus());
        }
        if (req.getRole() != null && !req.getRole().isBlank()) {
            user.setRole(req.getRole());
        }
        userMapper.updateById(user);
        return toResp(user);
    }

    @Override
    public void deleteUser(String userId) {
        UserDO user = getByUserIdInternal(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        userMapper.deleteById(user.getId());
    }

    @Override
    public UserResp updateProfile(ProfileUpdateReq req) {
        UserDO user = getCurrentUserEntity();
        checkEmailPhoneUnique(req.getEmail(), req.getPhone(), user.getUserId());
        if (req.getNickname() != null) {
            user.setNickname(req.getNickname());
        }
        if (req.getEmail() != null) {
            user.setEmail(emptyToNull(req.getEmail()));
        }
        if (req.getPhone() != null) {
            user.setPhone(emptyToNull(req.getPhone()));
        }
        if (req.getAvatar() != null) {
            user.setAvatar(emptyToNull(req.getAvatar()));
        }
        userMapper.updateById(user);
        return toResp(user);
    }

    @Override
    public void changePassword(ChangePasswordReq req) {
        UserDO user = getCurrentUserEntity();
        if (!user.getPasswordHash().equals(hash(req.getOldPassword()))) {
            throw new IllegalArgumentException("旧密码错误");
        }
        user.setPasswordHash(hash(req.getNewPassword()));
        userMapper.updateById(user);
    }

    @Override
    public UserDO getCurrentUserEntity() {
        StpUtil.checkLogin();
        String userId = String.valueOf(StpUtil.getLoginId());
        UserDO user = getByUserIdInternal(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        return user;
    }

    private UserDO getByUsername(String username) {
        LambdaQueryWrapper<UserDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDO::getUsername, username);
        return userMapper.selectOne(wrapper);
    }

    private UserDO getByUserIdInternal(String userId) {
        LambdaQueryWrapper<UserDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDO::getUserId, userId);
        return userMapper.selectOne(wrapper);
    }

    private void checkUsernameNotExists(String username, String skipUserId) {
        LambdaQueryWrapper<UserDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDO::getUsername, username);
        if (skipUserId != null) {
            wrapper.ne(UserDO::getUserId, skipUserId);
        }
        if (userMapper.selectCount(wrapper) > 0) {
            throw new IllegalArgumentException("账号已存在");
        }
    }

    private void checkEmailPhoneUnique(String email, String phone, String skipUserId) {
        if (email != null && !email.isBlank()) {
            LambdaQueryWrapper<UserDO> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UserDO::getEmail, email);
            if (skipUserId != null) {
                wrapper.ne(UserDO::getUserId, skipUserId);
            }
            if (userMapper.selectCount(wrapper) > 0) {
                throw new IllegalArgumentException("邮箱已被使用");
            }
        }
        if (phone != null && !phone.isBlank()) {
            LambdaQueryWrapper<UserDO> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UserDO::getPhone, phone);
            if (skipUserId != null) {
                wrapper.ne(UserDO::getUserId, skipUserId);
            }
            if (userMapper.selectCount(wrapper) > 0) {
                throw new IllegalArgumentException("手机号已被使用");
            }
        }
    }

    private UserResp toResp(UserDO user) {
        return UserResp.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .phone(user.getPhone())
                .avatar(user.getAvatar())
                .status(user.getStatus())
                .role(user.getRole())
                .lastLoginTime(user.getLastLoginTime())
                .createTime(user.getCreateTime())
                .updateTime(user.getUpdateTime())
                .build();
    }

    private String hash(String raw) {
        return DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
