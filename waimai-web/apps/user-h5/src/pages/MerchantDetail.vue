<template>
  <div class="merchant-detail">
    <van-nav-bar :title="merchant.shopName || '商家详情'" left-arrow @click-left="$router.back()" />

    <div class="merchant-header" v-if="merchant.id">
      <div class="mh-main">
        <div class="mh-logo">{{ (merchant.shopName || '商')[0] }}</div>
        <div class="mh-info">
          <div class="mh-name">{{ merchant.shopName }}</div>
          <div class="mh-meta">⭐ {{ merchant.rating || 4.5 }} · 月售{{ merchant.monthlySales || 0 }}</div>
          <div class="mh-meta">{{ merchant.businessHours || '营业中' }}</div>
        </div>
      </div>
      <div class="mh-notice" v-if="merchant.notice">📢 {{ merchant.notice }}</div>
    </div>

    <!-- 菜品列表（按分类） -->
    <div class="dish-list" v-for="cat in dishCategories" :key="cat.id">
      <div class="cat-title">{{ cat.name }}</div>
      <div class="dish-item" v-for="d in dishesByCategory(cat.id)" :key="d.id">
        <div class="dish-img">{{ (d.name || '菜')[0] }}</div>
        <div class="dish-body">
          <div class="dish-name">{{ d.name }}</div>
          <div class="dish-desc">{{ d.description }}</div>
          <div class="dish-foot">
            <span class="price">{{ d.price }}</span>
            <van-stepper :model-value="cartQty(d.id)" min="0" @change="(v: number) => changeQty(d, v)" />
          </div>
        </div>
      </div>
    </div>

    <!-- 购物车栏 -->
    <div class="cart-bar" v-if="totalQty > 0">
      <div class="cart-info">
        <span class="cart-total">¥{{ totalAmount.toFixed(2) }}</span>
        <span class="cart-count">{{ totalQty }} 件</span>
      </div>
      <van-button type="primary" color="#ff6034" round @click="goCart">去结算</van-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { showToast } from 'vant';
import { apiMerchantDetail, apiCartAdd, apiCartUpdate } from '@/api';
import type { Dish } from '@waimai/shared';

const route = useRoute();
const router = useRouter();
const merchantId = Number(route.params.id);

const merchant = ref<any>({});
const dishCategories = ref<any[]>([]);
const dishes = ref<Dish[]>([]);
const cartMap = ref<Record<number, number>>({});

function dishesByCategory(catId: number) {
  return dishes.value.filter((d) => d.categoryId === catId);
}

function cartQty(dishId: number) {
  return cartMap.value[dishId] || 0;
}

const totalQty = computed(() => Object.values(cartMap.value).reduce((a, b) => a + b, 0));
const totalAmount = computed(() =>
  dishes.value.reduce((sum, d) => sum + (cartMap.value[d.id] || 0) * d.price, 0)
);

async function changeQty(d: Dish, v: number) {
  if (v > 0) {
    await apiCartAdd(d.id, v);
  } else {
    await apiCartUpdate(d.id, 0);
  }
  cartMap.value[d.id] = v;
}

function goCart() {
  router.push('/cart');
}

async function load() {
  try {
    const data: any = await apiMerchantDetail(merchantId);
    merchant.value = data.merchant || {};
    dishCategories.value = data.categories || [];
    dishes.value = data.dishes || [];
  } catch (e: any) {
    showToast(e.message);
  }
}

onMounted(load);
</script>

<style scoped>
.merchant-detail {
  padding-bottom: 70px;
}
.merchant-header {
  background: #fff;
  padding: 16px;
}
.mh-main {
  display: flex;
  gap: 12px;
}
.mh-logo {
  width: 60px;
  height: 60px;
  border-radius: 10px;
  background: #ff6034;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  font-weight: bold;
}
.mh-name {
  font-size: 18px;
  font-weight: 600;
}
.mh-meta {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}
.mh-notice {
  margin-top: 12px;
  padding: 8px 12px;
  background: #fff7f3;
  border-radius: 8px;
  font-size: 13px;
  color: #ff6034;
}
.cat-title {
  padding: 12px 16px 4px;
  font-weight: 600;
  font-size: 15px;
}
.dish-item {
  display: flex;
  gap: 12px;
  padding: 12px 16px;
  background: #fff;
}
.dish-img {
  width: 64px;
  height: 64px;
  border-radius: 8px;
  background: #f0f0f0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  color: #ff6034;
}
.dish-body {
  flex: 1;
}
.dish-name {
  font-weight: 600;
}
.dish-desc {
  font-size: 12px;
  color: #999;
  margin: 4px 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dish-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}
.cart-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: #333;
  color: #fff;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
}
.cart-total {
  color: #ff6034;
  font-size: 18px;
  font-weight: 600;
}
.cart-count {
  color: #999;
  font-size: 12px;
  margin-left: 8px;
}
</style>
