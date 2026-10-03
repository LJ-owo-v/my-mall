package com.mymall.order.service.Impl;

import com.mymall.order.dao.OmsCompanyAddressMapper;
import com.mymall.order.model.OmsCompanyAddress;
import com.mymall.order.model.OmsCompanyAddressExample;
import com.mymall.order.service.OmsCompanyAddressService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OmsCompanyAddressServiceImpl implements OmsCompanyAddressService {
    @Autowired
    private OmsCompanyAddressMapper companyAddressMapper;
    @Override
    public List<OmsCompanyAddress> list() {
        return companyAddressMapper.selectByExample(new OmsCompanyAddressExample());
    }
}
