package com.mymall.user.service;

import com.mymall.user.model.UmsAdmin;

public interface UmsAdminCacheService {
    UmsAdmin getAdmin(Long id);

    void setAdmin(UmsAdmin admin);

    void delAdmin(Long id);
}
