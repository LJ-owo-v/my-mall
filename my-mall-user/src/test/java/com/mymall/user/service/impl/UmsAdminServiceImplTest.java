package com.mymall.user.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.mymall.user.dao.UmsAdminLoginLogMapper;
import com.mymall.user.dao.UmsAdminMapper;
import com.mymall.user.dao.UmsAdminRoleRelationDao;
import com.mymall.user.dao.UmsAdminRoleRelationMapper;
import com.mymall.user.domain.UmsAdminParam;
import com.mymall.user.domain.UpdateAdminPasswordParam;
import com.mymall.user.model.UmsAdmin;
import com.mymall.user.model.UmsAdminExample;
import com.mymall.user.model.UmsAdminRoleRelation;
import com.mymall.user.model.UmsAdminRoleRelationExample;
import com.mymall.user.service.UmsAdminCacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UmsAdminServiceImpl 纯单元测试。
 *
 * <p>按任务说明 5.2 的方案 B：优先测<b>不依赖 Sa-Token 登录态</b>的方法
 * （getAdminByUsername / register / updatePassword / updateRole）。
 * 只有 updateRole 里的"强制下线"需要确定性的静态桩，用 mockStatic 处理，
 * 其余方法完全不碰 StpUtil，避免单测依赖运行时上下文。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UmsAdminServiceImpl")
class UmsAdminServiceImplTest {

    @Mock private UmsAdminMapper adminMapper;
    @Mock private UmsAdminRoleRelationMapper adminRoleRelationMapper;
    @Mock private UmsAdminRoleRelationDao adminRoleRelationDao;
    @Mock private UmsAdminLoginLogMapper loginLogMapper;
    @Mock private UmsAdminCacheService adminCacheService;

    @InjectMocks private UmsAdminServiceImpl adminService;

    @Test
    @DisplayName("getAdminByUsername_返回对应管理员")
    void getAdminByUsername_返回对应管理员() {
        UmsAdmin admin = admin("lijing", "hashed");
        when(adminMapper.selectByExample(any(UmsAdminExample.class))).thenReturn(List.of(admin));

        UmsAdmin result = adminService.getAdminByUsername("lijing");

        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo("lijing");
    }

    @Test
    @DisplayName("getAdminByUsername_用户不存在_返回null")
    void getAdminByUsername_用户不存在_返回null() {
        when(adminMapper.selectByExample(any(UmsAdminExample.class))).thenReturn(new ArrayList<>());

        assertThat(adminService.getAdminByUsername("nobody")).isNull();
    }

    @Test
    @DisplayName("register_用户名已存在_返回null且不落库")
    void register_用户名已存在_返回null且不落库() {
        when(adminMapper.selectByExample(any(UmsAdminExample.class)))
                .thenReturn(List.of(admin("lijing", "hashed")));

        UmsAdminParam param = new UmsAdminParam();
        param.setUsername("lijing");
        param.setPassword("123456");

        assertThat(adminService.register(param)).isNull();
        verify(adminMapper, never()).insert(any(UmsAdmin.class));
    }

    @Test
    @DisplayName("register_用户名可用_密码以BCrypt密文落库")
    void register_用户名可用_密码以BCrypt密文落库() {
        when(adminMapper.selectByExample(any(UmsAdminExample.class))).thenReturn(new ArrayList<>());

        UmsAdminParam param = new UmsAdminParam();
        param.setUsername("newadmin");
        param.setPassword("123456");

        UmsAdmin result = adminService.register(param);

        ArgumentCaptor<UmsAdmin> captor = ArgumentCaptor.forClass(UmsAdmin.class);
        verify(adminMapper).insert(captor.capture());
        UmsAdmin saved = captor.getValue();

        assertThat(result).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(1);
        // 明文绝不能落库，且必须能被 checkpw 反查
        assertThat(saved.getPassword()).isNotEqualTo("123456");
        assertThat(BCrypt.checkpw("123456", saved.getPassword())).isTrue();
    }

    @Test
    @DisplayName("updatePassword_旧密码错误_返回-3且不更新")
    void updatePassword_旧密码错误_返回3且不更新() {
        UmsAdmin admin = admin("lijing", BCrypt.hashpw("correct"));
        when(adminMapper.selectByExample(any(UmsAdminExample.class))).thenReturn(List.of(admin));

        UpdateAdminPasswordParam param = new UpdateAdminPasswordParam();
        param.setUsername("lijing");
        param.setOldPassword("wrong");
        param.setNewPassword("newpass");

        assertThat(adminService.updatePassword(param)).isEqualTo(-3);
        verify(adminMapper, never()).updateByPrimaryKey(any(UmsAdmin.class));
    }

    @Test
    @DisplayName("updatePassword_新旧密码相同_当前实现未拦截仍会更新")
    void updatePassword_新旧密码相同_当前实现未拦截仍会更新() {
        UmsAdmin admin = admin("lijing", BCrypt.hashpw("same"));
        when(adminMapper.selectByExample(any(UmsAdminExample.class))).thenReturn(List.of(admin));

        UpdateAdminPasswordParam param = new UpdateAdminPasswordParam();
        param.setUsername("lijing");
        param.setOldPassword("same");
        param.setNewPassword("same");

        // 如实记录当前行为：方法内没有 "新旧密码不能相同" 的校验，会重新加密并整行更新
        // （详见回报"疑似缺陷"第 3 条，未修改业务代码）
        assertThat(adminService.updatePassword(param)).isEqualTo(1);
        verify(adminMapper).updateByPrimaryKey(any(UmsAdmin.class));
        verify(adminCacheService).delAdmin(admin.getId());
    }

    @Test
    @DisplayName("updateRole_先删后插_并清缓存与强制下线")
    void updateRole_先删后插_并清缓存与强制下线() {
        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            int count = adminService.updateRole(1L, List.of(2L, 3L));

            assertThat(count).isEqualTo(2);
            // 旧的 1→角色关系必须先清掉，否则会留下"已撤销但仍在"的角色
            verify(adminRoleRelationMapper).deleteByExample(any(UmsAdminRoleRelationExample.class));

            ArgumentCaptor<List<UmsAdminRoleRelation>> captor = ArgumentCaptor.forClass(List.class);
            verify(adminRoleRelationDao).insertList(captor.capture());
            assertThat(captor.getValue()).hasSize(2)
                    .allSatisfy(relation -> assertThat(relation.getAdminId()).isEqualTo(1L));

            // 权限变了 → 缓存要清、会话要踢，否则降权 7 天内不生效
            verify(adminCacheService).delAdmin(1L);
            stpUtil.verify(() -> StpUtil.logout(1L));
        }
    }

    @Test
    @DisplayName("updateRole_角色列表为空_只删不插")
    void updateRole_角色列表为空_只删不插() {
        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            int count = adminService.updateRole(1L, new ArrayList<>());

            assertThat(count).isZero();
            verify(adminRoleRelationMapper).deleteByExample(any(UmsAdminRoleRelationExample.class));
            verify(adminRoleRelationDao, never()).insertList(anyList());
        }
    }

    private UmsAdmin admin(String username, String hashedPassword) {
        UmsAdmin admin = new UmsAdmin();
        admin.setId(1L);
        admin.setUsername(username);
        admin.setPassword(hashedPassword);
        admin.setStatus(1);
        return admin;
    }
}
