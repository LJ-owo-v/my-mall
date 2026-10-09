package com.mymall.portal.service;

import cn.dev33.satoken.stp.SaTokenInfo;
import com.mymall.portal.model.UmsMember;

public interface UmsMemberService {
    void register(String username, String password, String telephone, String authCode);

    SaTokenInfo login(String username, String password);

    UmsMember getCurrentMember();

    void logout();

    String generateAuthCode(String telephone);

    void updatePassword(String telephone, String password, String authCode);

    void updateIntegration(Long id, Integer integration);

    UmsMember getById(Long memberId);
}
