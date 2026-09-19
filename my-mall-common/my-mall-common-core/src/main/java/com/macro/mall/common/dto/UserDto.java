package com.macro.mall.common.dto;

import java.io.Serializable;
import java.util.List;

/**
 * ⚠️ 兼容桥接类 —— 包名与类名必须与原项目完全一致。
 *
 * 原因：原项目把该对象存进 Sa-Token session 时，Jackson 开启了 default typing，
 * Redis 里的 JSON 长这样：
 *   "adminInfo":{"@class":"com.macro.mall.common.dto.UserDto","id":3,...}
 * 如果我的工程用 com.mymall.* 包名，反序列化时会抛 InvalidTypeIdException / ClassNotFoundException，
 * 导致读不到当前用户的权限列表。
 *
 * 字段顺序与名称照抄原项目：id / username / clientId / permissionList
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