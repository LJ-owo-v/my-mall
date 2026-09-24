package com.mymall.user.service;

import com.mymall.user.model.UmsAdmin;

public interface UmsAdminCacheService {
    /** 获取缓存的后台用户信息 */
    UmsAdmin getAdmin(Long id);

    /** 设置缓存的后台用户信息 */
    void setAdmin(UmsAdmin admin);

    /** 删除缓存的后台用户信息 */
    void delAdmin(Long id);
}
