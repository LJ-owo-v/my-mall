package com.mymall.order.service.impl;

import com.github.pagehelper.PageHelper;
import com.mymall.order.dao.*;
import com.mymall.order.domain.OmsOrderDeliveryParam;
import com.mymall.order.domain.OmsOrderQueryParam;
import com.mymall.order.domain.OmsReceiverInfoParam;
import com.mymall.order.model.OmsOrder;
import com.mymall.order.model.OmsOrderExample;
import com.mymall.order.model.OmsOrderOperateHistory;
// 生产代码的包是 service.Impl（大写 I），测试目录统一小写 impl，包名不同必须显式 import
import com.mymall.order.service.Impl.OmsOrderServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OmsOrderServiceImpl（后台订单管理）纯单元测试。
 *
 * <p>逻辑简单，主要验证参数透传与操作历史落库 —— 后台的"谁改了什么"完全依赖这张表。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OmsOrderServiceImpl")
class OmsOrderServiceImplTest {

    @Mock private OmsOrderMapper orderMapper;
    @Mock private OmsOrderDao orderDao;
    @Mock private OmsOrderOperateHistoryDao orderOperateHistoryDao;
    @Mock private OmsOrderOperateHistoryMapper orderOperateHistoryMapper;
    @Mock private OmsOrderItemMapper orderItemMapper;

    @InjectMocks private OmsOrderServiceImpl orderService;

    @AfterEach
    void clearPageHelper() {
        // PageHelper 用 ThreadLocal 传分页参数，不清理会污染同线程的后续测试
        PageHelper.clearPage();
    }

    @Test
    @DisplayName("list_按条件分页查询_参数原样透传给DAO")
    void list_按条件分页查询_参数原样透传给DAO() {
        OmsOrderQueryParam queryParam = new OmsOrderQueryParam();
        queryParam.setOrderSn("SN202610270001");
        queryParam.setStatus(1);
        List<OmsOrder> expected = List.of(order(1L));
        when(orderDao.getList(queryParam)).thenReturn(expected);

        List<OmsOrder> result = orderService.list(queryParam, 10, 1);

        assertThat(result).isSameAs(expected);
        // 查询条件不能被 Service 层"加工"，否则后台筛选会与前端不一致
        verify(orderDao).getList(eq(queryParam));
    }

    @Test
    @DisplayName("delivery_批量发货_更新配送信息并写操作历史")
    void delivery_批量发货_更新配送信息并写操作历史() {
        List<OmsOrderDeliveryParam> params = List.of(deliveryParam(1L), deliveryParam(2L));
        when(orderDao.delivery(params)).thenReturn(2);

        int count = orderService.delivery(params);

        assertThat(count).isEqualTo(2);
        ArgumentCaptor<List<OmsOrderOperateHistory>> captor = ArgumentCaptor.forClass(List.class);
        verify(orderOperateHistoryDao).insertList(captor.capture());
        assertThat(captor.getValue()).hasSize(2).allSatisfy(history -> {
            assertThat(history.getOrderStatus()).isEqualTo(2);   // 2=已发货
            assertThat(history.getOperateMan()).isEqualTo("后台管理员");
            assertThat(history.getNote()).isEqualTo("完成发货");
        });
    }

    @Test
    @DisplayName("delivery_入参为空_直接返回0不写库")
    void delivery_入参为空_直接返回0不写库() {
        int count = orderService.delivery(new ArrayList<>());

        assertThat(count).isZero();
        verify(orderDao, never()).delivery(anyList());
    }

    @Test
    @DisplayName("close_状态不符影响行数为0_返回失败")
    void close_状态不符影响行数为0_返回失败() {
        // 条件更新 WHERE status=0 没命中（订单已是已支付/已关闭）
        when(orderMapper.updateByExampleSelective(any(OmsOrder.class), any(OmsOrderExample.class)))
                .thenReturn(0);

        int count = orderService.close(List.of(1L), "用户申请取消");

        assertThat(count).isZero();
        ArgumentCaptor<OmsOrder> captor = ArgumentCaptor.forClass(OmsOrder.class);
        verify(orderMapper).updateByExampleSelective(captor.capture(), any(OmsOrderExample.class));
        assertThat(captor.getValue().getStatus()).isEqualTo(4);   // 4=已关闭
        // 注意：即使影响行数为 0，当前实现仍然写了一条"订单关闭"操作历史
        verify(orderOperateHistoryDao).insertList(anyList());
    }


    @Test
    @DisplayName("detail_订单不存在_返回null不继续查子表")
    void detail_订单不存在_返回null不继续查子表() {
        when(orderDao.getDetail(404L)).thenReturn(null);

        assertThat(orderService.detail(404L)).isNull();
        verify(orderItemMapper, never()).selectByExample(any());
        verify(orderOperateHistoryMapper, never()).selectByExample(any());
    }

    @Test
    @DisplayName("updateReceiverInfo_更新收货信息并写操作历史")
    void updateReceiverInfo_更新收货信息并写操作历史() {
        when(orderMapper.updateByPrimaryKeySelective(any(OmsOrder.class))).thenReturn(1);

        OmsReceiverInfoParam param = new OmsReceiverInfoParam();
        param.setOrderId(1L);
        param.setReceiverName("李京");
        param.setReceiverPhone("13642567329");
        param.setReceiverDetailAddress("白云大道北 2 号");
        param.setStatus(2);

        int count = orderService.updateReceiverInfo(param);

        ArgumentCaptor<OmsOrder> captor = ArgumentCaptor.forClass(OmsOrder.class);
        verify(orderMapper).updateByPrimaryKeySelective(captor.capture());
        assertThat(count).isEqualTo(1);
        assertThat(captor.getValue().getId()).isEqualTo(1L);
        assertThat(captor.getValue().getReceiverName()).isEqualTo("李京");
        // 改收货信息必须留痕，否则售后无法追溯是谁改的
        ArgumentCaptor<OmsOrderOperateHistory> historyCaptor =
                ArgumentCaptor.forClass(OmsOrderOperateHistory.class);
        verify(orderOperateHistoryMapper).insert(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getNote()).isEqualTo("修改收货人信息");
    }

    @Test
    @DisplayName("delete_批量软删除_只改deleteStatus")
    void delete_批量软删除_只改deleteStatus() {
        when(orderMapper.updateByExampleSelective(any(OmsOrder.class), any(OmsOrderExample.class)))
                .thenReturn(2);

        int count = orderService.delete(List.of(1L, 2L));

        ArgumentCaptor<OmsOrder> captor = ArgumentCaptor.forClass(OmsOrder.class);
        verify(orderMapper).updateByExampleSelective(captor.capture(), any(OmsOrderExample.class));
        assertThat(count).isEqualTo(2);
        assertThat(captor.getValue().getDeleteStatus()).isEqualTo(1);
        verify(orderMapper, never()).deleteByPrimaryKey(any());
    }

    // ==================== 夹具 ====================

    private OmsOrder order(Long id) {
        OmsOrder order = new OmsOrder();
        order.setId(id);
        order.setOrderSn("SN202610270001");
        order.setStatus(1);
        return order;
    }

    private OmsOrderDeliveryParam deliveryParam(Long orderId) {
        OmsOrderDeliveryParam param = new OmsOrderDeliveryParam();
        param.setOrderId(orderId);
        param.setDeliveryCompany("顺丰");
        param.setDeliverySn("SF" + orderId);
        return param;
    }
}
