package com.mymall.portal.service.impl;

import com.alibaba.nacos.common.utils.CollectionUtils;
import com.mymall.portal.dao.UmsMemberReceiveAddressMapper;
import com.mymall.portal.model.UmsMember;
import com.mymall.portal.model.UmsMemberReceiveAddress;
import com.mymall.portal.model.UmsMemberReceiveAddressExample;
import com.mymall.portal.service.UmsMemberReceiveAddressService;
import com.mymall.portal.service.UmsMemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UmsMemberReceiveAddressServiceImpl implements UmsMemberReceiveAddressService {
    @Autowired
    private UmsMemberService memberService;
    @Autowired
    private UmsMemberReceiveAddressMapper addressMapper;
    @Override
    public int add(UmsMemberReceiveAddress address) {
        UmsMember currentMember = memberService.getCurrentMember();
        address.setMemberId(currentMember.getId());
        return addressMapper.insert(address);
    }

    @Override
    public int delete(Long id) {
        UmsMember currentMember = memberService.getCurrentMember();
        UmsMemberReceiveAddressExample example = new UmsMemberReceiveAddressExample();
        example.createCriteria().andMemberIdEqualTo(currentMember.getId()).andIdEqualTo(id);
        return addressMapper.deleteByExample(example);
    }

    @Override
    @Transactional
    public int update(Long id, UmsMemberReceiveAddress address) {
        // ① id设为null，因为后面用example条件更新，不是按主键更新
        address.setId(null);

        // ② 获取当前登录会员
        UmsMember currentMember = memberService.getCurrentMember();

        // ③ 构建更新条件：当前会员的指定地址
        UmsMemberReceiveAddressExample example = new UmsMemberReceiveAddressExample();
        example.createCriteria()
                .andMemberIdEqualTo(currentMember.getId())
                .andIdEqualTo(id);

        // ④ 如果新地址要设为默认地址
        if (address.getDefaultStatus() == 1) {
            // 先把该会员原来的默认地址取消（设为0）
            UmsMemberReceiveAddress record = new UmsMemberReceiveAddress();
            record.setDefaultStatus(0);

            UmsMemberReceiveAddressExample updateExample = new UmsMemberReceiveAddressExample();
            updateExample.createCriteria()
                    .andMemberIdEqualTo(currentMember.getId())
                    .andDefaultStatusEqualTo(1);

            addressMapper.updateByExampleSelective(record, updateExample);
        }

        // ⑤ 更新目标地址的信息
        return addressMapper.updateByExampleSelective(address, example);
    }

    @Override
    public List<UmsMemberReceiveAddress> list() {
        UmsMember currentMember = memberService.getCurrentMember();
        UmsMemberReceiveAddressExample example = new UmsMemberReceiveAddressExample();
        example.createCriteria().andMemberIdEqualTo(currentMember.getId());
        return addressMapper.selectByExample(example);
    }

    @Override
    public UmsMemberReceiveAddress getItem(Long id) {
        // ① 获取当前登录会员
        UmsMember currentMember = memberService.getCurrentMember();

        // ② 构建查询条件：当前会员 + 指定地址id
        UmsMemberReceiveAddressExample example = new UmsMemberReceiveAddressExample();
        example.createCriteria()
                .andMemberIdEqualTo(currentMember.getId())
                .andIdEqualTo(id);

        // ③ 执行查询
        List<UmsMemberReceiveAddress> addressList = addressMapper.selectByExample(example);

        // ④ 查到了返回第一条
        if (!CollectionUtils.isEmpty(addressList)) {
            return addressList.get(0);
        }

        // ⑤ 没查到返回null（可能地址不存在或不是自己的）
        return null;
    }


}
