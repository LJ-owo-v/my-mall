# 62 文件域（my-mall-file，8090）— 1 Controller / 2 端点（只用阿里云 OSS，不做 MinIO）

## 归属
- 无数据库、无 MyBatis、无 Redis（pom 里已排除 my-mall-common-redis）
- 依赖：com.aliyun.oss:aliyun-sdk-oss:3.17.4
- 类前缀：/aliyun/oss（GET /policy 直传签名、POST /callback 上传回调）

## ★★ 配置键必须与 OssServiceImpl 完全一致
aliyun.oss.endpoint / accessKeyId / accessKeySecret / bucketName / maxSize / policy.expire / dir.prefix / callback

- 是 aliyun.oss.dir.prefix，不是 policy.dir.prefix：写错会让容器启动时 Could not resolve placeholder → 起不来（实测踩到）
- 🔴 AccessKey/Secret 绝不写进 yml（本仓库是公开仓库）：用环境变量注入
  ALIYUN_OSS_ACCESSKEYID / ALIYUN_OSS_ACCESSKEYSECRET / ALIYUN_OSS_BUCKETNAME（Spring 宽松绑定自动识别）
- host 用 http:// 拼（与原实现一致，别改成 https，否则契约变化）

## 验收限制（诚实记录）
- /policy：只验返回字段集与老服务一致（accessKeyId,policy,signature,dir,host,callback）；值与 AK/桶/目录前缀相关，必然不同（配置差异）
- /callback：只验路由与参数绑定
- 成功上云路径未验证（需要真实桶 + AccessKey + 公网回调地址）