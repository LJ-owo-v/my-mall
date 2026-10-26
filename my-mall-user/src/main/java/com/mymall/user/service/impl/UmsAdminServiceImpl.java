package com.mymall.user.service.impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.github.pagehelper.PageHelper;
import com.mymall.common.dto.UserDto;
import com.mymall.common.constant.AuthConstant;
import com.mymall.common.exception.Asserts;
import com.mymall.user.dao.UmsAdminLoginLogMapper;
import com.mymall.user.dao.UmsAdminMapper;
import com.mymall.user.dao.UmsAdminRoleRelationDao;
import com.mymall.user.dao.UmsAdminRoleRelationMapper;
import com.mymall.user.domain.UmsAdminParam;
import com.mymall.user.domain.UpdateAdminPasswordParam;
import com.mymall.user.model.*;
import com.mymall.user.service.UmsAdminCacheService;
import com.mymall.user.service.UmsAdminService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 后台管理员 Service 实现 —— 与原项目 com.macro.mall.service.impl.UmsAdminServiceImpl 逐项对齐。
 *
 * 必须对齐的 4 条失败文案（统一 code=500，靠 GlobalExceptionHandler 转 CommonResult）：
 *   用户名或密码不能为空！ / 找不到该用户！ / 密码不正确！ / 该账号已被禁用！
 */

