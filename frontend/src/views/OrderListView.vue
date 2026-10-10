<script setup>
import { onMounted, onBeforeUnmount } from 'vue'
import { createOrderListFlow } from '../composables/ordersFlow.js'
import { money, localTime, orderStatus } from '../utils/display.js'

const flow = createOrderListFlow()
const { orders, loading, error, actionId, actionMessage } = flow

onMounted(() => flow.load())
onBeforeUnmount(() => flow.stop())

function isPending(order) {
  return flow.canPay(order)
}
</script>

<template>
  <main class="catalog orders-page">
    <div class="section-heading">
      <div><p class="eyebrow">订单中心</p><h1>我的订单</h1><p class="intro">订单状态和金额以服务端返回为准。</p></div>
      <el-button :loading="loading" @click="flow.load">刷新订单</el-button>
    </div>
    <el-alert v-if="actionMessage" :title="actionMessage" type="info" :closable="false" show-icon />
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <div v-if="loading && orders.length === 0" class="empty-panel" role="status">正在加载订单……</div>
    <div v-else-if="orders.length === 0" class="empty-panel">暂无订单。去商品列表看看限时秒杀吧。</div>
    <div v-else class="order-list">
      <article v-for="order in orders" :key="order.id" class="order-card">
        <div class="order-card-heading"><div><span class="order-label">订单 #{{ order.id }}</span><h2>商品 {{ order.productId }}</h2></div>
          <el-tag :type="order.status === 'PAID' ? 'success' : order.status === 'PENDING_PAYMENT' ? 'warning' : 'info'">{{ orderStatus(order.status) }}</el-tag>
        </div>
        <dl class="order-fields">
          <div><dt>请求编号</dt><dd>{{ order.requestId }}</dd></div>
          <div><dt>订单金额</dt><dd>¥ {{ money(order.amount) }}</dd></div>
          <div><dt>创建时间</dt><dd>{{ localTime(order.createdAt) }}</dd></div>
          <div><dt>更新时间</dt><dd>{{ localTime(order.updatedAt) }}</dd></div>
        </dl>
        <div v-if="isPending(order)" class="order-actions">
          <!-- 操作按钮只提交订单 ID；状态更新来自服务端响应。 -->
          <el-button type="primary" :loading="actionId === order.id" :disabled="actionId !== null" @click="flow.pay(order)">模拟支付</el-button>
          <el-button :loading="actionId === order.id" :disabled="actionId !== null" @click="flow.cancel(order)">取消订单</el-button>
        </div>
      </article>
    </div>
  </main>
</template>
