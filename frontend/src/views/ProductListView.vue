<script setup>
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { productListRequest } from '../api/products.js'
import { errorMessage } from '../utils/http.js'
import { money, localTime } from '../utils/display.js'

const products = ref([])
const loading = ref(false)
const error = ref('')
const controller = new AbortController()

async function loadProducts() {
  if (loading.value) { return }
  loading.value = true
  error.value = ''
  try {
    const response = await productListRequest(controller.signal)
    if (!controller.signal.aborted) { products.value = response.data }
  } catch (exception) {
    if (!controller.signal.aborted) { error.value = errorMessage(exception, '商品加载失败，请重试。') }
  } finally {
    loading.value = false
  }
}
// 页面挂载后查商品；离开页面时取消浏览器请求。
onMounted(loadProducts)
onBeforeUnmount(() => controller.abort())
</script>

<template>
  <main class="catalog">
    <div class="section-heading"><div><p class="eyebrow">限时精选</p><h1>发现好物，把握好价。</h1><p class="intro">选择商品查看活动详情，秒杀结果以订单查询为准。</p></div>
      <el-button :loading="loading" @click="loadProducts">刷新商品</el-button>
    </div>
    <div v-if="loading" class="empty-panel" role="status">正在加载商品……</div>
    <el-alert v-else-if="error" :title="error" type="error" :closable="false" show-icon />
    <div v-else-if="products.length === 0" class="empty-panel">暂无上架商品，稍后再来看看。</div>
    <div v-else class="product-grid">
      <!-- key 让 Vue 根据商品 ID 识别每张卡片，而不是根据列表位置。 -->
      <article v-for="product in products" :key="product.id" class="product-card">
        <div class="product-card-top"><span>限时秒杀</span><span>No. {{ product.id }}</span></div>
        <h2>{{ product.name }}</h2>
        <p class="price">¥ {{ money(product.seckillPrice) }} <del>¥ {{ money(product.price) }}</del></p>
        <p class="activity-time">开始 {{ localTime(product.seckillStartAt) }}<br>结束 {{ localTime(product.seckillEndAt) }}</p>
        <RouterLink class="detail-link" :to="'/products/' + product.id">查看活动详情 →</RouterLink>
      </article>
    </div>
  </main>
</template>
