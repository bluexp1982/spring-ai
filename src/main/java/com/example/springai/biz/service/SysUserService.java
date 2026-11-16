package com.example.springai.biz.service;

import com.example.springai.biz.entity.SysUser;

public interface SysUserService {

    SysUser findByUsername(String username);

    boolean create(SysUser user);
}