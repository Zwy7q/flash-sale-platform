<script setup>
import { computed, reactive } from 'vue'
import { productPayload, validateProduct } from '../utils/productForm.js'

// props 是父页面传入的数据。复制一份表单，不直接修改 props 或列表商品。
const props = defineProps({ initial: { type: Object, required: true },
  editing: Boolean, saving: Boolean, blocked: Boolean })
// emit 通知父页面“用户请求保存”，真正 HTTP 操作仍由父页面负责。
const emit = defineEmits(['save', 'close'])
const form = reactive(productPayload(props.initial))
const validation = reactive({ message: '' })
const activityStarted = computed(() => props.editing && new Date(props.initial.seckillStartAt).getTime() <= Date.now())

function submit() {
  validation.message = validateProduct(form)
  if (validation.message || props.saving || props.blocked) { return }
  emit('save', productPayload(form))
}
</script>

<template>
  <el-form class="product-form" label-position="top" @submit.prevent="submit">
    <el-alert v-if="validation.message" :title="validation.message" type="error" :closable="false" show-icon />
    <el-alert v-if="activityStarted" title="活动已经开始：秒杀库存和排期受限。名称、价格、普通库存等仍可编辑；保存结果以服务器校验为准。" type="warning" :closable="false" />
    <el-form-item label="商品名称"><el-input v-model="form.name" aria-label="商品名称" maxlength="128" :disabled="saving || blocked" /></el-form-item>
    <el-form-item label="商品描述"><el-input v-model="form.description" aria-label="商品描述" type="textarea" :rows="3" maxlength="512" :disabled="saving || blocked" /></el-form-item>
    <div class="form-grid">
      <el-form-item label="原价（元）"><el-input-number v-model="form.price" aria-label="原价" :min="0" :max="99999999.99" :precision="2" :step="1" :disabled="saving || blocked" /></el-form-item>
      <el-form-item label="秒杀价（元）"><el-input-number v-model="form.seckillPrice" aria-label="秒杀价" :min="0" :max="99999999.99" :precision="2" :step="1" :disabled="saving || blocked" /></el-form-item>
      <el-form-item label="普通库存"><el-input-number v-model="form.stock" aria-label="普通库存" :min="0" :max="2147483647" :precision="0" :disabled="saving || blocked" /></el-form-item>
      <el-form-item label="秒杀库存"><el-input-number v-model="form.seckillStock" aria-label="秒杀库存" :min="0" :max="2147483647" :precision="0" :disabled="saving || blocked" /></el-form-item>
    </div>
    <el-form-item label="上架状态"><el-radio-group v-model="form.status" :disabled="saving || blocked"><el-radio :value="0">下架</el-radio><el-radio :value="1">上架</el-radio></el-radio-group></el-form-item>
    <div class="form-grid">
      <!-- value-format 控制提交值，format 控制显示，不做 UTC 转换。 -->
      <el-form-item label="秒杀开始（北京时间）"><el-date-picker v-model="form.seckillStartAt" aria-label="秒杀开始时间" type="datetime" format="YYYY-MM-DD HH:mm:ss" value-format="YYYY-MM-DDTHH:mm:ss" :disabled="saving || blocked" /></el-form-item>
      <el-form-item label="秒杀结束（北京时间）"><el-date-picker v-model="form.seckillEndAt" aria-label="秒杀结束时间" type="datetime" format="YYYY-MM-DD HH:mm:ss" value-format="YYYY-MM-DDTHH:mm:ss" :disabled="saving || blocked" /></el-form-item>
    </div>
    <div class="form-actions"><el-button type="primary" native-type="submit" :loading="saving" :disabled="blocked">保存商品</el-button><el-button :disabled="saving" @click="emit('close')">关闭表单</el-button></div>
  </el-form>
</template>
