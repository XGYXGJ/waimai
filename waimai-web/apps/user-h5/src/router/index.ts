import { createRouter, createWebHistory } from 'vue-router';
import { getToken, isTokenExpired, clearAuth } from '@waimai/shared';

const routes = [
  { path: '/login', component: () => import('@/pages/Login.vue') },
  { path: '/', component: () => import('@/pages/Home.vue') },
  { path: '/search', component: () => import('@/pages/Search.vue') },
  { path: '/merchant/:id', component: () => import('@/pages/MerchantDetail.vue') },
  { path: '/cart', component: () => import('@/pages/Cart.vue') },
  { path: '/pay/:orderId', component: () => import('@/pages/Pay.vue') },
  { path: '/orders', component: () => import('@/pages/Orders.vue') },
  { path: '/orders/:id', component: () => import('@/pages/OrderDetail.vue') },
  { path: '/orders/:id/track', component: () => import('@/pages/Track.vue') },
  { path: '/orders/:id/review', component: () => import('@/pages/Review.vue') },
  { path: '/chat', component: () => import('@/pages/Chat.vue') },
  { path: '/profile', component: () => import('@/pages/Profile.vue') },
  { path: '/address', component: () => import('@/pages/Address.vue') },
  { path: '/coupons', component: () => import('@/pages/Coupons.vue') },
  { path: '/coupon-hall', component: () => import('@/pages/CouponHall.vue') },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

const publicPaths = ['/login'];

router.beforeEach((to) => {
  if (publicPaths.includes(to.path)) return true;
  const token = getToken();
  if (!token || isTokenExpired(token)) {
    clearAuth();
    return { path: '/login', query: { redirect: to.fullPath } };
  }
  return true;
});

export default router;
