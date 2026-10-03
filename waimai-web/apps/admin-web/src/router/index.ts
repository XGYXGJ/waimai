import { createRouter, createWebHistory } from 'vue-router';
import { getToken, isTokenExpired, clearAuth } from '@waimai/shared';

const routes = [
  { path: '/login', component: () => import('@/pages/Login.vue') },
  { path: '/', component: () => import('@/pages/Dashboard.vue') },
  { path: '/merchants', component: () => import('@/pages/Merchants.vue') },
  { path: '/users', component: () => import('@/pages/Users.vue') },
  { path: '/riders', component: () => import('@/pages/Riders.vue') },
  { path: '/orders', component: () => import('@/pages/Orders.vue') },
  { path: '/reviews', component: () => import('@/pages/Reviews.vue') },
  { path: '/bid', component: () => import('@/pages/Bid.vue') },
  { path: '/config', component: () => import('@/pages/Config.vue') },
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
