# 15E：管理员商品管理教学与验收

本阶段完成 `/admin/products`：完整商品列表、新增、修改、快捷上下架。
先重启 IDEA 中的后端，因为这次增加了两个 Java GET 接口；前端仍在 frontend/ 中运行 `npm run dev`。
登录自己已经验证的管理员账号，从顶部“商品管理”进入；普通用户不会看到此入口。

## 1. 为什么增加管理员 GET 接口

用户列表只返回上架商品的摘要，不包含描述、库存等全部字段；用户详情也不能查询下架商品。
管理员需要编辑下架商品，所以新增：

| 方法与路径 | 返回值 |
|---|---|
| GET /api/admin/products | 所有上架和下架商品的完整数组 |
| GET /api/admin/products/{id} | 单个商品的完整数据，不存在为 404 |

请求依次经过 JWT 拦截器、管理员拦截器、AdminProductController、ProductService、ProductRepository、MySQL。
这两个读取方法直接查数据库，跳过普通用户商品缓存，避免将旧缓存当成编辑依据。
写入仍复用之前的新增、完整修改和上下架方法，也继续执行已有缓存失效与库存修改规则。

在 IDEA 中可在 AdminProductController.list、ProductService.listAdminProducts、
ProductRepository.findAllForAdmin 设置断点，观察列表从数据库返回到浏览器。
读取失败时先检查 Network 中的状态：401 是登录问题，403 是身份问题，404 是路径或商品问题。

## 2. 前端每层做什么

| 文件 | 职责 |
|---|---|
| src/api/admin.js | 集中维护管理员接口的 HTTP 方法和路径 |
| src/composables/adminProductsFlow.js | 管理列表、编辑、保存、冲突和请求取消 |
| src/views/admin/ProductManageView.vue | 将状态展示为表格、提示和操作按钮 |
| src/components/ProductForm.vue | 收集输入，检查表单，通知页面保存 |
| src/utils/productForm.js | 默认表单、字段白名单、基础输入校验 |

API 文件复用 Axios：自动带上已有 JWT，通过 Vite 将 `/api` 转发给后端。
Vue 不连接 MySQL、Redis 或 RabbitMQ。

## 3. 点击“编辑”后发生什么

1. `edit(id)` 发 GET 管理员商品详情，等待服务器返回。
2. `productPayload(response.data)` 复制九个业务字段，排除数据库 id。
3. 页面把这份数据作为 `initial` 传给 ProductForm。
4. 表单通过 `reactive(productPayload(props.initial))` 创建可修改的本地副本。
5. 用户修改输入时，`v-model="form.name"` 同时更新 form.name；其他字段同理。

`props` 是父组件传给子组件的数据；不要直接在表单里修改 props，也不要直接改列表对象。
复制后，用户尚未保存的输入不会提前改变表格中的商品。
`reactive` 让 Vue 跟踪对象字段变化，输入变了，相关页面显示也会更新。

页面的 `formVersion` 用作组件 `key`。重新读取或切换商品时版本加一，Vue 重建表单，
使其从新的 initial 开始，避免旧商品的输入残留在新商品表单里。
“关闭表单”仅关闭本地编辑区域，不向服务器保存。

读取最新详情减少旧数据问题，但不能阻止另一个管理员在读取后修改数据。
当前没有版本号或乐观锁，不应把这一流程理解为已解决全部并发编辑冲突。

## 4. 点击“保存商品”后发生什么

表单 `submit()` 先校验名称、金额、整数库存及时间先后；无效时提示，不发 HTTP。
通过后调用 `emit('save', productPayload(form))`。
`emit` 的意思是通知父组件“用户想保存这份数据”，它本身不请求服务器。
父页面接到 save 事件后调用业务流程里的 `save(value)`：

```javascript
// 没有商品 ID，说明正在新增；有 ID，说明正在修改已有商品。
if (editId.value === null) {
  await createProductRequest(payload, controller.signal)
} else {
  await updateProductRequest(editId.value, payload, controller.signal)
}
```

