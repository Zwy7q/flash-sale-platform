# 15B：登录、会话与请求拦截教学

本阶段只实现账户和公共基础代码。下一阶段才开发商品展示与秒杀页面。

## 1. 三个工具各自做什么

- Vue Router 决定当前显示哪个页面。`/login` 是页面路径，`/api/auth/login` 是后端接口。
- Pinia 跨页面共享 Token、当前用户和是否已完成会话初始化。
- Axios 发 HTTP 请求，共享实例统一设置地址、超时和认证请求头。

main.js 安装 Pinia、Element Plus 和 Router；App.vue 中 RouterView 显示当前页面。
等待首次路由准备好再挂载，避免资料尚未恢复时闪现受保护页面。

## 2. 登录按代码走一遍

从 src/views/LoginView.vue 的 submit 方法开始读：

1. `reactive({ username: '', password: '' })` 创建响应式表单；`v-model` 双向关联输入框和字段。
2. `@submit.prevent="submit"` 阻止浏览器默认整页提交，调用我们的 JavaScript 方法。
3. `submitting` 是 ref，JavaScript 使用 `.value` 修改，模板自动取值。提交中按钮显示 loading，防止重复点击。
4. `await auth.login(...)` 等待 Store 的登录方法完成，浏览器仍可响应其他事件。
5. stores/auth.js 调用 api/auth.js 的 loginRequest，后者通过共享 http 发 POST。
6. 后端 BCrypt 校验密码，返回 `{token, user}`，user 不含密码，包含服务端计算的 isAdmin。
7. Store 把 token 放进 sessionStorage，把 user 放进内存；成功后跳转账户首页或登录前目标页面。

try 写成功流程，catch 写失败提示，finally 不论成功失败都恢复按钮。
用户名 trim 去首尾空格，密码不 trim，因为空格可能就是密码的一部分。
注册 POST /api/users 成功返回 201 和用户资料，不返回 JWT，所以还需要登录。
前端空值、长度和确认密码校验方便纠错，不代替后端校验；后端完整参数校验仍待完善。

## 3. Token 如何自动放进请求

阅读 src/utils/http.js：axios.create 的 baseURL 是公共路径 `/api`，timeout=10000 表示 10 秒。
API 方法写 `/users/me`，最终是 `/api/users/me`，开发时由 Vite 从 5173 转发至 8080。

`http.interceptors.request.use(...)` 注册请求发出前的函数，读取 sessionStorage 并设置：

```text
Authorization: Bearer <JWT>
```

Authorization 是认证请求头，Bearer 表示凭持有的令牌访问。签名、有效期和身份仍由后端验证。
登录/注册的 `{skipAuth: true}` 是我们添加的 Axios 配置标记：不携带旧 Token；不是请求体字段。
响应保留完整对象：response.status 是状态码，response.data 是响应体。
后续秒杀要区分 202 受理与 200 查到订单，因此不能只返回 data。

## 4. 为什么刷新后仍知道是谁

刷新会销毁 JavaScript 内存，Pinia 中的 user 丢失；当前标签页 sessionStorage 中的 Token 仍在。

```text
刷新 → Store 读 Token → 导航守卫调用 restoreSession
  → GET /api/users/me（附加 Bearer）→ 后端 JWT 验证
  → Controller 查询用户 → Store 保存资料 → 守卫允许进入页面
```

initialized 表示是否已完成初始化，不表示一定登录。没有 Token 时也可 initialized=true。
computed 是计算状态，根据 token 和 user 自动得到 isAuthenticated，根据 user.isAdmin 得到管理员展示状态。
restoration 让多个恢复调用共用一个 Promise，防止重复请求 /me。
sessionVersion 是会话版本号：退出或重新登录就变化，旧请求迟到时不得恢复旧身份。
http.js 的 sessionToken 同理，避免旧 Token 的迟到 401 清除新登录会话。
初读时先看正常流程，再回头理解这两个并发边界。

## 5. 页面守卫与后端权限不同

阅读 router/index.js 的 meta 和 router/guards.js 的 createAuthGuard：

1. public 页面直接放行。
2. 受保护页面先等恢复会话；未登录跳 /login。
3. 管理员页面且非管理员跳 /forbidden。
4. 返回 true 时允许导航。

前端只能控制页面体验，用户可直接发 HTTP，所以实际权限由后端 JWT 和管理员拦截器验证。
isAdmin 来自服务端，不在前端硬编码用户 ID，也不自行解 JWT 决定权限。
safeRedirect 限制回跳到站内路径，避免外部地址和登录页循环。

## 6. 错误如何区分

| 情况 | 行为 |
|---|---|
| 登录 401 | 页面提示用户名或密码错误，不触发公共退出 |
| 当前受保护请求 401 | 清会话并重新登录，并发错误只触发一次 |
| /me 404 | 用户不存在，清会话 |
| 403 | 无权限，保留会话 |
| 断网、超时、服务不可用 | 不认定 Token 失效；恢复页提供重试 |
| 点击退出 | 清本地 Token 和用户，跳登录 |

后端没有撤销接口，因此已复制的 JWT 在有效期内可能仍能用。
sessionStorage 是教学中的简化方案，仍要防止 XSS；其中不保存密码。

## 7. 如何观察与测试

先按 README.md 手动验收。F12 → Network 观察方法、路径、请求头、status 和响应体，不分享 Token。
Sources 可按顺序设断点：LoginView.submit → auth.login → loginRequest → http 请求拦截器。
刷新时看 restoreSession → currentUserRequest → /me 响应 → user 更新。
管理员响应 isAdmin=true，普通用户 false；登录与 /me 应一致。

前端 npm test 覆盖 10 项会话与请求行为，后端本次相关 23 项测试通过，npm run build 通过。
真实浏览器已验证空表单、错误密码、正确登录、注册重名、新用户注册登录、刷新、退出和普通用户权限。
无效/过期 Token 由后端 JWT 测试与前端 401 测试覆盖，未等待真实 Token 到期做浏览器测试。

理解这些流程后，15C 增加商品列表、详情、秒杀按钮和有上限的 requestId 轮询。
