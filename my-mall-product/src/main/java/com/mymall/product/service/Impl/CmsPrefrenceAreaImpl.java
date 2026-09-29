package com.mymall.product.service.Impl;

import com.mymall.product.dao.CmsPrefrenceAreaMapper;
import com.mymall.product.model.CmsPrefrenceArea;
import com.mymall.product.model.CmsPrefrenceAreaExample;
import com.mymall.product.service.CmsPrefrenceAreaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CmsPrefrenceAreaImpl implements CmsPrefrenceAreaService {

    @Autowired
    private CmsPrefrenceAreaMapper prefrenceAreaMapper;

    @Override
    public List<CmsPrefrenceArea> listAll() {
        return prefrenceAreaMapper.selectByExample(new CmsPrefrenceAreaExample());
    }
}
