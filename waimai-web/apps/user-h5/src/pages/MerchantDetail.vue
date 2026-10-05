<template>
  <div class="merchant-detail">
    <van-nav-bar :title="merchant.shopName || '商家详情'" left-arrow @click-left="$router.back()" />

    <header class="shop" v-if="merchant.id">
      <div class="shop-main">
        <div class="shop-logo" aria-hidden="true">{{ (merchant.shopName || '商')[0] }}</div>
        <div class="shop-info">
          <h1 class="shop-name">{{ merchant.shopName }}</h1>
          <div class="shop-rate">
            <span class="shop-score">{{ merchant.rating || 4.5 }}</span>
            <van-icon name="star" size="11" class="shop-star" />
            <span class="dot">·</span>
            <span>月售 {{ merchant.monthlySales || 0 }}</span>
            <span class="dot">·</span>
            <span>{{ merchant.businessHours || '营业中' }}</span>
          </div>
          <div class="shop-ship">
            <span>起送 ¥{{ Number(merchant.minOrderAmount || 0).toFixed(2) }}</span>
            <span class="dot">·</span>
            <span>配送 ¥{{ Number(merchant.deliveryFee || 0).toFixed(2) }} 起</span>
            <template v-if="merchant.deliveryRadiusKm">
              <span class="dot">·</span>
              <span>范围 {{ merchant.deliveryRadiusKm }} km</span>
            </template>
          </div>
        </div>
      </div>
      <div class="shop-notice" v-if="merchant.notice">
        <van-icon name="volume-o" size="14" class="notice-icon" />
        <span>{{ merchant.notice }}</span>
      </div>
    </header>

    <!-- 菜品列表（按分类） -->
    <section class="cat-block" v-for="cat in dishCategories" :key="cat.id">
      <h2 class="cat-title">{{ cat.name }}</h2>
      <article class="dish" v-for="d in dishesByCategory(cat.id)" :key="d.id">
        <div class="dish-img" aria-hidden="true">{{ (d.name || '菜')[0] }}</div>
        <div class="dish-body">
          <h3 class="dish-name">{{ d.name }}</h3>
          <p class="dish-desc">{{ d.description }}</p>
          <div class="dish-foot">
            <div class="dish-price-wrap">
              <span class="price">{{ d.price }}</span>
              <span v-if="d.originalPrice > d.price" class="origin-price">¥{{ d.originalPrice }}</span>
              <span v-if="d.originalPrice > d.price" class="discount-tag">{{ discountLabel(d) }}</span>
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
      </article>
    </section>

    <!-- 购物车里是别家的菜：给出明确入口，不静默丢弃 -->
    <div class="other-shop-tip" v-if="otherShopCart">
      <span>购物车中还有「{{ otherShopCart.merchantName || '其他商家' }}」的 {{ otherShopCart.count }} 件商品</span>
      <van-button size="small" type="primary" @click="clearOtherShop">清空并换店</van-button>
    </div>

    <!-- 用户评价 -->
    <section class="reviews" v-if="reviews.records?.length">
      <div class="reviews-head">
        <h2 class="reviews-title">用户评价</h2>
        <span class="reviews-score">
          {{ reviews.avgRating || 0 }}
          <van-icon name="star" size="11" />
          <span class="reviews-sub">({{ reviews.total || 0 }}条 · 好评率{{ reviews.goodRate || 0 }}%)</span>
        </span>
      </div>
      <article class="review" v-for="r in reviews.records" :key="r.id">
        <div class="review-top">
          <van-rate :model-value="r.rating" readonly :size="12" />
          <span class="review-time">{{ (r.createdAt || '').slice(0, 10) }}</span>
        </div>
        <p class="review-content">{{ r.content }}</p>
        <p class="review-reply" v-if="r.reply">商家回复：{{ r.reply }}</p>
      </article>
    </section>

    <!-- 购物车栏 -->
    <div class="cart-bar" v-if="totalQty > 0">
      <div class="cart-info">
        <span class="cart-total">¥{{ totalAmount.toFixed(2) }}</span>
        <span class="cart-count">{{ totalQty }} 件</span>
        <span class="cart-gap" v-if="gapToMin > 0">还差 ¥{{ gapToMin.toFixed(2) }} 起送</span>
      </div>
      <van-button
        type="primary"
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
  min-height: 100vh;
  background: var(--wm-bg-page);
  padding-bottom: calc(84px + env(safe-area-inset-bottom));
}

/* ---------------- 店铺头部 ---------------- */
.shop {
  margin: var(--wm-space-3);
  padding: var(--wm-space-4);
  background: var(--wm-bg-card);
  border-radius: var(--wm-radius-lg);
  box-shadow: var(--wm-shadow-1);
}

.shop-main {
  display: flex;
  gap: var(--wm-space-3);
}

.shop-logo {
  flex: 0 0 60px;
  width: 60px;
  height: 60px;
  display: grid;
  place-items: center;
  border-radius: var(--wm-radius-md);
  background: linear-gradient(150deg, var(--wm-primary-light), var(--wm-primary));
  color: #fff;
  font-size: var(--wm-font-2xl);
  font-weight: 600;
  box-shadow: var(--wm-shadow-1);
}

.shop-info {
  flex: 1;
  min-width: 0;
}

.shop-name {
  font-size: var(--wm-font-xl);
  font-weight: 600;
  line-height: var(--wm-leading-tight);
  color: var(--wm-text-1);
}

.shop-rate,
.shop-ship {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--wm-space-1);
  margin-top: var(--wm-space-2);
  font-size: var(--wm-font-sm);
  color: var(--wm-text-3);
}

