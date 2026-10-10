import { computed, ref } from 'vue'
import { adminProductsRequest, adminProductRequest, createProductRequest,
  updateProductRequest, productStatusRequest } from '../api/admin.js'
import { emptyProduct, productPayload, validateProduct } from '../utils/productForm.js'
import { errorMessage } from '../utils/http.js'

export function createAdminProductFlow() {
  const products = ref([])
  const loading = ref(false)
  const editing = ref(false)
  const saving = ref(false)
  const actionId = ref(null)
  const editorOpen = ref(false)
  const editId = ref(null)
  const draft = ref(emptyProduct())
  const formVersion = ref(0)
  const conflict = ref(false)
  const error = ref('')
  const formError = ref('')
  const message = ref('')
  const busy = computed(() => editing.value || saving.value || actionId.value !== null)
  const controller = new AbortController()
  let stopped = false

  async function load() {
    if (stopped || loading.value) { return }
    loading.value = true
    error.value = ''
    try {
      const response = await adminProductsRequest(controller.signal)
      if (!stopped) { products.value = response.data }
    } catch (exception) {
      if (!stopped) { error.value = errorMessage(exception, '管理商品加载失败，请重试。') }
    } finally { loading.value = false }
  }

  function create() {
    if (stopped || busy.value) { return }
    editId.value = null
    draft.value = emptyProduct()
    formVersion.value += 1
    formError.value = ''
    conflict.value = false
    editorOpen.value = true
  }

  async function edit(id) {
    if (stopped || busy.value) { return }
    editing.value = true
    formError.value = ''
    try {
      const response = await adminProductRequest(id, controller.signal)
      if (stopped) { return }
      editId.value = id
      draft.value = productPayload(response.data)
      // 改变版本让表单组件重新创建，本地输入不会混入上一个商品。
      formVersion.value += 1
      conflict.value = false
      editorOpen.value = true
    } catch (exception) {
      if (!stopped) {
        error.value = errorMessage(exception, '无法读取商品最新详情，可能已不存在或没有权限。')
        formError.value = error.value
      }
    } finally { editing.value = false }
  }

  function closeEditor() {
    if (busy.value) { return }
    editorOpen.value = false
  }

  async function save(value) {
    if (stopped || busy.value || conflict.value || !editorOpen.value) { return }
    formError.value = validateProduct(value)
    if (formError.value) { return }
    saving.value = true
    message.value = ''
    try {
      const payload = productPayload(value)
      if (editId.value === null) {
        await createProductRequest(payload, controller.signal)
      } else {
        await updateProductRequest(editId.value, payload, controller.signal)
      }
      if (stopped) { return }
      editorOpen.value = false
      message.value = '商品已保存。'
      await load()
    } catch (exception) {
      if (stopped) { return }
      const status = exception.response?.status
      if (status === 409) {
        conflict.value = true
        formError.value = errorMessage(exception, '库存或排期发生冲突。')
        await load()
        // 保留用户输入供对照，必须主动重新读取并确认，不自动覆盖或重试 PUT。
      } else {
        formError.value = errorMessage(exception, '保存未确认，请刷新核对后再操作，避免重复新增。')
      }
    } finally { saving.value = false }
  }

  async function changeStatus(product) {
    if (stopped || busy.value) { return }
    actionId.value = product.id
    message.value = ''
    try {
      let nextStatus = 1
      if (product.status === 1) { nextStatus = 0 }
      await productStatusRequest(product.id, nextStatus, controller.signal)
      if (!stopped) { message.value = '上下架状态已更新。'; await load() }
    } catch (exception) {
      if (!stopped) { error.value = errorMessage(exception, '操作未确认，请刷新列表核对状态。') }
    } finally { actionId.value = null }
  }

  function stop() {
    stopped = true
    controller.abort()
  }
  return { products, loading, editing, saving, actionId, busy, editorOpen, editId,
    draft, formVersion, conflict, error, formError, message, load, create, edit,
    closeEditor, save, changeStatus, stop }
}