新增发送 POST；修改发送 PUT，ID 放路径里，九个业务字段放 JSON 请求体。
完整 PUT 要提交名称、描述、原价、秒杀价、普通库存、秒杀库存、状态、开始时间、结束时间。
只传 price 并不是目前接口支持的部分更新方式。

`saving` 在请求期间禁用保存按钮，防止连续点击；finally 中恢复状态。
保存成功关闭表单，再 GET 完整列表，用数据库结果刷新表格。
网络中断只表示结果未确认，不证明保存失败；先刷新核对，尤其不能盲目重复新增。
前端基础校验方便输入，不能代替后端安全校验；当前后端还没有完整 Bean Validation。

## 5. 日期格式为什么这样设置

`format="YYYY-MM-DD HH:mm:ss"` 是人看到的格式。
`value-format="YYYY-MM-DDTHH:mm:ss"` 是 v-model 保存、HTTP 提交的字符串格式。
例如提交 `2030-01-01T10:00:00`，对应后端 LocalDateTime。
本项目约定它是北京时间的本地日期时间，不调用 toISOString() 转成 UTC，避免排期偏移八小时。

## 6. 库存冲突和快捷上下架

活动已经开始后，修改秒杀库存或重新排期会被现有后端规则拒绝，返回 409。
页面提示原因，保留输入供对照，并禁用继续保存；不会自动再发 PUT。
点击“放弃当前输入并读取最新详情”才重新 GET、重建表单、解除保存限制。
此按钮会放弃当前草稿，先核对提示再点击。名称、价格和普通库存仍按后端现有规则处理。

快捷“上架/下架”走独立状态接口，请求体只有 `{ "status": 0 }` 或 `{ "status": 1 }`。
不会为了切换状态再提交一遍可能过时的库存和价格。
操作成功刷新完整列表，下架商品仍保留在管理员列表里。
列表秒杀库存是 MySQL 值，不等于 Redis 当前实时可抢库存。

路由守卫隐藏入口、阻止普通用户进入页面只是体验控制。
真实权限仍由后端验证，手动调用管理员 API 的普通用户得到 403。

## 7. 你如何自己测试

1. 重启 IDEA 后端，用管理员登录，访问 http://localhost:5173/admin/products。
2. 新增“学习测试商品”，原价199、秒杀价99、普通库存20、秒杀库存10，状态选择下架。
   排期选择将来的时间，结束晚于开始。保存后表格出现新商品及数据库 ID。
3. 编辑这件下架商品，修改名称、价格、普通库存和未来活动库存；保存后刷新确认结果。
4. 点“上架”，在用户商品列表确认可见；点“下架”，用户列表不再显示，管理员仍能编辑。
5. 用自己确认可修改的测试商品，将开始时间设为未来几分钟、结束为更晚，等待活动开始。
   再读取详情、修改秒杀库存并保存，应提示 409，不能保存；主动重读后恢复数据库原值。
   不要为了测试修改已有订单关联商品的排期，不要批量清空 Redis。
6. F12 → Network 检查五种请求：GET 列表、GET 详情、POST 新增、PUT 修改、PUT 状态。
7. 普通用户没有商品管理入口，直接访问 /admin/products 进入无权限页；管理员 GET API 仍返回 403。

可以用断点跟踪 `edit → productPayload → ProductForm.submit → save → updateProductRequest`。
先弄懂“输入只改本地，保存才发送 HTTP，成功后重新读取”这条主线，再看冲突与请求取消。

## 8. 本阶段验证与下一步

前端 `npm test`：33 项通过；`npm run build` 通过。
后端相关 34 项通过（管理员 Controller、ProductService、管理员权限集成测试）。
浏览器已验证新增、下架商品编辑、重新上架、受限库存修改和主动重读。
Element Plus 全量引入仍有构建体积提示，暂未做按需拆包。

下一步是 15F：管理员统计看板，展示现有统计接口返回的库存、订单、支付金额和受理成功率，
区分真实零与未知值，并做整体联调验收。本阶段不提前实现统计页面。
Git commit/push 由你测试、理解后自行执行。
