# 阶段 15：Vue 前端架构与教学开发计划

> 执行方式：在当前对话中逐阶段开发；每阶段实现、验证、讲解后等待用户理解，再推进下一阶段。
> 2026-10-09：15A～15C 已实现；15D～15F 待逐步推进。助手不 commit/push。

**Goal:** 为现有秒杀后端建设可独立运行的 Vue 工程，完成用户购买流程和简易管理员管理/统计展示。

**Architecture:** 前后端分离，Vue 通过 HTTP 访问现有 Spring Boot API。前端作为同一仓库的独立工程，
有自己的 package.json、依赖、构建产物与开发服务器；不放进 Java resources/static，不让 Maven 编译 Vue。

**Tech Stack:** Vue 3、Vite、JavaScript、Vue Router、Pinia、Axios、Element Plus、npm。
第一版统计先做数字卡片和表格，暂不引入图表库。使用中文注释、async/await 和清晰的 if/else。

## 1. 工程放在哪里

推荐方案：同一 Git 仓库中的独立 `frontend/` 工程。

```text
D:/codexspace/codespace02/projects/flash-sale-platform/
├─ pom.xml                  后端 Maven 工程
├─ src/                     后端 Java 代码
├─ sql/                     数据库脚本
├─ docker-compose.yml       MySQL、Redis、RabbitMQ
├─ API.md                   已实现接口，以当前代码为准
├─ FRONTEND_PLAN.md         本计划
└─ frontend/                下一阶段新建的独立 Vue 工程
   ├─ package.json          前端依赖与 npm 命令
   ├─ package-lock.json     锁定实际安装版本，纳入 Git
   ├─ index.html            Vue 的 HTML 入口
   ├─ vite.config.js        开发端口、HTTP 代理
   ├─ .env.development      开发环境非敏感配置
   └─ src/                 前端代码
```

这已经是独立前端工程，“前后端分离”不要求必须建立第二个 Git 仓库。
如果放在旁边的 flash-sale-web/ 并建立独立仓库，也可行，但需维护两套提交/版本关系；
当前个人教学项目推荐同仓库。将 Vue 打包后直接塞进 Java 静态资源目录会混合交付流程，这次不采用。

文档放在仓库根目录，是因为现有 `.gitignore` 忽略了 docs/；API.md 和本计划可以正常进入你的提交。

## 2. 页面与路由

| 页面 | Vue 路由 | 使用的后端接口 | 访问条件 |
|---|---|---|---|
| 登录 | `/login` | POST /api/auth/login | 公开 |
| 注册 | `/register` | POST /api/users | 公开 |
| 商品列表 | `/products` | GET /api/products | 登录 |
| 商品详情/秒杀 | `/products/:id` | GET 商品详情、POST 秒杀、GET requestId 结果 | 登录 |
| 我的订单 | `/orders` | GET 订单列表/详情、POST 支付/取消 | 登录 |
| 商品管理 | `/admin/products` | 管理员商品读写接口 | 管理员 |
| 统计看板 | `/admin/analytics` | 管理员三个统计接口 | 管理员 |
| 未找到 | 其他路径 | 不调用后端 | 页面兜底 |

`/products/:id` 是浏览器页面路由，`/api/products/{id}` 是服务器数据接口，二者不要混淆。
根路径 `/` 根据登录状态跳转商品列表或登录页。
第一版订单详情用弹窗或页内区域，不另做复杂页面。管理员页面复用同一个 Vue 工程与登录会话。

## 3. 前端分层与职责

```text
Vue 页面 views/
    ↓ 调用业务 API 方法
api/auth.js、products.js、seckill.js、orders.js、admin.js
    ↓ 共享 Axios 实例
utils/http.js（地址、Token、超时、公共认证错误）
    ↓ HTTP
Vite /api 代理 → Spring Boot :8080 → 现有后端分层
```

规划目录：