.shop-ship {
  margin-top: var(--wm-space-1);
}

.shop-score,
.shop-star {
  color: var(--wm-primary);
  font-weight: 600;
}

.dot {
  color: var(--wm-text-4);
}

.shop-notice {
  display: flex;
  align-items: flex-start;
  gap: var(--wm-space-2);
  margin-top: var(--wm-space-3);
  padding: var(--wm-space-2) var(--wm-space-3);
  border-radius: var(--wm-radius-md);
  background: var(--wm-primary-50);
  color: var(--wm-primary-dark);
  font-size: var(--wm-font-sm);
  line-height: var(--wm-leading-normal);
}

.notice-icon {
  margin-top: 2px;
  flex-shrink: 0;
}

/* ---------------- 菜品 ---------------- */
.cat-block {
  margin: var(--wm-space-3);
  padding: var(--wm-space-2) var(--wm-space-4) var(--wm-space-4);
  background: var(--wm-bg-card);
  border-radius: var(--wm-radius-lg);
  box-shadow: var(--wm-shadow-1);
}

.cat-title {
  padding: var(--wm-space-3) 0 var(--wm-space-2);
  font-size: var(--wm-font-md);
  font-weight: 600;
  color: var(--wm-text-1);
}

.dish {
  display: flex;
  gap: var(--wm-space-3);
  padding: var(--wm-space-3) 0;
  border-top: 1px solid var(--wm-border);
}

.dish:first-of-type {
  border-top: 0;
}

.dish-img {
  flex: 0 0 64px;
  width: 64px;
  height: 64px;
  display: grid;
  place-items: center;
  border-radius: var(--wm-radius-md);
  background: var(--wm-primary-50);
  color: var(--wm-primary);
  font-size: var(--wm-font-2xl);
}

.dish-body {
  flex: 1;
  min-width: 0;
}

.dish-name {
  font-size: var(--wm-font-md);
  font-weight: 600;
  color: var(--wm-text-1);
}

.dish-desc {
  margin: var(--wm-space-1) 0 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--wm-font-sm);
  color: var(--wm-text-3);
}

.dish-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--wm-space-2);
  margin-top: var(--wm-space-2);
}

.dish-price-wrap {
  display: flex;
  align-items: center;
  gap: var(--wm-space-2);
  min-width: 0;
}

.dish-price-wrap .price {
  font-size: var(--wm-font-lg);
}

.origin-price {
  font-size: var(--wm-font-sm);
  color: var(--wm-text-4);
  text-decoration: line-through;
}

.discount-tag {
  padding: 1px var(--wm-space-2);
  border: 1px solid var(--wm-primary-200);
  border-radius: var(--wm-radius-sm);
  color: var(--wm-primary);
  font-size: var(--wm-font-xs);
  line-height: 16px;
}

/* ---------------- 其他商家的购物车提示 ---------------- */
.other-shop-tip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--wm-space-2);
  margin: var(--wm-space-3);
  padding: var(--wm-space-3);
  border-radius: var(--wm-radius-md);
  background: var(--wm-primary-50);
  color: var(--wm-primary-dark);
  font-size: var(--wm-font-sm);
}

/* ---------------- 评价 ---------------- */
.reviews {
  margin: var(--wm-space-3);
  padding: var(--wm-space-4);
  background: var(--wm-bg-card);
  border-radius: var(--wm-radius-lg);
  box-shadow: var(--wm-shadow-1);
}

.reviews-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--wm-space-2);
  margin-bottom: var(--wm-space-3);
}

.reviews-title {
  font-size: var(--wm-font-lg);
  font-weight: 600;
  color: var(--wm-text-1);
}

.reviews-score {
  display: inline-flex;
  align-items: center;
  gap: var(--wm-space-1);
  color: var(--wm-primary);
  font-weight: 600;
}

.reviews-sub {
  font-size: var(--wm-font-sm);
  color: var(--wm-text-3);
  font-weight: normal;
}

.review {
  padding: var(--wm-space-3) 0;
  border-bottom: 1px solid var(--wm-border);
}

.review:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.review-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.review-time {
  font-size: var(--wm-font-sm);
  color: var(--wm-text-3);
}

.review-content {
  margin-top: var(--wm-space-2);
  font-size: var(--wm-font-md);
  line-height: var(--wm-leading-normal);
  color: var(--wm-text-2);
}

.review-reply {
  margin-top: var(--wm-space-2);
  padding: var(--wm-space-2) var(--wm-space-3);
  border-radius: var(--wm-radius-sm);
  background: var(--wm-primary-50);
  color: var(--wm-primary-dark);
  font-size: var(--wm-font-sm);
  line-height: var(--wm-leading-normal);
}

/* ---------------- 底部购物车栏 ---------------- */
.cart-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--wm-space-3) var(--wm-space-4);
  padding-bottom: calc(var(--wm-space-3) + env(safe-area-inset-bottom));
  background: var(--wm-text-1);
  color: #fff;
  box-shadow: 0 -4px 16px rgba(24, 24, 32, 0.16);
}

.cart-info {
  display: flex;
  align-items: baseline;
  gap: var(--wm-space-2);
  min-width: 0;
}

.cart-total {
  color: var(--wm-primary-light);
  font-size: var(--wm-font-xl);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.cart-count {
  font-size: var(--wm-font-sm);
  color: rgba(255, 255, 255, 0.72);
}

.cart-gap {
  font-size: var(--wm-font-sm);
  color: var(--wm-warning);
}
</style>
