package com.aipal.service;

import com.aipal.entity.SysUser;
import com.aipal.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper userMapper;

    public Page<SysUser> listUsers(int pageNum, int pageSize) {
        Page<SysUser> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(SysUser::getCreateTime);
        return userMapper.selectPage(page, wrapper);
    }

    public SysUser getUserById(Long id) {
        return userMapper.selectById(id);
    }

    public SysUser getUserByUsername(String username) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, username);
        return userMapper.selectOne(wrapper);
    }

    public boolean saveUser(SysUser user) {
        return userMapper.insert(user) > 0;
    }

    public boolean updateUser(SysUser user) {
        return userMapper.updateById(user) > 0;
    }

    public boolean deleteUser(Long id) {
        return userMapper.deleteById(id) > 0;
    }
}