```text
frontend/src/
├─ main.js                  创建 Vue，注册 Router、Pinia、Element Plus
├─ App.vue                  页面根组件
├─ router/index.js          路由表和登录/管理员导航守卫
├─ stores/auth.js           Token、当前用户、初始化和退出会话
├─ utils/http.js            统一 Axios 实例；保留完整响应的 status/data
├─ utils/display.js         金额、订单状态、可空统计值等展示转换
├─ api/
│  ├─ auth.js               登录、注册、当前用户
│  ├─ products.js           用户商品列表/详情
│  ├─ seckill.js            提交秒杀、查询异步结果
│  ├─ orders.js             我的订单、支付、取消
│  └─ admin.js              商品管理和统计
├─ layouts/MainLayout.vue   导航、当前用户、退出按钮
├─ views/
│  ├─ LoginView.vue
│  ├─ RegisterView.vue
│  ├─ ProductListView.vue
│  ├─ ProductDetailView.vue
│  ├─ OrderListView.vue
│  ├─ NotFoundView.vue
│  └─ admin/
│     ├─ ProductManageView.vue
│     └─ AnalyticsView.vue
├─ components/ProductForm.vue  新增/修改商品的公共表单
└─ assets/main.css          全局基础样式
```

页面只负责输入、展示和交互；API 文件集中维护 HTTP 方法和路径；Pinia 只保存跨页面共享的会话。
商品列表、表单、当前订单放在各自页面，不把所有数据都塞进全局 Store。
前端不访问 MySQL/Redis/RabbitMQ，不保存数据库密码或 JWT 签名密钥。

## 4. 环境与配置怎么做

已在当前机器只读检查到 Node.js v24.15.0、npm 11.12.1，无需先重复安装。
正式执行 15A 时再次检查，再由 Vite 生成工程；以实际安装包的 Node engines 要求为准。

下一阶段拟执行的命令（现在不执行）：

```powershell
cd D:/codexspace/codespace02/projects/flash-sale-platform
npm create vite@latest frontend -- --template vue
cd frontend
npm install
npm install vue-router@4 pinia axios element-plus
npm run dev
```

命令中的 vue 模板使用 JavaScript，暂不引入 TypeScript，减少初学阶段的额外概念。
安装后保留 package-lock.json 锁定具体版本，不手写猜测依赖版本。

计划中的 vite.config.js 核心配置：

```javascript
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    strictPort: true, // 端口被占用时明确报错，避免悄悄换端口造成困惑。
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // 不 rewrite：后端的路由本身就包含 /api。
      },
    },
  },
})
```

计划中的 .env.development：

```dotenv
VITE_API_BASE_URL=/api
```

Axios 使用该 baseURL，业务 API 方法写 `/products` 而不是 `/api/products`，避免拼成 `/api/api/products`。
创建实例的配置示例：

```javascript
import axios from 'axios'

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 10000,
})
```

这是设计示例，正式实现时补请求/响应拦截器与注释。统一工具不吞掉 HTTP status，页面能区分 200 和 202。
JWT 通过 Authorization 请求头传递，不依赖 Cookie，因此当前不需要 withCredentials。

开发时需要同时运行：

| 进程 | 地址/用途 | 启动方式 |
|---|---|---|
| Docker 基础设施 | MySQL/Redis/RabbitMQ | 沿用 docker compose |
| Spring Boot | localhost:8080，提供数据接口 | IDEA 启动 Java 主类 |
| Vite | localhost:5173，提供 Vue 页面 | frontend/ 内 npm run dev |

Vite 代理只在开发服务器中生效，不会被打包成生产代理。
部署时 Vue dist/ 由独立静态服务器提供，`/api` 通过反向代理转到 Spring Boot。
若启用 createWebHistory，生产服务器还需把前端页面路径回退到 index.html，API 不能回退到 HTML。
部署配置在阶段 16 整理，本阶段先实现本地前后端联调。

## 5. 登录与权限方案

- Token 暂存 sessionStorage，Pinia 保存当前会话；刷新页面可恢复，关闭当前标签页后不长期保留。
- 登录成功保存 Token 和 user，再跳到原目标页面或商品列表。
- 刷新时先读取 Token，再调用 GET /api/users/me 恢复用户；初始化完成后才判断路由权限，避免闪跳。
- 请求拦截器在需要登录的请求上附加 `Authorization: Bearer <token>`，公开登录/注册不发送过期 Token。
- 受保护接口 401 清理会话并跳登录，避免多个并发 401 重复弹窗；登录失败 401 只显示用户名或密码错误。
- 403 只提示无权限，不误当作 Token 过期。网络异常不自动删除 Token。
- 退出只是删除浏览器会话；当前后端没有 Token 撤销接口，不宣称退出会立即使已签发 JWT 失效。
- 前端路由守卫只改善体验，真实权限始终由后端拦截器决定；不能靠隐藏按钮保护接口。

**已补后端契约：** UserResponse 增加服务端计算的 `isAdmin: boolean`，登录、注册和 /me 均返回。
复用现有 app.admin-user-id 配置，不新增角色表，不让前端硬编码 user.id===1，也不由前端自行解 JWT 决定权限。
相关权限测试已通过，响应示例已更新到 API.md。

