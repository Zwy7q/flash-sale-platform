import { createRouter, createWebHistory } from 'vue-router'
import { createAuthGuard } from './guards.js'
import { useAuthStore } from '../stores/auth.js'
import LoginView from '../views/LoginView.vue'
import RegisterView from '../views/RegisterView.vue'
import WelcomeView from '../views/WelcomeView.vue'
import SessionView from '../views/SessionView.vue'
import AccessView from '../views/AccessView.vue'
import MainLayout from '../layouts/MainLayout.vue'
import ProductListView from '../views/ProductListView.vue'
import ProductDetailView from '../views/ProductDetailView.vue'
import OrderListView from '../views/OrderListView.vue'
import ProductManageView from '../views/admin/ProductManageView.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/products' },
    { path: '/login', component: LoginView, meta: { public: true } },
    { path: '/register', component: RegisterView, meta: { public: true } },
    { path: '/session', component: SessionView, meta: { public: true } },
    {
      path: '/', component: MainLayout,
      children: [
        { path: 'welcome', component: WelcomeView },
        { path: 'products', component: ProductListView },
        { path: 'products/:id', component: ProductDetailView },
        { path: 'orders', component: OrderListView },
        // 15B 只提供身份确认页，不提前实现管理商品和统计业务。
        { path: 'admin', component: AccessView, meta: { admin: true } },
        { path: 'admin/products', component: ProductManageView, meta: { admin: true } },
        { path: 'forbidden', component: AccessView, meta: { denied: true } },
      ],
    },
    { path: '/:pathMatch(.*)*', component: AccessView, meta: { public: true, missing: true } },
  ],
})

// Pinia 在 main.js 安装后调用，避免在尚未创建 Pinia 时访问 Store。
export function installAuthGuard() {
  router.beforeEach(createAuthGuard(useAuthStore()))
}
