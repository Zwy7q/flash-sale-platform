<script setup>
import { onMounted, onBeforeUnmount } from 'vue'
import { createAdminProductFlow } from '../../composables/adminProductsFlow.js'
import { money, localTime } from '../../utils/display.js'
import ProductForm from '../../components/ProductForm.vue'

const flow = createAdminProductFlow()
const { products, loading, editing, saving, actionId, busy, editorOpen, editId,
  draft, formVersion, conflict, error, formError, message } = flow
onMounted(flow.load)
onBeforeUnmount(flow.stop)
</script>

<template>
  <main class="catalog admin-products">
    <div class="section-heading"><div><p class="eyebrow">管理员工作台</p><h1>商品管理</h1><p class="intro">管理全部商品，包含下架商品。编辑前读取最新详情。</p></div>
      <div class="form-actions"><el-button :loading="loading" :disabled="busy" @click="flow.load">刷新列表</el-button><el-button type="primary" :disabled="busy" @click="flow.create">新增商品</el-button></div>
    </div>
    <el-alert v-if="message" :title="message" type="success" :closable="false" show-icon />
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <section v-if="editorOpen" class="product-editor" aria-label="商品编辑表单">
      <h2 v-if="editId === null">新增商品</h2><h2 v-else>编辑商品 #{{ editId }}</h2>
      <el-alert v-if="formError" :title="formError" type="error" :closable="false" show-icon />
      <div v-if="conflict" class="conflict-note"><p>保存被拒绝。重新读取会放弃当前输入，请核对最新库存和排期后再编辑。</p>
        <el-button :loading="editing" @click="flow.edit(editId)">放弃当前输入并读取最新详情</el-button>
      </div>
      <!-- key 变化时重建表单；save/close 是子组件向父页面发出的事件。 -->
      <ProductForm :key="formVersion" :initial="draft" :editing="editId !== null" :saving="saving || editing" :blocked="conflict" @save="flow.save" @close="flow.closeEditor" />
    </section>
    <div v-if="loading && products.length === 0" class="empty-panel">正在读取管理商品……</div>
    <div v-else-if="!error && products.length === 0" class="empty-panel">暂无商品，可以创建第一件商品。</div>
    <div v-else class="table-scroll">
      <table class="product-table"><thead><tr><th>商品</th><th>价格 / 秒杀价</th><th>普通 / 秒杀库存</th><th>秒杀排期</th><th>状态</th><th>操作</th></tr></thead>
        <tbody><tr v-for="product in products" :key="product.id">
          <td><span class="order-label">#{{ product.id }}</span><strong>{{ product.name }}</strong></td>
          <td>¥ {{ money(product.price) }}<br>¥ {{ money(product.seckillPrice) }}</td>
          <td>{{ product.stock }} / {{ product.seckillStock }}</td>
          <td>{{ localTime(product.seckillStartAt) }}<br>{{ localTime(product.seckillEndAt) }}</td>
          <td><el-tag v-if="product.status === 1" type="success">上架</el-tag><el-tag v-else type="info">下架</el-tag></td>
          <td><div class="table-actions"><el-button :disabled="busy" @click="flow.edit(product.id)">编辑</el-button>
            <el-button :loading="actionId === product.id" :disabled="busy" @click="flow.changeStatus(product)"><span v-if="product.status === 1">下架</span><span v-else>上架</span></el-button></div></td>
        </tr></tbody>
      </table>
    </div>
  </main>
</template>
