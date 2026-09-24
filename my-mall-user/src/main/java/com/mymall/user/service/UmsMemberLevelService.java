package com.mymall.user.service;

import com.mymall.user.model.UmsMemberLevel;

import java.util.List;

public interface UmsMemberLevelService {
    List<UmsMemberLevel> list(Integer defaultStatus);
}
