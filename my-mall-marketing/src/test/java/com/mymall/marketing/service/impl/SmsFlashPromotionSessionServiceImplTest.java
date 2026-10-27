package com.mymall.marketing.service.impl;

import com.mymall.marketing.dao.SmsFlashPromotionProductRelationDao;
import com.mymall.marketing.dao.SmsFlashPromotionSessionMapper;
import com.mymall.marketing.domain.SmsFlashPromotionSessionDetail;
import com.mymall.marketing.model.SmsFlashPromotionSession;
import com.mymall.marketing.model.SmsFlashPromotionSessionExample;
// 生产代码的包是 service.Impl（大写 I），测试目录统一小写 impl
import com.mymall.marketing.service.Impl.SmsFlashPromotionSessionServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SmsFlashPromotionSessionServiceImpl 纯单元测试。
 *
 * <p>重点验证"一次 GROUP BY 取全部场次商品数"的聚合结果能正确回填到每个场次，
 * 缺数据的场次必须补 0 而不是 null。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SmsFlashPromotionSessionServiceImpl")
class SmsFlashPromotionSessionServiceImplTest {

    @Mock private SmsFlashPromotionSessionMapper promotionSessionMapper;
    @Mock private SmsFlashPromotionProductRelationDao relationDao;

    @InjectMocks private SmsFlashPromotionSessionServiceImpl sessionService;

    @Test
    @DisplayName("selectList_按活动查场次_汇总各场次商品数")
    void selectList_按活动查场次_汇总各场次商品数() {
        when(promotionSessionMapper.selectByExample(any(SmsFlashPromotionSessionExample.class)))
                .thenReturn(List.of(session(1L, "08:00-10:00"), session(2L, "12:00-14:00")));
        // 一次 GROUP BY 返回的结果：场次 1 有 5 个商品，场次 2 没有数据
        List<Map<String, Object>> counts = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put("sessionId", 1L);
        row.put("productCount", 5L);
        counts.add(row);
        when(relationDao.countBySessionId(9L)).thenReturn(counts);

        List<SmsFlashPromotionSessionDetail> result = sessionService.selectList(9L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getProductCount()).isEqualTo(5L);
        // 聚合结果里没有的场次必须补 0，否则前端显示 null
        assertThat(result.get(1).getProductCount()).isZero();
        assertThat(result.get(0).getName()).isEqualTo("08:00-10:00");
    }

    @Test
    @DisplayName("selectList_活动不存在_返回空列表不抛异常")
    void selectList_活动不存在_返回空列表不抛异常() {
        when(promotionSessionMapper.selectByExample(any(SmsFlashPromotionSessionExample.class)))
                .thenReturn(new ArrayList<>());
        when(relationDao.countBySessionId(404L)).thenReturn(new ArrayList<>());

        assertThat(sessionService.selectList(404L)).isEmpty();
    }

    @Test
    @DisplayName("selectList_未传活动ID_直接返回空列表不查库")
    void selectList_未传活动ID_直接返回空列表不查库() {
        assertThat(sessionService.selectList(null)).isEmpty();
    }

    @Test
    @DisplayName("updateStatus_启用或停用_返回影响行数")
    void updateStatus_启用或停用_返回影响行数() {
        when(promotionSessionMapper.updateByPrimaryKeySelective(any(SmsFlashPromotionSession.class)))
                .thenReturn(1);

        int count = sessionService.updateStatus(3L, 0);

        ArgumentCaptor<SmsFlashPromotionSession> captor =
                ArgumentCaptor.forClass(SmsFlashPromotionSession.class);
        verify(promotionSessionMapper).updateByPrimaryKeySelective(captor.capture());
        assertThat(count).isEqualTo(1);
        assertThat(captor.getValue().getId()).isEqualTo(3L);
        assertThat(captor.getValue().getStatus()).isZero();
    }

    private SmsFlashPromotionSession session(Long id, String name) {
        SmsFlashPromotionSession session = new SmsFlashPromotionSession();
        session.setId(id);
        session.setName(name);
        session.setStatus(1);
        return session;
    }
}
