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
          <div class="mh-meta">
            起送 ¥{{ Number(merchant.minOrderAmount || 0).toFixed(2) }}
            · 配送费起 ¥{{ Number(merchant.deliveryFee || 0).toFixed(2) }}
            <template v-if="merchant.deliveryRadiusKm"> · 配送范围 {{ merchant.deliveryRadiusKm }}km</template>
          </div>
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
            <div class="dish-price-wrap">
              <span class="price">¥{{ d.price }}</span>
              <span v-if="d.originalPrice > d.price" class="origin-price">¥{{ d.originalPrice }}</span>
              <van-tag v-if="d.originalPrice > d.price" type="danger" plain size="mini">{{ discountLabel(d) }}</van-tag>
            </div>
            <van-stepper
              :model-value="cartQty(d.id)"
              :min="0"
              :max="maxQty(d)"
              :disabled="merchant.openStatus !== 1"
              @change="(v: any) => changeQty(d, Number(v))"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- 购物车里是别家的菜：给出明确入口，不静默丢弃 -->
    <div class="other-shop-tip" v-if="otherShopCart">
      <span>购物车中还有「{{ otherShopCart.merchantName || '其他商家' }}」的 {{ otherShopCart.count }} 件商品</span>
      <van-button size="mini" type="primary" color="#ff6034" @click="clearOtherShop">清空并换店</van-button>
    </div>

    <!-- 用户评价 -->
    <div class="review-section" v-if="reviews.records?.length">
      <div class="review-header">
        <span class="review-title">用户评价</span>
        <span class="review-score">⭐ {{ reviews.avgRating || 0 }} <span class="review-sub">({{ reviews.total || 0 }}条 · 好评率{{ reviews.goodRate || 0 }}%)</span></span>
      </div>
      <div class="review-item" v-for="r in reviews.records" :key="r.id">
        <div class="review-top">
          <van-rate :model-value="r.rating" readonly :size="12" color="#ff6034" />
          <span class="review-time">{{ (r.createdAt || '').slice(0, 10) }}</span>
        </div>
        <div class="review-content">{{ r.content }}</div>
        <div class="review-reply" v-if="r.reply">商家回复：{{ r.reply }}</div>
      </div>
    </div>

    <!-- 购物车栏 -->
    <div class="cart-bar" v-if="totalQty > 0">
      <div class="cart-info">
        <span class="cart-total">¥{{ totalAmount.toFixed(2) }}</span>
        <span class="cart-count">{{ totalQty }} 件</span>
        <span class="cart-gap" v-if="gapToMin > 0">还差 ¥{{ gapToMin.toFixed(2) }} 起送</span>
      </div>
      <van-button
        type="primary"
        color="#ff6034"
        round
        :disabled="gapToMin > 0"
        @click="goCart"
      >{{ gapToMin > 0 ? '未达起送价' : '去结算' }}</van-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { showToast, showConfirmDialog } from 'vant';
import {
  apiMerchantDetail, apiCartUpdate, apiCartList, apiCartClear, apiMerchantReviews,
} from '@/api';
import type { Dish } from '@waimai/shared';

const route = useRoute();
const router = useRouter();
const merchantId = Number(route.params.id);

const merchant = ref<any>({});
const dishCategories = ref<any[]>([]);
const dishes = ref<Dish[]>([]);
const reviews = ref<any>({ records: [], avgRating: 0, total: 0, goodRate: 0 });

/**
 * 购物车数量。必须是「进页面时从后端拉回来」的，不能只靠点 stepper 累积——
 * 旧实现从不调用 apiCartList()，所以从确认订单页返回时组件重建、cartMap 归零，
 * 底部购物车栏（v-if="totalQty > 0"）就凭空消失了。
 */
const cartMap = ref<Record<number, number>>({});
/** 购物车里是别家商家的菜时，把信息留住给用户一个明确的选择入口 */
const otherShopCart = ref<{ merchantId: number; merchantName?: string; count: number } | null>(null);
/** 防抖：同一次操作未完成前不允许再改，避免连点产生竞态 */
const pending = ref<Record<number, boolean>>({});

const MAX_QTY = 99;

function discountLabel(d: any) {
  if (!d.originalPrice || d.originalPrice <= 0) return '';
  const pct = Math.round((d.price / d.originalPrice) * 100) / 10;
  return pct + '折';
}

function dishesByCategory(catId: number) {
  return dishes.value.filter((d) => d.categoryId === catId);
}

function cartQty(dishId: number) {
  return cartMap.value[dishId] || 0;
}

/** 单个菜品的可选上限：库存、全局上限二者取小 */
function maxQty(d: Dish) {
  const stock = Number(d.stock ?? MAX_QTY);
  if (!Number.isFinite(stock) || stock <= 0) return MAX_QTY;
  return Math.min(stock, MAX_QTY);
}

const totalQty = computed(() => Object.values(cartMap.value).reduce((a, b) => a + b, 0));
const totalAmount = computed(() =>
  dishes.value.reduce((sum, d) => sum + cartQty(d.id) * Number(d.price), 0)
);
const minOrderAmount = computed(() => Number(merchant.value.minOrderAmount || 0));
const gapToMin = computed(() => Math.max(0, minOrderAmount.value - totalAmount.value));

function setQty(dishId: number, qty: number) {
  const next = { ...cartMap.value };
  if (qty > 0) next[dishId] = qty;
  else delete next[dishId];
  cartMap.value = next;
}

