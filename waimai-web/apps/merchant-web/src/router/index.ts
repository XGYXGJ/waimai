import { createRouter, createWebHistory } from 'vue-router';
import { getToken } from '@waimai/shared';

const routes = [
  { path: '/login', component: () => import('@/pages/Login.vue') },
  { path: '/', component: () => import('@/pages/Dashboard.vue') },
  { path: '/orders', component: () => import('@/pages/Orders.vue') },
  { path: '/dishes', component: () => import('@/pages/Dishes.vue') },
  { path: '/shop', component: () => import('@/pages/Shop.vue') },
  { path: '/coupons', component: () => import('@/pages/Coupons.vue') },
  { path: '/bid', component: () => import('@/pages/Bid.vue') },
  { path: '/forecast', component: () => import('@/pages/Forecast.vue') },
  { path: '/reviews', component: () => import('@/pages/Reviews.vue') },
];

const router = createRouter({ history: createWebHistory(), routes });

router.beforeEach((to) => {
  if (to.path !== '/login' && !getToken()) {
    return { path: '/login' };
  }
  return true;
});

export default router;
