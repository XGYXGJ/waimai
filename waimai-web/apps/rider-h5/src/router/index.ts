import { createRouter, createWebHistory } from 'vue-router';
import { getToken, isTokenExpired, clearAuth } from '@waimai/shared';

const routes = [
  { path: '/login', component: () => import('@/pages/Login.vue') },
  { path: '/', component: () => import('@/pages/Hall.vue') },
  { path: '/delivering', component: () => import('@/pages/Delivering.vue') },
  { path: '/history', component: () => import('@/pages/History.vue') },
  { path: '/map/:orderId', component: () => import('@/pages/Map.vue') },
];

const router = createRouter({ history: createWebHistory(), routes });

router.beforeEach((to) => {
  if (to.path === '/login') return true;
  const token = getToken();
  if (!token || isTokenExpired(token)) {
    clearAuth();
    return { path: '/login' };
  }
  return true;
});

export default router;
