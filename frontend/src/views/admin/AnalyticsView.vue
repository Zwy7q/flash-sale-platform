<script setup>
import { onMounted, onBeforeUnmount } from 'vue'
import { createAnalyticsFlow } from '../../composables/analyticsFlow.js'
import { statisticNumber, statisticPercent, redisStockLabel, requestStatusLabel, utcTime, money } from '../../utils/display.js'

const flow = createAnalyticsFlow()
const { overview, products, loading, overviewError, productsError, detail,
  detailLoading, detailError, selectedId } = flow
// 进入页面读取一次，之后手动刷新，避免管理员看板持续给基础设施施压。
onMounted(flow.refresh)
onBeforeUnmount(flow.stop)
</script>

<template>
  <main class="catalog analytics-page">
    <div class="section-heading">
      <div><p class="eyebrow">管理员工作台</p><h1>统计看板</h1><p class="intro">库存、订单与秒杀请求统计 · 手动刷新</p></div>
      <el-button type="primary" :loading="loading" @click="flow.refresh">刷新统计</el-button>
    </div>
    <p class="scope-note">统计是跨 MySQL / Redis 的观察值，并非严格同时快照。当前库存不是活动初始投放量；202 受理不代表最终创建订单。</p>
    <div v-if="loading" class="empty-panel" role="status">正在读取统计，已有数据为上次读取结果……</div>
    <el-alert v-if="overviewError" :title="overviewError" type="error" :closable="false" show-icon />
    <section v-if="overview" aria-label="统计总览">
      <p class="statistics-time">总览生成：{{ utcTime(overview.generatedAt) }}（北京时间）</p>
      <div class="statistics-grid">
        <article class="stat-card"><span>商品 / 上架</span><strong>{{ overview.productCount }} / {{ overview.onSaleProductCount }}</strong></article>
        <article class="stat-card"><span>MySQL 普通库存</span><strong>{{ statisticNumber(overview.ordinaryStock) }}</strong></article>
        <article class="stat-card"><span>MySQL 秒杀库存</span><strong>{{ statisticNumber(overview.databaseSeckillStock) }}</strong></article>
        <article class="stat-card"><span>MySQL 当前总库存</span><strong>{{ statisticNumber(overview.totalStock) }}</strong></article>
        <article class="stat-card"><span>Redis 秒杀库存总和</span><strong>{{ statisticNumber(overview.redisSeckillStock) }}</strong><small>已知 {{ overview.knownRedisStockProductCount }} / {{ overview.productCount }} 件；有未知则总和未知</small></article>
        <article class="stat-card"><span>历史订单 / 已支付</span><strong>{{ overview.orders.createdOrderCount }} / {{ overview.orders.paidOrderCount }}</strong></article>
        <article class="stat-card"><span>已支付金额</span><strong>¥ {{ money(overview.orders.paidAmount) }}</strong></article>
        <article class="stat-card"><span>支付转化率</span><strong>{{ statisticPercent(overview.orders.paymentConversionRatePercent) }}</strong><small>已支付订单 / 历史创建订单</small></article>
      </div>
      <p class="statistics-time">待支付 {{ overview.orders.pendingPaymentOrderCount }} · 已取消 {{ overview.orders.cancelledOrderCount }} · 超时关闭 {{ overview.orders.paymentTimeoutOrderCount }}</p>
      <section class="stat-request" aria-label="请求采集统计">
        <h2>秒杀请求采集</h2>
        <p>{{ requestStatusLabel(overview.requests.status) }} · 采集起点：{{ utcTime(overview.requests.collectionStartedAt) }}（北京时间）</p>
        <p>已处理请求 {{ statisticNumber(overview.requests.requestCount) }} · 已受理 {{ statisticNumber(overview.requests.acceptedRequestCount) }} · 受理成功率 {{ statisticPercent(overview.requests.successRatePercent) }}</p>
        <p class="scope-note">仅统计采集启用后进入秒杀 Controller 的已处理请求；Redis 故障可能漏记，不能还原此前历史流量。</p>
      </section>
    </section>

    <h2>逐商品统计</h2>
    <el-alert v-if="productsError" :title="productsError" type="error" :closable="false" show-icon />
    <div v-else-if="!loading && products.length === 0" class="empty-panel">暂无商品统计。</div>
    <div v-if="products.length" class="table-scroll">
      <table class="product-table analytics-table"><thead><tr><th>商品 / 状态</th><th>MySQL 普通 / 秒杀 / 总库存</th><th>Redis 秒杀库存</th><th>历史订单 / 支付</th><th>支付金额 / 转化率</th><th>请求 / 受理 / 成功率</th><th>操作</th></tr></thead>
        <tbody><tr v-for="product in products" :key="product.productId">
          <td><span class="order-label">#{{ product.productId }}</span><strong>{{ product.name }}</strong><span v-if="product.status === 1">上架</span><span v-else>下架</span></td>
          <td>{{ product.ordinaryStock }} / {{ product.databaseSeckillStock }} / {{ product.totalStock }}</td>
          <td>{{ statisticNumber(product.redisSeckillStock) }}<br>{{ redisStockLabel(product.redisStockStatus) }}</td>
          <td>{{ product.orders.createdOrderCount }} / {{ product.orders.paidOrderCount }}</td>
          <td>¥ {{ money(product.orders.paidAmount) }}<br>{{ statisticPercent(product.orders.paymentConversionRatePercent) }}</td>
          <td>{{ statisticNumber(product.requests.requestCount) }} / {{ statisticNumber(product.requests.acceptedRequestCount) }}<br>{{ statisticPercent(product.requests.successRatePercent) }} · {{ requestStatusLabel(product.requests.status) }}</td>
          <td><el-button :disabled="loading" @click="flow.selectProduct(product.productId)">查看统计</el-button></td>
        </tr></tbody>
      </table>
    </div>
    <!-- 详情单独请求后端，包含采集起点及订单状态分布，不把列表当完整实时详情。 -->
    <section v-if="selectedId !== null" class="stat-request" aria-label="单商品统计">
      <div class="section-heading"><h2>商品 #{{ selectedId }} 统计详情</h2><el-button @click="flow.closeDetail">关闭详情</el-button></div>
      <p v-if="detailLoading" role="status">正在读取商品统计……</p>
      <el-alert v-if="detailError" :title="detailError" type="error" :closable="false" show-icon />
      <template v-if="detail">
        <h3>{{ detail.name }}</h3>
        <p>MySQL 普通库存 {{ detail.ordinaryStock }} · 秒杀库存 {{ detail.databaseSeckillStock }} · 总库存 {{ detail.totalStock }}</p>
        <p>Redis 秒杀库存 {{ statisticNumber(detail.redisSeckillStock) }} · {{ redisStockLabel(detail.redisStockStatus) }}</p>
        <p>历史订单 {{ detail.orders.createdOrderCount }} · 待支付 {{ detail.orders.pendingPaymentOrderCount }} · 已支付 {{ detail.orders.paidOrderCount }} · 已取消 {{ detail.orders.cancelledOrderCount }} · 超时关闭 {{ detail.orders.paymentTimeoutOrderCount }}</p>
        <p>支付金额 ¥ {{ money(detail.orders.paidAmount) }} · 支付转化率 {{ statisticPercent(detail.orders.paymentConversionRatePercent) }}</p>
        <p>请求 {{ statisticNumber(detail.requests.requestCount) }} · 受理 {{ statisticNumber(detail.requests.acceptedRequestCount) }} · 受理成功率 {{ statisticPercent(detail.requests.successRatePercent) }}</p>
        <p>{{ requestStatusLabel(detail.requests.status) }} · 采集起点 {{ utcTime(detail.requests.collectionStartedAt) }}（北京时间）</p>
      </template>
    </section>
  </main>
</template>
