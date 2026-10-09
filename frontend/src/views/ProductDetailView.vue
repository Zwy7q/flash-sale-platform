<script setup>
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth.js'
import { productDetailRequest } from '../api/products.js'
import { createSeckillFlow } from '../composables/seckillFlow.js'
import { errorMessage } from '../utils/http.js'
import { money, localTime, orderStatus } from '../utils/display.js'

const route = useRoute()
const auth = useAuthStore()
const productId = route.params.id
const product = ref(null)
const loading = ref(false)
const error = ref('')
const controller = new AbortController()
// 解构得到的仍是 ref。模板自动读取，JavaScript 则用 .value。
const flow = createSeckillFlow(auth.user.id, productId)
const { phase, message, requestId, order, attempts, busy, canSubmit } = flow

async function loadProduct() {
  if (loading.value) { return }
  loading.value = true
  error.value = ''
  try {
    const response = await productDetailRequest(productId, controller.signal)
    if (!controller.signal.aborted) { product.value = response.data }
  } catch (exception) {
    if (controller.signal.aborted) { return }
    if (exception.response && exception.response.status === 404) {
      error.value = '商品不存在或已下架，请返回商品列表。'
    } else {
      error.value = errorMessage(exception, '商品详情加载失败，请重试。')
    }
  } finally {
    loading.value = false
  }
}
onMounted(loadProduct)
onBeforeUnmount(() => {
  controller.abort()
  flow.stop()
})
</script>

<template>
  <main class="catalog detail-page">
    <RouterLink class="text-link" to="/products">← 返回商品列表</RouterLink>
    <div v-if="loading" class="empty-panel" role="status">正在加载商品详情……</div>
    <div v-else-if="error" class="empty-panel"><p role="alert">{{ error }}</p><el-button @click="loadProduct">重新加载</el-button></div>
    <template v-else-if="product">
      <div class="detail-grid">
        <section class="detail-copy"><p class="eyebrow">限时秒杀 · 商品 {{ product.id }}</p>
          <h1>{{ product.name }}</h1><p class="product-description">{{ product.description || '暂无商品介绍。' }}</p>
          <p class="price">¥ {{ money(product.seckillPrice) }} <del>¥ {{ money(product.price) }}</del></p>
          <dl class="connection-details">
            <div><dt>活动开始</dt><dd>{{ localTime(product.seckillStartAt) }}</dd></div>
            <div><dt>活动结束</dt><dd>{{ localTime(product.seckillEndAt) }}</dd></div>
            <div><dt>普通库存</dt><dd>{{ product.stock }}</dd></div>
            <div><dt>秒杀库存参考</dt><dd>{{ product.seckillStock }}</dd></div>
          </dl>
          <p class="scope-note">展示库存仅供参考，实际可购买数量以提交结果为准。活动时间为北京时间。</p>
        </section>
        <section class="purchase-panel" aria-label="秒杀操作">
          <p class="eyebrow">准备好了吗</p><h2>参与本场秒杀</h2><p class="intro">点击后请等待结果，离开页面不会撤销已提交的请求。</p>
          <el-button class="submit-button" type="primary" :disabled="!canSubmit" :loading="busy" @click="flow.submit">立即秒杀</el-button>
          <p class="result-message" role="status" aria-live="polite">{{ message }}</p>
          <p v-if="phase === 'polling'" class="scope-note">查询进度 {{ attempts }} / 15</p>
          <p v-if="requestId" class="request-id">请求编号<br>{{ requestId }}</p>
          <el-button v-if="phase === 'unknown' && requestId" @click="flow.checkAgain">继续查询结果</el-button>
          <p v-if="phase === 'unknown' && !requestId" class="scope-note">暂时无法确认本次提交，请稍后核对订单，避免重复购买。</p>
          <div v-if="order" class="order-result">
            <h3>订单已创建</h3><dl class="connection-details">
              <div><dt>订单编号</dt><dd>{{ order.id }}</dd></div>
              <div><dt>商品编号</dt><dd>{{ order.productId }}</dd></div>
              <div><dt>订单金额</dt><dd>¥ {{ money(order.amount) }}</dd></div>
              <div><dt>订单状态</dt><dd>{{ orderStatus(order.status) }}</dd></div>
            </dl><p class="scope-note">订单列表、模拟支付与取消在下一阶段接入。</p>
          </div>
        </section>
      </div>
    </template>
  </main>
</template>