@Service
public  class UmsAdminServiceImpl implements UmsAdminService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UmsAdminServiceImpl.class);
    @Autowired
    private UmsAdminMapper adminMapper;
    @Autowired
    private UmsAdminRoleRelationMapper adminRoleRelationMapper;
    @Autowired
    private UmsAdminRoleRelationDao adminRoleRelationDao;
    @Autowired
    private UmsAdminLoginLogMapper loginLogMapper;
    @Autowired
    private UmsAdminCacheService adminCacheService;

    @Override
    public UmsAdmin getAdminByUsername(String username) {
        UmsAdminExample example = new UmsAdminExample();
        example.createCriteria().andUsernameEqualTo(username);
        List<UmsAdmin> adminList = adminMapper.selectByExample(example);
        if (adminList != null && !adminList.isEmpty()) {
            return adminList.get(0);
        }
        return null;
    }


    //新用户注册
    @Override
    public UmsAdmin register(UmsAdminParam umsAdminParam) {
        UmsAdmin umsAdmin = new UmsAdmin();
        BeanUtils.copyProperties(umsAdminParam, umsAdmin);
        umsAdmin.setCreateTime(new Date());
        umsAdmin.setStatus(1);
        // 查询是否有相同用户名的用户
        UmsAdminExample example = new UmsAdminExample();
        example.createCriteria().andUsernameEqualTo(umsAdmin.getUsername());
        List<UmsAdmin> umsAdminList = adminMapper.selectByExample(example);
        if (!umsAdminList.isEmpty()) {
            return null;   // 控制器据此返回 CommonResult.failed(),有相同名字的用户
        }
        umsAdmin.setPassword(BCrypt.hashpw(umsAdminParam.getPassword()));
        adminMapper.insert(umsAdmin);
        return umsAdmin;
    }

    /**
     * TODO 校验（空值→用户存在→密码正确→账号正常）
     *      ↓
     * TODO 创建会话（StpUtil.login 写 token 映射）
     *      ↓
     * TODO 组装权限（三表 join 查出资源列表）
     *      ↓
     * TODO 写入 Session（UserDto 存 Redis，网关靠它鉴权）
     *      ↓
     * TODO 返回 token + 写日志
     */


    @Override
    public SaTokenInfo login(String username, String password) {
        // ① 空值校验：用户名或密码为空直接抛业务异常，不往下走
        if (StrUtil.isEmpty(username) || StrUtil.isEmpty(password)) {
            Asserts.fail("用户名或密码不能为空！");
        }

        // ② 按用户名查数据库，查不到说明账号不存在
        UmsAdmin admin = getAdminByUsername(username);
        if (admin == null) {
            Asserts.fail("找不到该用户！");
        }

        // ③ BCrypt 密码校验：把前端传来的明文密码和数据库里的密文做比对
        //    BCrypt 自带盐值，同样的密码每次加密结果也不同，必须用 checkpw 而不是 equals
        if (!BCrypt.checkpw(password, admin.getPassword())) {
            Asserts.fail("密码不正确！");
        }

        // ④ 账号状态校验：status=1 为正常，0 为禁用
        if (admin.getStatus() != 1) {
            Asserts.fail("该账号已被禁用！");
        }

        // ⑤ Sa-Token 登录：以 adminId 作为 loginId 创建会话
        //    底层会在 Redis 写入 token→loginId 映射，loginType 默认是 "login"
        StpUtil.login(admin.getId());

        // ⑥ 组装 UserDto 并存入 Session
        //    UserDto 是网关鉴权时读取的核心对象，包名必须是 com.mymall.common.dto.UserDto
        UserDto userDto = new UserDto();
        userDto.setId(admin.getId());
        userDto.setUsername(admin.getUsername());
        userDto.setClientId(AuthConstant.ADMIN_CLIENT_ID);  // 标识后台端："admin-app"

        // 查出该用户关联的所有资源权限（admin→role→resource 三表 join）
        List<UmsResource> resourceList = getResourceList(admin.getId());
        // 拼成 "资源ID:资源名称" 格式，如 "5:商品管理"，与网关权限表 value 格式一致
        List<String> permissionList = resourceList.stream()
                .map(item -> item.getId() + ":" + item.getName())
                .toList();
        userDto.setPermissionList(permissionList);

        // ⑦ 把 UserDto 写入 Sa-Token Session（Redis key: Authorization:login:session:{adminId}）
        //    网关鉴权时通过 StpInterfaceImpl 从这里取权限列表
        StpUtil.getSession().set(AuthConstant.STP_ADMIN_INFO, userDto);

        // ⑧ 获取 token 信息（包含 tokenValue、tokenName、isLogin 等）
        SaTokenInfo saTokenInfo = StpUtil.getTokenInfo();

        // ⑨ 写登录日志到 ums_admin_login_log 表（含 IP、时间、用户名）
        insertLoginLog(admin);

        return saTokenInfo;
    }


    //添加登入记录
    private void insertLoginLog(UmsAdmin admin) {
        if (admin == null) {
            return;
        }
        UmsAdminLoginLog loginLog = new UmsAdminLoginLog();
        loginLog.setAdminId(admin.getId());
        loginLog.setCreateTime(new Date());
        // 获取 ip
        ServletRequestAttributes
                attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            loginLog.setIp(request.getRemoteAddr());
        }
        loginLogMapper.insert(loginLog);
    }

    @Override
    public List<UmsResource> getResourceList(Long adminId) {
        return adminRoleRelationDao.getResourceList(adminId);
    }


    @Override
    public List<UmsRole> getRoleList(Long id) {
        return adminRoleRelationDao.getRoleList(id);
    }

    /**
     * TODO Sa-Token Session（UserDto，只有 id 和权限）
     *         ↓ 拿到 id
     * TODO Redis 缓存（UmsAdmin，完整用户信息）
     *         ↓ 缓存没有
     * TODO MySQL 数据库（查完写回 Redis）
     *
     */
    @Override
    public UmsAdmin getCurrentAdmin() {
        // 1. 从 Sa-Token 的 Session 中取出登录时写入的 UserDto（key = "adminInfo"）
        UserDto userDto = (UserDto) StpUtil.getSession().get(AuthConstant.STP_ADMIN_INFO);

        // 2. 取不到说明没登录、token 过期，或新老服务序列化不兼容，直接抛业务异常
        if (userDto == null) {
            LOGGER.warn("session 里没有 {}，可能未登录或 session 契约不匹配", AuthConstant.STP_ADMIN_INFO);
            Asserts.fail("暂未登录或token已经过期");
        }

        // 3. 先从 Redis 缓存里查完整的 UmsAdmin（UserDto 只有 id/username/权限，不含完整用户信息）
        UmsAdmin admin = adminCacheService.getAdmin(userDto.getId());

        // 4. 缓存没有（首次请求或缓存过期），查数据库，再写回缓存
        if (admin == null) {
            admin = adminMapper.selectByPrimaryKey(userDto.getId());
            adminCacheService.setAdmin(admin);
        }

        return admin;
    }

    /**
     * 登出功能
     */
    @Override
    public void logout() {
        // 1. 先从 Session 取出当前登录用户的 UserDto
        UserDto userDto = (UserDto) StpUtil.getSession().get(AuthConstant.STP_ADMIN_INFO);

        if (userDto != null) {
            // 2. 删除 Redis 中缓存的 UmsAdmin 完整用户信息，避免下次登录读到旧数据
            adminCacheService.delAdmin(userDto.getId());
        }

        // 3. Sa-Token 登出：删除 token→loginId 映射、Session 等所有会话数据
        //    登出后该 token 立即失效，再请求受保护接口会返回 401
        StpUtil.logout();
    }

    /**
     * 获取用户列表
     */
    @Override
    public List<UmsAdmin> list(String keyword, Integer pageSize, Integer pageNum) {
        // 1. 开启 PageHelper 分页（紧跟在它后面的第一条 SQL 会自动加 LIMIT）
        PageHelper.startPage(pageNum, pageSize);

        // 2. 构造 MBG 的 Example 查询条件
        UmsAdminExample example = new UmsAdminExample();
        UmsAdminExample.Criteria criteria = example.createCriteria();

        // 3. 如果传了关键词，按用户名或昵称模糊搜索
        if (!StringUtils.isEmpty(keyword)) {
            // 条件一：用户名 LIKE '%keyword%'
            criteria.andUsernameLike("%" + keyword + "%");
            // 条件二（OR）：昵称 LIKE '%keyword%'
            // 最终 SQL：WHERE (username LIKE ?) OR (nick_name LIKE ?)
            example.or(example.createCriteria().andNickNameLike("%" + keyword + "%"));
        }

        // 4. 执行查询，PageHelper 会自动拦截并加分页
        return adminMapper.selectByExample(example);
    }

    /**
     * 根据ID获取用户详情
     */
    @Override
    public UmsAdmin getItem(Long id) {
        return adminMapper.selectByPrimaryKey(id);
    }

    /**
     * 根据ID修改用户
     * 大部分逻辑在TODO**判断前端有没有改密码**，决定要不要重新加密。
     */
    @Override
    public int update(Long id, UmsAdmin admin) {
        // 1. 把路径里的 id 设置到对象上，防止前端没传 id
        admin.setId(id);

        // 2. 查出数据库里现有的用户记录
        UmsAdmin rawAdmin = adminMapper.selectByPrimaryKey(id);

        if (rawAdmin != null && rawAdmin.getPassword().equals(admin.getPassword())) {
            // 3. 前端传来的密码和数据库里的密文一模一样
            //    说明前端没改密码（只是把原密文原样回传），设为 null 跳过密码更新
            admin.setPassword(null);
        } else {
            // 4. 密码不同，说明用户输入了新密码
            if (StrUtil.isEmpty(admin.getPassword())) {
                // 4.1 新密码为空，也跳过（不允许改成空密码）
                admin.setPassword(null);
            } else {
                // 4.2 新密码非空，用 BCrypt 加密后再存
                admin.setPassword(BCrypt.hashpw(admin.getPassword()));
            }
        }

        // 5. 选择性更新：只更新非 null 字段（password=null 的字段不会被写入）
        int count = adminMapper.updateByPrimaryKeySelective(admin);

        // 6. 删除 Redis 缓存，下次查询会重新从数据库加载最新数据
        adminCacheService.delAdmin(id);

        return count;
    }

    /**
     * 修改密码
     */
    @Override
    public int updatePassword(UpdateAdminPasswordParam param) {
        // 1. 参数校验：用户名、旧密码、新密码任一为空都返回 -1
        if (StrUtil.isEmpty(param.getUsername())
                || StrUtil.isEmpty(param.getOldPassword())
                || StrUtil.isEmpty(param.getNewPassword())) {
            return -1;
        }

        // 2. 按用户名查数据库，查不到返回 -2
        UmsAdminExample example = new UmsAdminExample();
        example.createCriteria().andUsernameEqualTo(param.getUsername());
        List<UmsAdmin> adminList = adminMapper.selectByExample(example);
        if (CollUtil.isEmpty(adminList)) {
            return -2;
        }

        // 3. 取出用户记录
        UmsAdmin umsAdmin = adminList.get(0);

        // 4. 校验旧密码是否正确，不正确返回 -3
        if (!BCrypt.checkpw(param.getOldPassword(), umsAdmin.getPassword())) {
            return -3;
        }

        // 5. 新密码用 BCrypt 加密后设置到对象上
        umsAdmin.setPassword(BCrypt.hashpw(param.getNewPassword()));

        // 6. 更新数据库（全字段更新，因为 umsAdmin 是查出来的完整对象）
        adminMapper.updateByPrimaryKey(umsAdmin);

        // 7. 删除 Redis 缓存，下次查询重新加载
        adminCacheService.delAdmin(umsAdmin.getId());

        return 1;
    }

    /**
     * 根据ID给用户分配角色
     * 原来：用户3 → 角色[1, 2]
     * 现在要改成：用户3 → 角色[2, 3, 4]
     *
     * 第一步：DELETE FROM ums_admin_role_relation WHERE admin_id=3
     *         （用户3的角色关系全清掉）
     *
     * 第二步：INSERT INTO ums_admin_role_relation VALUES (3,2), (3,3), (3,4)
     *         （重新插入新的角色关系）
     */
    @Override
    public int updateRole(Long adminId, List<Long> roleIds) {
        // 1. 记录本次分配了多少个角色（用于返回值）
        int count = roleIds == null ? 0 : roleIds.size();

        // 2. 先删除该用户原来的所有角色关系（经典的"先删后插"模式）
        UmsAdminRoleRelationExample adminRoleRelationExample = new UmsAdminRoleRelationExample();
        adminRoleRelationExample.createCriteria().andAdminIdEqualTo(adminId);
        adminRoleRelationMapper.deleteByExample(adminRoleRelationExample);

        // 3. 建立新的角色关系
        if (!CollectionUtils.isEmpty(roleIds)) {
            List<UmsAdminRoleRelation> list = new ArrayList<>();
            for (Long roleId : roleIds) {
                UmsAdminRoleRelation roleRelation = new UmsAdminRoleRelation();
                roleRelation.setAdminId(adminId);
                roleRelation.setRoleId(roleId);
                list.add(roleRelation);
            }
            // 批量插入（MBG 生成不了 insertList，这是自定义 DAO 方法）
            adminRoleRelationDao.insertList(list);
        }

        // 4. 角色关系变了 → 权限变了，必须让"旧权限"立刻失效
        //    permissionList 是登录时算好写进 Sa-Token session 的（网关 StpInterfaceImpl 直接读 session），
        //    不处理的话，被降权的人会一直拿着旧权限用到 token 过期（timeout=7 天），
        //    提权的人也要等重新登录才生效。
        //    这里选择"强制重登"：先清管理员缓存，再注销该账号的全部会话。
        adminCacheService.delAdmin(adminId);
        kickOutForPermissionChange(adminId);

        return count;
    }

    /**
     * 权限变更后强制指定管理员重新登录（is-concurrent=true，会踢掉他所有端）。
     *
     * <p>失败只告警不抛出：角色关系已经落库，不能因为会话清理失败把整个分配操作判为失败，
     * 但必须留下日志，因为此时旧权限可能仍在生效。
     */
    private void kickOutForPermissionChange(Long adminId) {
        try {
            StpUtil.logout(adminId);
            LOGGER.info("管理员[{}]权限已变更，已强制其重新登录", adminId);
        } catch (Exception e) {
            LOGGER.warn("管理员[{}]权限已变更，但强制下线失败，旧权限可能仍在生效: {}",
                    adminId, e.getMessage());
        }
    }


    @Override
    public int delete(Long id) {
        int count = adminMapper.deleteByPrimaryKey(id);
        adminCacheService.delAdmin(id);
        return count;
    }
}