/**
 * stepper 给的是「目标数量」（绝对值），所以必须走 /cart/update（set 语义）。
 * 旧代码走的是 /cart/add（增量语义）：从 1 点到 2 会变成后端 1+2=3，
 * 页面显示 2、下单却是 3，金额凭空多出来。
 */
async function changeQty(d: Dish, v: number) {
  if (pending.value[d.id]) return;
  const qty = Math.max(0, Math.min(Number(v) || 0, maxQty(d)));
  const old = cartQty(d.id);
  if (qty === old) return;

  pending.value = { ...pending.value, [d.id]: true };
  setQty(d.id, qty); // 乐观更新，点下去立刻有反馈
  try {
    await apiCartUpdate(d.id, qty);
    otherShopCart.value = null;
  } catch (e: any) {
    setQty(d.id, old); // 失败回滚，避免显示和实际不一致
    const msg = e?.message || '操作失败';
    showToast(msg);
    if (msg.includes('其他商家')) await offerSwitchShop();
  } finally {
    const p = { ...pending.value };
    delete p[d.id];
    pending.value = p;
  }
}

/** 购物车属于其他商家：问一下要不要清空换店（绝不静默清空用户的东西） */
async function offerSwitchShop() {
  try {
    await showConfirmDialog({
      title: '更换商家',
      message: `购物车中还有「${otherShopCart.value?.merchantName || '其他商家'}」的商品，切换商家需要先清空购物车。`,
      confirmButtonText: '清空并继续',
      cancelButtonText: '再想想',
    });
  } catch {
    return; // 用户点了取消
  }
  await clearOtherShop();
}

async function clearOtherShop() {
  try {
    await apiCartClear();
    otherShopCart.value = null;
    cartMap.value = {};
    showToast('已清空购物车');
  } catch (e: any) {
    showToast(e.message || '清空失败');
  }
}

function goCart() {
  if (gapToMin.value > 0) {
    showToast(`还差 ¥${gapToMin.value.toFixed(2)} 起送`);
    return;
  }
  router.push('/cart');
}

/** 把后端的购物车同步进页面（进页面 / 返回页面时都要调） */
async function loadCart() {
  const cart: any = await apiCartList();
  const records: any[] = cart?.records || [];
  if (!records.length) {
    cartMap.value = {};
    otherShopCart.value = null;
    return;
  }
  const cartMerchantId = Number(cart.merchantId ?? records[0].merchantId);
  if (cartMerchantId !== merchantId) {
    // 别家的菜：不往 stepper 上填，也不偷偷删，交给用户决定
    otherShopCart.value = {
      merchantId: cartMerchantId,
      merchantName: cart.merchantName,
      count: records.reduce((s, r) => s + Number(r.quantity || 0), 0),
    };
    cartMap.value = {};
    return;
  }
  otherShopCart.value = null;
  const next: Record<number, number> = {};
  for (const r of records) {
    const dishId = Number(r.dishId);
    if (!dishes.value.some((d) => d.id === dishId)) {
      apiCartUpdate(dishId, 0).catch(() => {}); // 菜品已下架，顺手清出购物车
      continue;
    }
    next[dishId] = Number(r.quantity);
  }
  cartMap.value = next;
}

async function load() {
  try {
    const data: any = await apiMerchantDetail(merchantId);
    // 后端 /merchant/{id} 返回的是「扁平」的商家 VO（categories/dishes 平铺在同一层），
    // 不是 { merchant: {...} }。写成 data.merchant 时永远是 undefined，
    // 导致店名/评分/营业时间/公告整块不渲染。
    merchant.value = data.merchant || data || {};
    dishCategories.value = data.categories || [];
    dishes.value = data.dishes || [];
  } catch (e: any) {
    showToast(e.message);
  }
  // 必须先拿到 dishes 再同步购物车（要按菜品是否存在做校验）
  try {
    await loadCart();
  } catch {}
  try {
    const r: any = await apiMerchantReviews(merchantId);
    reviews.value = r || { records: [], avgRating: 0, total: 0, goodRate: 0 };
  } catch {}
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
.dish-price-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
}
.origin-price {
  font-size: 12px;
  color: #999;
  text-decoration: line-through;
}
.review-section {
  background: #fff;
  margin-top: 12px;
  padding: 12px 16px;
}
.review-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.review-title {
  font-weight: 600;
  font-size: 15px;
}
.review-score {
  color: #ff6034;
  font-weight: 600;
}
.review-sub {
  font-size: 12px;
  color: #999;
  font-weight: normal;
}
.review-item {
  padding: 10px 0;
  border-bottom: 1px solid #f0f0f0;
}
.review-item:last-child {
  border-bottom: none;
}
.review-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.review-time {
  font-size: 12px;
  color: #999;
}
.review-content {
  font-size: 14px;
  color: #333;
  margin-top: 6px;
}
.review-reply {
  font-size: 12px;
  color: #ff6034;
  margin-top: 6px;
  padding: 6px 8px;
  background: #fff7f3;
  border-radius: 6px;
}
.other-shop-tip {
  margin: 12px 16px;
  padding: 10px 12px;
  background: #fff7f3;
  border-radius: 8px;
  font-size: 13px;
  color: #ff6034;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
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
  padding-bottom: calc(10px + env(safe-area-inset-bottom));
  z-index: 10;
}
.cart-gap {
  color: #ffb27a;
  font-size: 12px;
  margin-left: 8px;
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
