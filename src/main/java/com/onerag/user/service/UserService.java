package com.onerag.user.service;

import com.onerag.user.dao.entity.UserDO;
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

public interface UserService {
    UserResp register(AuthRegisterReq req);

    AuthLoginResp login(AuthLoginReq req);

    void logout();

    UserResp currentUser();

    PageResp<UserResp> pageUsers(UserQueryReq req);

    UserResp getByUserId(String userId);

    UserResp createUser(UserCreateReq req);

    UserResp updateUser(String userId, UserUpdateReq req);

    void deleteUser(String userId);

    UserResp updateProfile(ProfileUpdateReq req);

    void changePassword(ChangePasswordReq req);

    UserDO getCurrentUserEntity();
}
