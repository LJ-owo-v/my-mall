package com.mymall.portal.service.impl;

import com.mymall.common.dto.UserDto;


import cn.dev33.satoken.stp.SaTokenInfo;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.mymall.common.constant.AuthConstant;
import com.mymall.common.exception.Asserts;
import com.mymall.portal.dao.UmsMemberLevelMapper;
import com.mymall.portal.dao.UmsMemberMapper;
import com.mymall.portal.model.UmsMember;
import com.mymall.portal.model.UmsMemberExample;
import com.mymall.portal.model.UmsMemberLevel;
import com.mymall.portal.model.UmsMemberLevelExample;
import com.mymall.portal.service.UmsMemberCacheService;
import com.mymall.portal.service.UmsMemberService;
import com.mymall.portal.util.StpMemberUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Random;

@Service
public class UmsMemberServiceImpl  implements UmsMemberService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UmsMemberServiceImpl.class);
    @Autowired
    private UmsMemberMapper memberMapper;
    @Autowired
    private UmsMemberCacheService memberCacheService;

    @Autowired
    private UmsMemberLevelMapper memberLevelMapper;
    @Value("${redis.key.authCode}")
    private String REDIS_KEY_PREFIX_AUTH_CODE;
    @Value("${redis.expire.authCode}")
    private Long AUTH_CODE_EXPIRE_SECONDS;


    @Override
    public void register(String username, String password, String telephone, String authCode) {
        //验证验证码
        if (!verifyAuthCode(telephone, authCode)){
            Asserts.fail("验证码错误");
        }

        //查询是否已有该用户
        UmsMemberExample example = new UmsMemberExample();
        example.createCriteria().andUsernameEqualTo(username);
        example.or(example.createCriteria().andPhoneEqualTo(telephone));
        List<UmsMember> umsMembers = memberMapper.selectByExample(example);
        if (!CollectionUtils.isEmpty(umsMembers)) {
            Asserts.fail("该用户已经存在");
        }
        //没有该用户进行添加操作
        UmsMember umsMember = new UmsMember();
        umsMember.setUsername(username);
        umsMember.setPhone(telephone);
        umsMember.setPassword(BCrypt.hashpw(password));
        umsMember.setCreateTime(new Date());
        umsMember.setStatus(1);
        //获取默认会员等级并设置
        UmsMemberLevelExample levelExample = new UmsMemberLevelExample();
        levelExample.createCriteria().andDefaultStatusEqualTo(1);
        List<UmsMemberLevel> memberLevelList = memberLevelMapper.selectByExample(levelExample);
        if (!CollectionUtils.isEmpty(memberLevelList)) {
            umsMember.setMemberLevelId(memberLevelList.get(0).getId());
        }
        memberMapper.insert(umsMember);
        umsMember.setPassword(null);
    }

    @Override
    public SaTokenInfo login(String username, String password) {
        if(StrUtil.isEmpty(username)||StrUtil.isEmpty(password)){
            Asserts.fail("用户名或密码不能为空！");
        }
        UmsMember member = getByUsername(username);
        if(member==null){
            Asserts.fail("找不到该用户！");
        }
        if (!BCrypt.checkpw(password, member.getPassword())) {
            Asserts.fail("密码不正确！");
        }
        if(member.getStatus()!=1){
            Asserts.fail("该账号已被禁用！");
        }
        // 登录校验成功后，一行代码实现登录
        StpMemberUtil.login(member.getId());
        UserDto userDto = new UserDto();
        userDto.setId(member.getId());
        userDto.setUsername(member.getUsername());
        userDto.setClientId(AuthConstant.PORTAL_CLIENT_ID);
        // 将用户信息存储到Session中
        StpMemberUtil.getSession().set(AuthConstant.STP_MEMBER_INFO,userDto);
        // 获取当前登录用户Token信息
        return StpMemberUtil.getTokenInfo();
    }

    @Override
    public UmsMember getCurrentMember() {
        // ① 从 sa-token 的 session 中取出当前登录会员信息（登录时存进去的）
        UserDto userDto = (UserDto) StpMemberUtil.getSession().get(AuthConstant.STP_MEMBER_INFO);

        // ② 先查 Redis 缓存，看有没有这个会员
        UmsMember member = memberCacheService.getMember(userDto.getId());

        // ③ 缓存命中，直接返回
        if (member != null) {
            return member;
        } else {
            // ④ 缓存没命中，查数据库
            member = getById(userDto.getId());

            // ⑤ 查到后写入缓存，下次直接走缓存
            memberCacheService.setMember(member);

            // ⑥ 返回会员信息
            return member;
        }
    }

    @Override
    public void logout() {
        //先清空缓存
        UserDto userDto = (UserDto) StpMemberUtil.getSession().get(AuthConstant.STP_MEMBER_INFO);
        memberCacheService.delMember(userDto.getId());
        //再调用sa-token的登出方法
        StpMemberUtil.logout();
    }

    @Override
    public String generateAuthCode(String telephone) {
        // ① 创建 StringBuilder 用于拼接验证码
        StringBuilder sb = new StringBuilder();

        // ② 创建随机数对象
        Random random = new Random();

        // ③ 循环6次，每次生成0-9的随机数字，拼成6位验证码
        for (int i = 0; i < 6; i++) {
            sb.append(random.nextInt(10));
        }

        // ④ 将验证码存入 Redis，key=手机号，value=验证码（带过期时间，比如5分钟）
        memberCacheService.setAuthCode(telephone, sb.toString());

        // TODO⑤ 返回生成的验证码（实际项目中这里应该发短信，不直接返回）
        return sb.toString();
    }

    @Override
    public void updatePassword(String telephone, String password, String authCode) {
        // ① 按手机号查询会员
        UmsMemberExample example = new UmsMemberExample();
        example.createCriteria().andPhoneEqualTo(telephone);
        List<UmsMember> memberList = memberMapper.selectByExample(example);

        // ② 会员不存在，报错
        if (CollectionUtils.isEmpty(memberList)) {
            Asserts.fail("该账号不存在");
        }

        // ③ 验证短信验证码是否正确（从Redis取之前发的验证码比对）
        if (!verifyAuthCode(authCode, telephone)) {
            Asserts.fail("验证码错误");
        }

        // ④ 取出会员对象，新密码BCrypt加密后设置
        UmsMember umsMember = memberList.get(0);
        umsMember.setPassword(BCrypt.hashpw(password));

        // ⑤ 更新数据库（只更新非null字段）
        memberMapper.updateByPrimaryKeySelective(umsMember);
    }



    private UmsMember getById(Long id) {
        return memberMapper.selectByPrimaryKey(id);
    }


    public UmsMember getByUsername(String username) {
        // 创建查询条件对象
        UmsMemberExample example = new UmsMemberExample();

        // 设置查询条件：username = 传入的用户名
        example.createCriteria().andUsernameEqualTo(username);

        // 执行查询，返回匹配的会员列表
        List<UmsMember> memberList = memberMapper.selectByExample(example);

        // 如果列表不为空，返回第一条数据
        if (!CollectionUtils.isEmpty(memberList)) {
            return memberList.get(0);
        }

        // 没查到，返回null
        return null;
    }


    //对输入的验证码进行校验
    private boolean verifyAuthCode(String telephone, String authCode) {
        if(StringUtils.isEmpty(authCode)){
            return false;
        }
        String realAuthCode = memberCacheService.getAuthCode(telephone);
        return authCode.equals(realAuthCode);
    }
}
