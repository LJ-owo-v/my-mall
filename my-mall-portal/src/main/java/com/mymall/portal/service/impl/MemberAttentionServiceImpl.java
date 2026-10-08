package com.mymall.portal.service.impl;

import com.mymall.portal.domain.MemberBrandAttention;
import com.mymall.portal.model.UmsMember;
import com.mymall.portal.repository.MemberBrandAttentionRepository;
import com.mymall.portal.service.MemberAttentionService;
import com.mymall.portal.service.UmsMemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class MemberAttentionServiceImpl implements MemberAttentionService {
    @Autowired
    private UmsMemberService memberService;
    @Autowired
    private MemberBrandAttentionRepository memberBrandAttentionRepository;

    @Override
    public int add(MemberBrandAttention memberBrandAttention) {
        int count = 0;

        // ① 获取当前登录会员
        UmsMember member = memberService.getCurrentMember();

        // ② 填充会员信息（冗余存储）
        memberBrandAttention.setMemberId(member.getId());
        memberBrandAttention.setMemberNickname(member.getNickname());
        memberBrandAttention.setMemberIcon(member.getIcon());
        memberBrandAttention.setCreateTime(new Date());

        // ③ 查询是否已经关注过该品牌（防止重复关注）
        MemberBrandAttention findAttention = memberBrandAttentionRepository
                .findByMemberIdAndBrandId(memberBrandAttention.getMemberId(), memberBrandAttention.getBrandId());

        // ④ 没关注过才保存
        if (findAttention == null) {
            memberBrandAttentionRepository.save(memberBrandAttention);
            count = 1;
        }

        // ⑤ 返回1=关注成功，0=已关注过
        return count;
    }

    @Override
    public int delete(Long brandId) {
        UmsMember member = memberService.getCurrentMember();
        return memberBrandAttentionRepository.deleteByMemberIdAndBrandId(member.getId(),brandId);
    }

    @Override
    public Page<MemberBrandAttention> list(Integer pageNum, Integer pageSize) {
        UmsMember member = memberService.getCurrentMember();
        Pageable pageable = PageRequest.of(pageNum-1,pageSize);
        return memberBrandAttentionRepository.findByMemberId(member.getId(),pageable);
    }

    @Override
    public MemberBrandAttention detail(Long brandId) {
        UmsMember member = memberService.getCurrentMember();
        return memberBrandAttentionRepository.findByMemberIdAndBrandId(member.getId(), brandId);
    }

    @Override
    public void clear() {
        UmsMember member = memberService.getCurrentMember();
        memberBrandAttentionRepository.deleteAllByMemberId(member.getId());
    }

}
