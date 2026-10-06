import { createRouter, createWebHistory } from 'vue-router';
import { hasValidSession, clearAuth } from '@waimai/shared';

const routes = [
  { path: '/login', component: () => import('@/pages/Login.vue') },
  { path: '/', component: () => import('@/pages/Hall.vue') },
  { path: '/delivering', component: () => import('@/pages/Delivering.vue') },
  { path: '/history', component: () => import('@/pages/History.vue') },
  { path: '/map/:orderId', component: () => import('@/pages/Map.vue') },
  { path: '/chat/:orderId', component: () => import('@/pages/Chat.vue') },
  { path: '/me', component: () => import('@/pages/Me.vue') },
];

const router = createRouter({ history: createWebHistory(), routes });

router.beforeEach((to) => {
  if (to.path === '/login') return true;
  if (!hasValidSession()) {
    clearAuth();
    return { path: '/login' };
  }
  return true;
});

export default router;