## 6. 两个前端衔接缺口

| 缺口 | 原因 | 计划补法 |
|---|---|---|
| 登录资料无管理员标识（已解决） | user.status 是启用状态，不是角色 | 15B 已补 isAdmin，由后端配置计算 |
| 无管理员商品读列表/详情 | 用户详情只返回上架商品，统计缺完整字段 | 15E 前补 GET /api/admin/products 与 GET /api/admin/products/{id} |

管理员商品读取必须查询 MySQL 最新数据，支持下架商品，并复用现有 JWT/管理员拦截器。
计划列表返回 Product[]，详情返回 Product；不存在 404，普通用户 403，无 Token 401。
完整修改表单不能用商品缓存中的旧库存覆盖 MySQL 最新库存；冲突 409 后刷新详情，再由用户确认。
即使拿到最新详情，也不消除并发修改风险，后端库存规则仍须执行。

本阶段不顺带增加图片上传、复杂 RBAC、真实支付、全量订单管理或大屏图表。

## 7. 教学阶段与验收点

### 15A：创建 Vue 工程，接通前后端 HTTP

文件：frontend/package.json、package-lock.json、index.html、vite.config.js、.env.development、src/main.js、src/App.vue；
根 .gitignore 增加 frontend/node_modules/、frontend/dist/ 和本地环境文件排除规则，保留可共享开发配置。

- [x] 检查 Node/npm 和 frontend/ 是否已存在，保留已有文件，不覆盖现有工程。
- [x] 生成 Vue JavaScript 工程，安装依赖，配置固定 5173 端口和 /api 代理。
- [x] 把模板页面收敛为教学欢迎页，通过浏览器 HTTP 请求 `/api/health`，显示 OK 或连接失败。
- [x] 真实代理请求 5173/api/health 返回 200 + OK；浏览器确认连接成功，停止后端后确认错误提示和按钮恢复。用户可按 frontend/README.md 在 Network 中复核。
- [x] frontend/ 执行 `npm run build` 通过；工程文件、请求流程和操作说明见 frontend/README.md。
- [ ] 用户验证和理解后才进入 15B；提交和推送由用户自行完成。

15A 不实现商品、订单、管理页面，也不把所有计划文件一次性生成为空壳。

### 15B：登录、注册与公共请求层

文件：src/utils/http.js、src/api/auth.js、src/stores/auth.js、src/router/index.js、LoginView.vue、RegisterView.vue、MainLayout.vue；
后端 UserResponse、AuthController/UserController 相关转换与必要的配置辅助代码。

- [x] 后端 isAdmin 覆盖管理员 true、普通用户 false、登录与 /me 一致，更新 API.md。
- [x] 建立 Axios、Pinia、Router；Token 按上节方案存取，页面使用普通 async/await 和 if/else。
- [x] 登录错误、注册重名、空表单分别提供提示，注册成功后引导登录，不假设注册返回 Token。
- [x] 刷新页面调用 /me，正确恢复登录；退出清理会话，受保护页面返回登录。
- [x] 检查 401/403 分流和不重复跳转；普通用户不能进入管理员页面。
- [x] 后端相关 23 项测试、前端 10 项测试与构建通过；浏览器验收密码、注册、刷新、退出与普通用户权限。无效/过期 Token 由后端 JWT 测试与前端 401 测试覆盖。
- [x] 会话恢复和 401 分流等关键行为集中于 src/auth.test.js。
- [x] 用户授权继续进入 15C。

15B 的 `/welcome` 是临时账户首页；`/admin` 只演示身份拦截，商品管理在 15E 实现。
断网恢复失败显示 `/session` 重试页，保留 Token。教学见 frontend/LEARNING_15B.md。

### 15C：商品展示与异步秒杀

文件：src/api/products.js、src/api/seckill.js、ProductListView.vue、ProductDetailView.vue。

