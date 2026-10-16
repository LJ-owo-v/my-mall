package com.mymall.common.dto;

import java.io.Serializable;
import java.util.List;

/**
 * 登录用户信息（存于 Sa-Token session 的 {@code adminInfo} / {@code memberInfo}）。
 *
 * <h3>包名变更历史（2026-10-16）</h3>
 * 本类原为「兼容桥接类」，包名刻意保持与老项目一致
 * （{@code com.mymall.common.dto.UserDto}）。原因：
 * 老项目把该对象存进 Sa-Token session 时 Jackson 开启了 default typing，
 * Redis 里的 JSON 形如：
 * <pre>
 *   "adminInfo":{"@class":"com.mymall.common.dto.UserDto","id":3,...}
 * </pre>
 * 若用自有包名反序列化会抛 ClassNotFoundException，读不到权限列表。
 *
 * <p><b>老服务已正式注销</b>（2026-10-16），旧的 {@code com.macro} 会话数据已清理，
 * 因此不再需要兼容包名，改为自有包名 {@code com.mymall.common.dto}。
 *
 * <p>⚠️ <b>注意</b>：Redis 中若仍残留 {@code "…":"com.mymall.common.dto.UserDto"}
 * 的旧会话，反序列化会失败（表现为登录态读取异常）。清理方式：
 * 删除 Redis 中 {@code Authorization:*:session:*} 键，让用户重新登录即可。
 *
 * <p>字段与老项目保持一致：id / username / clientId / permissionList。
 */
public class UserDto implements Serializable {

    private Long id;
    private String username;
    private String clientId;
    private List<String> permissionList;

    public UserDto() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public List<String> getPermissionList() { return permissionList; }
    public void setPermissionList(List<String> permissionList) { this.permissionList = permissionList; }

    @Override
    public String toString() {
        return "UserDto{id=" + id + ", username='" + username + "', clientId='" + clientId
                + "', permissionCount=" + (permissionList == null ? 0 : permissionList.size()) + '}';
    }
}