package com.xy.welllog.controller;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xy.welllog.common.Result;
import com.xy.welllog.entity.SysUser;
import com.xy.welllog.service.SysOperationLogService;
import com.xy.welllog.service.SysUserService;
import com.xy.welllog.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private SysOperationLogService operationLogService;

    private static final String SALT = "xwlog#2026"; // 保持与注册一致

    private String getUsername(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return jwtUtils.getUsernameFromToken(header.substring(7));
        }
        return null;
    }

    private Long getUserId(HttpServletRequest request) {
        String username = getUsername(request);
        if (!StringUtils.hasText(username)) {
            return 1L;
        }
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, username);
        SysUser user = sysUserService.getOne(wrapper);
        return user != null ? user.getId() : 1L;
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/info")
    public Result<SysUser> getInfo(HttpServletRequest request) {
        String username = getUsername(request);
        if (username == null) return Result.failed("鉴权失败，请重新登录");

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, username);
        SysUser user = sysUserService.getOne(wrapper);
        if (user != null) {
            user.setPassword(null);
        }
        return Result.success(user);
    }

    /**
     * 更新当前用户个人信息
     */
    @PostMapping("/updateProfile")
    public Result<Void> updateProfile(@RequestBody SysUser userParam, HttpServletRequest request) {
        String username = getUsername(request);
        if (username == null) return Result.failed("鉴权失败，请重新登录");

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, username);
        SysUser currentUser = sysUserService.getOne(wrapper);

        if (currentUser != null) {
            currentUser.setRealName(userParam.getRealName());
            currentUser.setEmail(userParam.getEmail());
            currentUser.setAvatar(userParam.getAvatar());
            currentUser.setIntroduction(userParam.getIntroduction());
            sysUserService.updateById(currentUser);
            operationLogService.recordLog("用户管理", "更新个人资料", 0, 0L, currentUser.getId());
            return Result.success(null, "个人信息更新成功");
        }
        return Result.failed("未找到当前用户");
    }

    @PostMapping("/updateSettings")
    public Result<Void> updateSettings(@RequestBody SysUser userParam, HttpServletRequest request) {
        String username = getUsername(request);
        if (username == null) return Result.failed("鉴权失败，请重新登录");

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, username);
        SysUser currentUser = sysUserService.getOne(wrapper);

        if (currentUser != null) {
            currentUser.setSysSettings(userParam.getSysSettings());
            sysUserService.updateById(currentUser);
            operationLogService.recordLog("用户管理", "更新系统偏好设置", 0, 0L, currentUser.getId());
            return Result.success(null, "系统偏好设置更新成功");
        }
        return Result.failed("未找到当前用户");
    }

    /**
     * 更新当前用户密码
     */
    @PostMapping("/updatePwd")
    public Result<Void> updatePwd(@RequestBody com.xy.welllog.dto.UpdatePwdDTO pwdDto, HttpServletRequest request) {
        String username = getUsername(request);
        if (username == null) return Result.failed("鉴权失败，请重新登录");

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, username);
        SysUser currentUser = sysUserService.getOne(wrapper);

        if (currentUser == null) return Result.failed("用户不存在");

        String oldEncryptPsw = DigestUtil.md5Hex(pwdDto.getOldPassword() + SALT);
        if (!oldEncryptPsw.equals(currentUser.getPassword())) {
            return Result.failed("原密码不正确");
        }

        String newEncryptPsw = DigestUtil.md5Hex(pwdDto.getNewPassword() + SALT);
        currentUser.setPassword(newEncryptPsw);
        sysUserService.updateById(currentUser);
        operationLogService.recordLog("用户管理", "修改登录密码", 0, 0L, currentUser.getId());

        return Result.success(null, "密码修改成功，请重新登录");
    }

    /**
     * [管理员] 管理员获取用户分页列表
     */
    @GetMapping("/page")
    public Result<Page<SysUser>> page(@RequestParam(defaultValue = "1") Integer current,
                                      @RequestParam(defaultValue = "10") Integer size) {
        Page<SysUser> pageParam = new Page<>(current, size);
        // 按管理员角色排序，保证管理员用户在前面，方便管理员查看和操作,内部排序按创建时间升序
        Page<SysUser> userPage = sysUserService.page(pageParam,
                new LambdaQueryWrapper<SysUser>().orderByAsc(SysUser::getRole).orderByAsc(SysUser::getCreateTime)
                        .orderByAsc(SysUser::getId));
        userPage.getRecords().forEach(u -> u.setPassword(null));
        return Result.success(userPage);
    }

    /**
     * [管理员] 新增用户
     */
    @PostMapping("/add")
    public Result<Void> add(@RequestBody SysUser user, HttpServletRequest request) {
        long count = sysUserService.count(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, user.getUsername()));
        if (count > 0) return Result.failed("用户名已存在");

        String encryptPassword = DigestUtil.md5Hex("123456" + SALT);
        user.setPassword(encryptPassword);

        if (!StringUtils.hasText(user.getRole())) {
            user.setRole("user");
        }

        sysUserService.save(user);
        operationLogService.recordLog("用户管理", "新增用户 [" + user.getUsername() + "]", 0, 0L, getUserId(request));
        return Result.success(null, "添加成功，默认密码为：123456");
    }

    /**
     * [管理员] 编辑用户
     */
    @PostMapping("/update")
    public Result<Void> update(@RequestBody SysUser user, HttpServletRequest request) {
        SysUser exist = sysUserService.getById(user.getId());
        if (exist != null) {
            exist.setEmail(user.getEmail());
            exist.setRealName(user.getRealName());
            exist.setRole(user.getRole());
            exist.setIntroduction(user.getIntroduction());
            sysUserService.updateById(exist);
            operationLogService.recordLog("用户管理", "更新用户 [" + exist.getUsername() + "]", 0, 0L, getUserId(request));
            return Result.success(null, "更新用户成功");
        }
        return Result.failed("用户不存在");
    }

    /**
     * [管理员] 删除用户
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        SysUser exist = sysUserService.getById(id);
        sysUserService.removeById(id);
        String target = exist != null ? exist.getUsername() : String.valueOf(id);
        operationLogService.recordLog("用户管理", "删除用户 [" + target + "]", 0, 0L, getUserId(request));
        return Result.success(null, "删除成功");
    }
}
