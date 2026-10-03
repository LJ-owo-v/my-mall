package com.mymall.file.service;

import com.mymall.file.domain.OssCallbackResult;
import com.mymall.file.domain.OssPolicyResult;
import jakarta.servlet.http.HttpServletRequest;

public interface OssService {
    OssPolicyResult policy();

    OssCallbackResult callback(HttpServletRequest request);
}
