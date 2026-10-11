package com.mymall.portal.service.impl;


import com.mymall.portal.dao.OmsOrderReturnApplyMapper;
import com.mymall.portal.domain.OmsOrderReturnApplyParam;
import com.mymall.portal.model.OmsOrderReturnApply;
import com.mymall.portal.service.OmsPortalOrderReturnApplyService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class OmsPortalOrderReturnApplyServiceImpl implements OmsPortalOrderReturnApplyService{

    @Autowired
    private OmsOrderReturnApplyMapper returnApplyMapper;

    @Override
    public int create(OmsOrderReturnApplyParam returnApply) {
        // ① 创建数据库实体对象（对应 oms_order_return_apply 表）
        OmsOrderReturnApply realApply = new OmsOrderReturnApply();

        // ② 把入参对象的属性拷贝到实体对象
        //    源：returnApply（前端传的 OmsOrderReturnApplyParam，DTO）
        //    目标：realApply（数据库实体 OmsOrderReturnApply，Entity）
        //    同名字段自动拷贝，不同名的忽略
        BeanUtils.copyProperties(returnApply, realApply);

        // ③ 后端补充字段：创建时间
        realApply.setCreateTime(new Date());

        // ④ 后端补充字段：退货申请状态
        //    0->待处理；1->退货中；2->已完成；3->已拒绝
        realApply.setStatus(0);

        // ⑤ 插入数据库，返回影响行数（成功为1）
        return returnApplyMapper.insert(realApply);
    }

}
