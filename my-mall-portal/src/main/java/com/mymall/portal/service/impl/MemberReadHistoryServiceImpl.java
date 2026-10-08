package com.mymall.portal.service.impl;


import com.mymall.portal.domain.MemberReadHistory;
import com.mymall.portal.model.UmsMember;
import com.mymall.portal.repository.MemberReadHistoryRepository;
import com.mymall.portal.service.MemberReadHistoryService;
import com.mymall.portal.service.UmsMemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class MemberReadHistoryServiceImpl implements MemberReadHistoryService {
    @Autowired
    private MemberReadHistoryRepository memberReadHistoryRepository;
    @Autowired
    private UmsMemberService memberService;

    @Override
    public int create(MemberReadHistory memberReadHistory) {
        // ① 获取当前登录会员
        UmsMember member = memberService.getCurrentMember();

        // ② 填充会员信息（冗余存储，不用每次查会员表）
        memberReadHistory.setMemberId(member.getId());
        memberReadHistory.setMemberNickname(member.getNickname());
        memberReadHistory.setMemberIcon(member.getIcon());

        // ③ id设为null，MongoDB自动生成
        memberReadHistory.setId(null);

        // ④ 设置浏览时间
        memberReadHistory.setCreateTime(new Date());

        // ⑤ 保存到 MongoDB
        memberReadHistoryRepository.save(memberReadHistory);

        return 1;
    }

    @Override
    public int delete(List<String> ids) {
        // ① 构建要删除的对象列表（MongoDB的deleteAll需要实体对象）
        List<MemberReadHistory> deleteList = new ArrayList<>();
        for (String id : ids) {
            MemberReadHistory memberReadHistory = new MemberReadHistory();
            memberReadHistory.setId(id);
            deleteList.add(memberReadHistory);
        }

        // ② 批量删除
        memberReadHistoryRepository.deleteAll(deleteList);

        // ③ 返回删除数量
        return ids.size();
    }

    @Override
    public void clear() {
        // ① 获取当前登录会员
        UmsMember member = memberService.getCurrentMember();

        // ② 按会员id删除该用户的所有浏览历史
        memberReadHistoryRepository.deleteAllByMemberId(member.getId());
    }

    @Override
    public Page<MemberReadHistory> list(Integer pageNum, Integer pageSize) {
        UmsMember member = memberService.getCurrentMember();
        Pageable pageable = PageRequest.of(pageNum-1, pageSize);
        return memberReadHistoryRepository.findByMemberIdOrderByCreateTimeDesc(member.getId(),pageable);
    }


}
