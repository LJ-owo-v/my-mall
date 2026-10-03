package com.mymall.marketing.service.Impl;

import com.mymall.marketing.dao.SmsFlashPromotionProductRelationDao;
import com.mymall.marketing.dao.SmsFlashPromotionSessionMapper;
import com.mymall.marketing.domain.SmsFlashPromotionSessionDetail;
import com.mymall.marketing.model.SmsFlashPromotionSession;
import com.mymall.marketing.model.SmsFlashPromotionSessionExample;
import com.mymall.marketing.service.SmsFlashPromotionSessionService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


import java.util.*;


@Service
public class SmsFlashPromotionSessionServiceImpl implements SmsFlashPromotionSessionService {
    @Autowired
    private SmsFlashPromotionSessionMapper promotionSessionMapper;
    @Autowired
    private SmsFlashPromotionProductRelationDao relationDao;



    @Override
    public int create(SmsFlashPromotionSession promotionSession) {
        promotionSession.setCreateTime(new Date());
        return promotionSessionMapper.insert(promotionSession);
    }

    @Override
    public int update(Long id, SmsFlashPromotionSession promotionSession) {
        promotionSession.setId(id);
        return promotionSessionMapper.updateByPrimaryKey(promotionSession);
    }

    @Override
    public int updateStatus(Long id, Integer status) {
        SmsFlashPromotionSession promotionSession = new SmsFlashPromotionSession();
        promotionSession.setId(id);
        promotionSession.setStatus(status);
        return promotionSessionMapper.updateByPrimaryKeySelective(promotionSession);
    }

    @Override
    public int delete(Long id) {
        return promotionSessionMapper.deleteByPrimaryKey(id);
    }

    @Override
    public SmsFlashPromotionSession getItem(Long id) {
        return promotionSessionMapper.selectByPrimaryKey(id);
    }

    @Override
    public List<SmsFlashPromotionSession> list() {
        SmsFlashPromotionSessionExample example = new SmsFlashPromotionSessionExample();
        return promotionSessionMapper.selectByExample(example);
    }

    /**
     * 查询限时购场次列表（含每个场次的商品数量）。
     *
     * 优化点：替代原项目的 N+1 查询——
     * 原写法在循环里逐场次调 getCount()，5个场次=6次SQL；
     * 优化后用一次 GROUP BY 聚合查全部场次商品数，内存匹配，固定2次SQL。
     * 优点：SQL次数不随场次数增长，数据库压力小，响应更快，微服务下网络开销更低。
     */

    @Override
    public List<SmsFlashPromotionSessionDetail> selectList(Long flashPromotionId) {

        if(flashPromotionId == null){
            return Collections.emptyList();
        }
        // ① 查所有启用的场次
        SmsFlashPromotionSessionExample example = new SmsFlashPromotionSessionExample();
        example.createCriteria().andStatusEqualTo(1);
        List<SmsFlashPromotionSession> list = promotionSessionMapper.selectByExample(example);

        // ② 一次查出每个场次的商品数（GROUP BY）
        List<Map<String, Object>> countList = relationDao.countBySessionId(flashPromotionId);
        Map<Long, Long> countMap = new HashMap<>();
        for (Map<String, Object> row : countList) {
            countMap.put(
                    ((Number) row.get("sessionId")).longValue(),
                    ((Number) row.get("productCount")).longValue()
            );
        }

        // ③ 内存匹配，0次SQL
        List<SmsFlashPromotionSessionDetail> result = new ArrayList<>();
        for (SmsFlashPromotionSession session : list) {
            SmsFlashPromotionSessionDetail detail = new SmsFlashPromotionSessionDetail();
            BeanUtils.copyProperties(session, detail);
            detail.setProductCount(countMap.getOrDefault(session.getId(), 0L));
            result.add(detail);
        }
        return result;
    }

}
