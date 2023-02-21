package com.xkyii.spry.admin.service;

import cn.hutool.crypto.digest.DigestUtil;
import com.xkyii.spry.admin.constant.AdminError;
import com.xkyii.spry.admin.constant.UserStatus;
import com.xkyii.spry.admin.dto.auth.LoginUser;
import com.xkyii.spry.admin.dto.user.get_user_info.Converter;
import com.xkyii.spry.admin.dto.user.get_user_info.UserPermissionDto;
import com.xkyii.spry.admin.dto.user.login.LoginCommand;
import com.xkyii.spry.admin.dto.user.login.LoginDto;
import com.xkyii.spry.admin.dto.user.register.RegisterCommand;
import com.xkyii.spry.admin.dto.user.register.RegisterDto;
import com.xkyii.spry.admin.entity.SysUser;
import com.xkyii.spry.admin.filter.AuthedAugmentor;
import com.xkyii.spry.admin.manager.DictionaryManager;
import com.xkyii.spry.admin.manager.SecureManager;
import com.xkyii.spry.admin.manager.TokenManager;
import com.xkyii.spry.admin.repository.SysUserRepository;
import com.xkyii.spry.common.error.ApiException;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.hibernate.reactive.panache.common.runtime.ReactiveTransactional;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.unchecked.Unchecked;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.core.Context;

import java.util.Objects;

@ApplicationScoped
public class SysUserService {

    @Inject
    SysUserRepository userRepository;

    @Inject
    TokenManager tokenManager;

    @Inject
    SecureManager secureManager;

    @Inject
    DictionaryManager dictionaryManager;

    @Inject
    Converter converter;

    @Context
    SecurityIdentity securityIdentity;

    @ReactiveTransactional
    public Uni<RegisterDto> register(@Valid RegisterCommand input) {
        String username = input.getUsername();

        return userRepository.find("username", username).firstResult()
            // 如果查询到已有username的用户,报异常
            .onItem().ifNotNull().failWith(new ApiException(AdminError.用户名已经被注册, username))
            // 否则添加这个用户
            .onItem().ifNull().switchTo(() -> {
                String password = input.getPassword();

                SysUser user = new SysUser();
                user.setUsername(username);
                String saltMd5 = DigestUtil.md5Hex16(username.toUpperCase()).toUpperCase();
                user.setPassword(BcryptUtil.bcryptHash(password, 10, saltMd5.getBytes()));
                // 默认值
                user.setDeleted((byte) 0);
                user.setNickName(input.getUsername());
                user.setStatus(UserStatus.正常);
                return userRepository.persist(user);
            })
            .onItem().transform(RegisterDto::from)
            ;
    }

    public Uni<LoginDto> login(@Valid LoginCommand input) {
        String username = input.getUsername();
        return userRepository.find("username", username).firstResult()
                .onItem().ifNull().failWith(new ApiException(AdminError.用户不存在, username))
                // 校验密码
                .onItem().invoke(Unchecked.consumer(u -> {
                    String decryptPassword = secureManager.decrypt(input.getPassword());
                    String saltMd5 = DigestUtil.md5Hex16(username.toUpperCase()).toUpperCase();
                    String decryptHash = BcryptUtil.bcryptHash(decryptPassword, 10, saltMd5.getBytes());
                    if (!Objects.equals(decryptHash, u.getPassword())) {
                        throw new ApiException(AdminError.密码错误);
                    }
                }))
                // 置空密码
                .onItem().invoke(user -> {
                    user.setPassword(null);
                })
                // 生成token
                .onItem().transform(u -> new LoginDto(tokenManager.generateToken(u)))
                ;
    }

    @RequestScoped
    public Uni<UserPermissionDto> getLoginUserInfo() {
        LoginUser loginUser = securityIdentity.getAttribute(AuthedAugmentor.LOGIN_USER);

        return converter.convertUser(loginUser.getEntity())
            .map(userDto -> {
                UserPermissionDto permissionDto = new UserPermissionDto();
                permissionDto.setUser(userDto);
                permissionDto.setRoleKey(loginUser.getRoleInfo().getRoleKey());
                permissionDto.setPermissions(loginUser.getRoleInfo().getMenuPermissions());
//                permissionDto.setDictTypes(dictionaryManager.getAll());
                return permissionDto;
            })
            .flatMap(permissionDto -> dictionaryManager.getAll().map(dic -> {
                permissionDto.setDictTypes(dic);
                return permissionDto;
            }));
    }
}
