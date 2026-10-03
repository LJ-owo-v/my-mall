package com.mymall.order.dao;

import com.mymall.order.model.OmsOrderOperateHistory;

import java.util.List;

public interface OmsOrderOperateHistoryDao {
    void insertList(List<OmsOrderOperateHistory> operateHistoryList);
}