- [x] 列表接 ProductSummary[]，详情单独请求，处理加载、空列表、404 和网络失败。
- [x] 点击秒杀期间禁用按钮；身份、价格和库存由后端决定，不发送 userId 或 amount。
- [x] 对 202 保存 requestId，每次请求结束后等待一秒再查、每轮最多 15 次；200 展示订单，202 继续查询。
- [x] PUBLISH_UNKNOWN/PUBLISH_FAILED 返回的编号也可查询；持续未知显示待确认，不自动重发 POST。未确认记录按用户/商品保存至 sessionStorage，刷新可恢复。
- [x] 离开页面清计时器并中断请求，避免重叠；409 售罄/重复购买、400 时间错误分别展示。
- [x] 浏览器实测真实秒杀→异步订单、重复购买、已结束活动、下架商品；Vitest 验证未知结果、卸载清理和重复点击。
- [x] 前端 21 项测试和构建通过；教学见 frontend/LEARNING_15C.md。
- [ ] 用户自行测试并理解本阶段后进入 15D。

15C 新增 composables/seckillFlow.js，集中管理提交与轮询；提前提供 utils/display.js，供 15D 复用。
根路径及默认登录落点进入 /products，保留 /welcome 账户页。订单操作页面仍在 15D 实现。

### 15D：我的订单、模拟支付与取消

文件：src/api/orders.js、src/utils/display.js、OrderListView.vue。

- [ ] 展示订单编号、商品 ID、金额、状态与时间，空数组显示“暂无订单”。
- [ ] 待支付订单提供模拟支付与取消；确认后调用 POST，单笔操作中禁用按钮。
- [ ] 成功后以返回订单或重新查询结果刷新，不能前端直接伪造 PAID/CANCELLED。
- [ ] 409 刷新当前状态；503 STOCK_RELEASE_PENDING 提示释放待确认，不承诺立即重购。
- [ ] 验收已支付重复点击、取消后重购、支付超时与当前用户隔离；不增加真实支付 SDK。
- [ ] `npm run build` 通过，解释前端展示状态与服务端状态的区别。

### 15E：管理员商品管理

文件：后端 AdminProductController、ProductService、ProductRepository 与对应测试；
前端 src/api/admin.js、views/admin/ProductManageView.vue、components/ProductForm.vue。

- [ ] 先补两个管理员读接口，测试上架/下架商品都可读、权限正确、缺失返回 404，更新 API.md。
- [ ] 管理列表读完整 MySQL 商品；新增/编辑复用一个表单，状态支持快捷上下架。
- [ ] 表单有名称、描述、价格、秒杀价、两类库存、状态、开始/结束时间，完整 PUT 不遗漏字段。
- [ ] 日期组件输出 `YYYY-MM-DDTHH:mm:ss` 本地时间，避免用 toISOString() 意外转 UTC。
- [ ] 活动开始后的秒杀库存/排期在 UI 提示受限；真正拒绝仍由后端 409 决定。
- [ ] 验收下架→仍可编辑→重新上架、库存修改冲突、普通用户 403、操作后数据刷新。
- [ ] 后端相关测试与 `npm run build` 通过后讲解组件 props、事件与表单双向绑定。

### 15F：统计看板与整体验收

文件：src/api/admin.js、views/admin/AnalyticsView.vue、src/utils/display.js。

- [ ] 展示库存、历史订单、支付金额、支付转化率、请求受理成功率，使用后端聚合结果。
- [ ] null 显示未知或不可用，不用 `value || 0` 抹掉未知状态；零保留为真实数字 0。
- [ ] 显示采集起点/生成时间，说明 202 受理与订单创建的区别；手动刷新，不高频自动轮询统计。
- [ ] 验收未预热库存、无请求、已支付/取消后的统计变化、Redis 故障显示。
- [ ] 完整浏览器验收注册→登录→商品→秒杀→订单→支付/取消，以及管理员商品→统计。
- [ ] 执行 `npm run build`、已加入的关键前端测试，以及受改动影响的后端测试。
- [ ] 更新启动说明，记录仍未实现的能力。用户自行提交后进入阶段 16。

## 8. 测试与推进原则

每阶段先说明目标和预期，再实现当前功能；新增代码写中文注释，讲解参数、返回值和请求流程。
简单样式通过浏览器检查，不写重复实现的测试；会话、轮询、状态转换使用集中测试覆盖关键边界。
后端接口改变必须增加或更新相应权限/行为测试，再更新 API.md。
不重置用户数据库，不清空 Redis 演示数据，不自动 commit/push，也不一次完成所有页面。

## 9. 当前下一步

先阅读 API.md 的通用约定、登录响应和秒杀结果查询结构。
15A～15C 已实现。先按 frontend/LEARNING_15C.md 验收并理解商品与秒杀流程。
用户理解后进入 **15D：我的订单、模拟支付与取消**。
阶段 16 仍为 JMeter 压测、部署/启动与架构文档、简历描述和面试讲解整理。
