# 秒杀平台 Vue 前端 · 15E

独立 Vue 工程，Spring Boot 在父目录。当前已完成账户、商品、秒杀、我的订单和管理员商品管理页面。
后续按父目录 FRONTEND_PLAN.md 完成统计看板。教学见 LEARNING_15B.md～LEARNING_15E.md。

## 启动

在 IDEA 运行后端启动类，使用 8080 端口。在 IDEA Terminal 中执行：

```powershell
cd D:/codexspace/codespace02/projects/flash-sale-platform/frontend
npm ci
npm run dev
```

首次获取工程或 lockfile 改变时执行 npm ci。依赖已安装且未变化时直接 npm run dev。
打开 http://localhost:5173/products，未登录先跳登录，成功后进入商品列表。
账户首页 /welcome 保留用户资料和“检查连接”。订单路径为 /orders，详情路径为 /products/商品ID。
15E 新增两个管理员 GET 接口，已运行的 IDEA 后端需要重启。
不要同时启动两份 Vite；5173 占用时先确认是否已有本工程运行。
官方 npm 源超时时可仅为当前安装指定镜像，不改全局配置：

```powershell
npm ci --registry=https://registry.npmmirror.com
```

## 工程文件的作用

| 文件 | 作用 |
|---|---|
| package.json | 依赖和 dev/build/preview 命令，类似 pom.xml 的部分职责 |
| package-lock.json | 锁定依赖实际版本，提交 Git |
| index.html | HTML 入口，包含 id=app 的容器 |
| src/main.js | 创建并挂载 Vue，类似启动入口，但运行在浏览器 |
| src/App.vue | 根组件，RouterView 根据当前路由展示页面 |
| src/style.css | 字体、颜色、布局、窄屏样式 |
| vite.config.js | Vue 编译插件、开发端口、/api 代理 |
| .env.development | 开发环境公开的 API 基础路径，不存密码 |
| node_modules/ | npm 安装的依赖，不提交 Git |
| dist/ | npm run build 生成的静态文件，不提交 Git |

## 一次请求如何经过前后端

```text
浏览器加载 localhost:5173
    ↓ index.html 加载 main.js
main.js 安装 Router / Pinia，挂载 App.vue
    ↓ 登录后账户首页手动检查连接
共享 Axios GET /api/health
    ↓ 浏览器访问 localhost:5173/api/health
Vite 转发到 localhost:8080/api/health
    ↓ HealthController 返回纯文本 OK
WelcomeView.vue 修改 ref，Vue 自动更新页面
```

健康检查公开，无需 JWT，不访问数据库或扣库存。
OK 仅表示此健康接口可访问，不代表所有基础设施都健康。

`<script setup>` 写 JavaScript 逻辑，`<template>` 写页面结构。
`ref` 变量在 JavaScript 中通过 `.value` 修改，模板 `{{ statusTitle }}` 自动读取。
`@click` 绑定事件，`:disabled` 绑定禁用状态，`v-if/v-else` 按条件显示内容。
`onMounted` 在组件显示后执行；`try/catch/finally` 处理成功、错误和统一收尾。
Axios 的 `await` 等待响应，不需要自己写复杂的 Promise 链。

公共请求实例在 utils/http.js，账户接口在 api/auth.js，会话在 stores/auth.js。
Router、Pinia、Element Plus 已注册。具体模块与流程见 LEARNING_15B.md。

## 自己验收

1. 未登录访问 /welcome，应跳登录页。空表单和错误密码应分别显示提示。
2. 使用自己已验证的账号登录，显示昵称和角色；刷新后 GET /api/users/me 恢复资料。
3. F12 → Network，受保护请求应带 Authorization: Bearer …；不要分享 Token。
4. 注册新普通用户，成功后登录；无管理员入口，直接访问 /admin 应进入无权限页。
5. 重复用户名注册应提示重名。退出后访问 /welcome，应重新要求登录。
6. 打开 /orders，确认能看到订单；待支付订单可模拟支付或取消，操作中按钮会禁用。
7. 已登录时停止后端并刷新，显示恢复失败页；重启后端并点击重试，恢复会话。
8. 账户首页“检查连接”调用公开 /api/health，200 + OK，只代表 Web 入口可访问。

/admin 是身份验证演示，管理员商品管理入口为 /admin/products。Token 存当前标签页 sessionStorage，密码不保存。
退出只清本地会话，不撤销服务器已签发的 JWT。

## 构建

```powershell
npm test
npm run build
```

成功生成 dist/。`npm run preview` 只预览静态产物，不等于生产部署。
当前 /api 代理只配置在开发服务器，生产需另外配置反向代理。
修改 .env.development 或 vite.config.js 后重启 npm run dev。

33 项前端测试集中于 src/auth.test.js、src/seckill.test.js、src/orders.test.js 和 src/admin.test.js。当前全量引入 Element Plus，构建可能提示 chunk 较大。
商品管理的表单原理和手工验收见 LEARNING_15E.md。理解并验收后进入 15F：统计看板与整体验收。Git 提交/推送由用户自行执行。
