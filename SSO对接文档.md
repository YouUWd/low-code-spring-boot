# JDEC Platform SSO 单点登录对接文档

## 概述

本文档描述第三方系统接入 JDEC Platform SSO 单点登录服务的核心接口。

**服务端地址**: `http://{host}:{port}/api/config/sso`

---

## 1. 账号密码登录接口

### 接口信息
- **接口路径**: `/api/config/sso/doLogin`
- **请求方式**: `POST`
- **接口说明**: 提交手机号和验证码进行 SSO 登录认证

### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| phone | String | 是 | 用户手机号 |
| verifyCode | String | 是 | 验证码 |
| client | String | 是 | 应用编码（应用的 projectNo） |

### 请求示例

```http
POST /api/config/sso/doLogin
Content-Type: application/x-www-form-urlencoded

phone=13800138000&verifyCode=123456&client=YOUR_PROJECT_NO
```

### 响应说明

登录成功后，Sa-Token 会自动处理会话状态，后续可通过 `/getTicket` 或 `/getRedirectUrl` 获取凭证。

---

## 2. 获取重定向 URL 接口

### 接口信息
- **接口路径**: `/api/config/sso/getRedirectUrl`
- **请求方式**: `GET`
- **接口说明**: 已登录用户获取携带 ticket 的重定向地址，用于跨域单点登录

### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| client | String | 是 | 客户端标识（应用的 projectNo） |
| redirect | String | 是 | 客户端回调地址（需 URL 编码） |

### 请求示例

```http
GET /api/config/sso/getRedirectUrl?client=YOUR_PROJECT_NO&redirect=https%3A%2F%2Fyour-app.com%2Fcallback
```

### 响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": "https://your-app.com/callback?ticket=ST-xxx-xxxxx"
}
```

### 响应说明

- **未登录**: `data` 返回空字符串 `""`
- **已登录**: `data` 返回完整的重定向 URL，包含 ticket 参数
- 第三方系统收到此 URL 后，可直接重定向用户浏览器，或提取 ticket 进行后续验证

---

## 3. 获取 SSO Ticket 接口

### 接口信息
- **接口路径**: `/api/config/sso/getTicket`
- **请求方式**: `GET`
- **接口说明**: 已登录用户获取用于跨域认证的 ticket

### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| client | String | 是 | 客户端标识（应用的 projectNo） |

### 请求示例

```http
GET /api/config/sso/getTicket?client=YOUR_PROJECT_NO
```

### 响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": "ST-xxxxx-xxxxx-xxxxx"
}
```

### 响应说明

- **未登录**: `data` 返回空字符串 `""`
- **已登录**: `data` 返回 ticket 字符串
- 每次调用会删除旧 ticket 并生成新 ticket
- 第三方系统获取 ticket 后，需调用 Sa-Token 的 ticket 校验接口验证用户身份

---

## 对接流程示例

### 场景 1: 用户首次登录

1. 第三方系统引导用户访问 SSO 登录页
2. 用户在 SSO 登录页提交手机号和验证码
3. 调用 `/doLogin` 接口完成登录
4. 调用 `/getRedirectUrl` 获取带 ticket 的回调地址
5. 重定向到第三方系统回调地址
6. 第三方系统验证 ticket 并建立本地会话

### 场景 2: 已登录用户跨应用访问

1. 用户在 A 应用已登录
2. 访问 B 应用时，B 应用调用 `/getTicket` 获取 ticket
3. B 应用验证 ticket 后建立本地会话，实现单点登录

---

## 注意事项

1. **client 参数**: 必须使用正确的应用 `projectNo`，用于标识不同的接入应用
2. **URL 编码**: `redirect` 参数必须进行 URL 编码
3. **Ticket 时效**: ticket 具有时效性，获取后应立即验证使用
4. **安全性**: 生产环境必须使用 HTTPS 协议
5. **旧 ticket 清理**: 每次调用 `/getTicket` 会自动删除该 client 的旧 ticket

---

## 技术支持

如有对接问题，请联系 JDEC Platform 技术支持团队。
