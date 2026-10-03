package com.mymall.marketing.service.Impl;

import com.alibaba.nacos.common.utils.CollectionUtils;
import com.github.pagehelper.PageHelper;
import com.mymall.marketing.dao.SmsFlashPromotionProductRelationDao;
import com.mymall.marketing.dao.SmsFlashPromotionProductRelationMapper;
import com.mymall.marketing.domain.SmsFlashPromotionProduct;
import com.mymall.marketing.model.SmsFlashPromotionProductRelation;
import com.mymall.marketing.service.SmsFlashPromotionProductRelationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SmsFlashPromotionProductRelationServiceImpl implements SmsFlashPromotionProductRelationService {
    @Autowired
    private SmsFlashPromotionProductRelationMapper relationMapper;

    @Autowired
    private SmsFlashPromotionProductRelationDao relationDao;

    /**
     * 批量添加限时购商品关联。
     *
     * 优化点：替代原项目的循环单条插入——
     * 原写法 for循环里逐条 insert，N条记录=N次SQL；
     * 优化后用 <foreach> 批量插入，一次SQL写完。
     * 优点：减少网络往返次数，数据库压力小，插入速度快，
     *       数据量越大性能差距越明显（100条从100次SQL→1次）。
     */

    @Override
    public int create(List<SmsFlashPromotionProductRelation> relationList) {
        if (CollectionUtils.isEmpty(relationList)) {
            return 0;
        }
        relationDao.insertList(relationList);
        return relationList.size();
    }


    @Override
    public int update(Long id, SmsFlashPromotionProductRelation relation) {
        relation.setId(id);
        return relationMapper.updateByPrimaryKey(relation);
    }

    @Override
    public int delete(Long id) {
        return relationMapper.deleteByPrimaryKey(id);
    }

    @Override
    public SmsFlashPromotionProductRelation getItem(Long id) {
        return relationMapper.selectByPrimaryKey(id);
    }

    @Override
    public List<SmsFlashPromotionProduct> list(Long flashPromotionId, Long flashPromotionSessionId, Integer pageSize, Integer pageNum) {
        PageHelper.startPage(pageNum,pageSize);
        return relationDao.getList(flashPromotionId,flashPromotionSessionId);
    }
}
