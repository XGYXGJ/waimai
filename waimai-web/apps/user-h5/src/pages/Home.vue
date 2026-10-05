<template>
  <div class="home">
    <!-- 顶部：定位 + 搜索 -->
    <header class="hero">
      <div class="hero-row">
        <button class="loc-btn" type="button" @click="goLocation">
          <van-icon name="location-o" size="16" />
          <span class="loc-text">{{ locationText }}</span>
          <van-icon name="arrow" size="12" class="loc-arrow" />
        </button>
        <div class="loc-tools">
          <button class="tool-btn" type="button" @click.stop="refreshLocation">
            <van-icon name="replay" size="15" />
            <span>刷新</span>
          </button>
          <button class="tool-btn" type="button" @click.stop="goLocation">
            <van-icon name="edit" size="15" />
            <span>设置</span>
          </button>
        </div>
      </div>
      <div class="hero-search">
        <van-search
          v-model="keyword"
          placeholder="搜索商家、菜品"
          shape="round"
          background="transparent"
          @search="goSearch"
        />
      </div>
    </header>

    <!-- 分类：横向滚动圆形入口 -->
    <nav class="cats" v-if="categories.length" aria-label="商家分类">
      <button
        v-for="c in categories"
        :key="c.id"
        class="cat"
        :class="{ 'cat--on': categoryId === c.id }"
        type="button"
        @click="filterCategory(c.id)"
      >
        <span class="cat-icon">
          <van-icon :name="c.icon || 'shop-o'" size="20" />
        </span>
        <span class="cat-name">{{ c.name }}</span>
      </button>
    </nav>

    <!-- AI 每日推荐 -->
    <section class="section" v-if="recommends.length">
      <div class="section-head">
        <h2 class="section-title">AI 每日推荐</h2>
        <span class="section-sub">为你精选</span>
      </div>
      <div class="rec-scroll">
        <article
          class="rec-card"
          v-for="r in recommends"
          :key="r.dishId"
          @click="goMerchant(r.merchantId)"
        >
          <span class="rec-badge">AI 推荐</span>
          <h3 class="rec-name">{{ r.dishName }}</h3>
          <p class="rec-reason">{{ r.reason }}</p>
          <div class="rec-foot">
            <span class="price">{{ r.price }}</span>
            <van-icon name="arrow" size="12" class="rec-go" />
          </div>
        </article>
      </div>
    </section>

    <!-- 附近商家 -->
    <section class="section">
      <div class="section-head">
        <h2 class="section-title">附近商家</h2>
        <button class="sort-btn" type="button" @click="cycleSort">
          {{ sortLabel }}
          <van-icon name="arrow-down" size="10" />
        </button>
      </div>

      <van-skeleton v-if="loading" :row="3" />
      <div class="m-list" v-else>
        <article class="m-card" v-for="m in merchants" :key="m.id" @click="goMerchant(m.id)">
          <div class="m-logo" aria-hidden="true">{{ (m.shopName || '商')[0] }}</div>
          <div class="m-body">
            <div class="m-top">
              <h3 class="m-name">{{ m.shopName }}</h3>
              <span v-if="m.isAd" class="m-ad">广告</span>
            </div>
            <div class="m-rate">
              <span class="m-score">{{ m.rating || 4.5 }}</span>
              <van-icon name="star" size="11" class="m-star" />
              <span class="m-dot">·</span>
              <span>月售 {{ m.monthlySales || 0 }}</span>
            </div>
            <div class="m-ship">
              <span>起送 ¥{{ money(m.minOrderAmount) }}</span>
              <span class="m-dot">·</span>
              <span>配送 ¥{{ money(m.deliveryFee) }}</span>
              <template v-if="m.distanceKm">
                <span class="m-dot">·</span>
                <span>{{ m.distanceKm }} km</span>
              </template>
            </div>
            <div class="m-range" v-if="m.deliveryRadiusKm">
              配送范围 {{ m.deliveryRadiusKm }} km
            </div>
          </div>
        </article>
        <van-empty v-if="!merchants.length" :description="emptyDesc" />
      </div>
    </section>

    <!-- 购物车悬浮入口：不用进商家详情页也能直接去结算 -->
    <button class="cart-fab" type="button" aria-label="去购物车结算" @click="goCart">
      <van-badge :content="cartBadge" :show-zero="false" :offset="[-2, 2]">
        <van-icon name="shopping-cart-o" size="24" />
      </van-badge>
      <span class="fab-text">购物车</span>
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { showToast } from 'vant';
import { apiHome, apiMerchantList, apiDailyRecommend, apiSaveSearch, apiCartList } from '@/api';
import { getSavedLocation, setSavedLocation, locationLabel } from '@waimai/shared';
import type { Merchant, RecommendItem } from '@waimai/shared';

const router = useRouter();
const keyword = ref('');

/** 购物车件数：首页悬浮入口的角标 */
const cartCount = ref(0);
const cartBadge = computed(() => (cartCount.value > 99 ? '99+' : String(cartCount.value)));

async function loadCartCount() {
  try {
    const data: any = await apiCartList();
    const records: any[] = data?.records || [];
    cartCount.value = records.reduce((sum, r) => sum + Number(r.quantity || 0), 0);
  } catch {
    // 拿不到就当作 0，不打扰用户
    cartCount.value = 0;
  }
}

function goCart() {
  router.push('/cart');
}

function money(v: any) {
  return Number(v || 0).toFixed(2);
}

/** 空列表提示：有定位时大概率是「附近商家都不在这个位置的服务范围内」 */
const emptyDesc = computed(() =>
  lng.value && lat.value
    ? '当前位置附近没有可配送的商家，可点上方「刷新定位」或「手动设置」换个位置'
    : '暂无商家'
);

// 默认位置 = 上次刷新的位置（缓存于 localStorage）；无缓存时首次进入才自动定位一次
const cachedLocation = getSavedLocation();
const lng = ref<number | undefined>(cachedLocation?.lng);
const lat = ref<number | undefined>(cachedLocation?.lat);
const locationText = ref(cachedLocation ? locationLabel(cachedLocation) : '定位中...');

const categories = ref<any[]>([]);
const recommends = ref<RecommendItem[]>([]);
const merchants = ref<Merchant[]>([]);
const loading = ref(true);
const sortType = ref('综合');
const categoryId = ref<number | undefined>();

const sortOptions = ['综合', 'rating', 'sales', 'distance'];
const sortLabels: Record<string, string> = { 综合: '综合排序', rating: '评分优先', sales: '销量优先', distance: '距离优先' };
const sortLabel = computed(() => sortLabels[sortType.value] || '综合排序');

function cycleSort() {
  const i = sortOptions.indexOf(sortType.value);
  sortType.value = sortOptions[(i + 1) % sortOptions.length];
  loadMerchants();
}

function goSearch() {
  if (keyword.value.trim()) {
    apiSaveSearch(keyword.value.trim());
    router.push({ path: '/search', query: { keyword: keyword.value.trim() } });
  }
}

function filterCategory(id: number) {
  categoryId.value = id;
  loadMerchants();
}

function goMerchant(id: number) {
  router.push(`/merchant/${id}`);
}

function goLocation() {
  router.push('/location');
}

async function loadHome() {
  try {
    const data: any = await apiHome(lng.value, lat.value);
    categories.value = data.categories || [];
    merchants.value = data.merchants || [];
  } catch (e: any) {
    showToast(e.message);
  } finally {
    loading.value = false;
  }
}

async function loadMerchants() {
  loading.value = true;
  try {
    const data: any = await apiMerchantList({ lng: lng.value, lat: lat.value, categoryId: categoryId.value, sort: sortType.value });
    merchants.value = data.records || [];
  } catch (e: any) {
    showToast(e.message);
  } finally {
    loading.value = false;
  }
}

async function loadRecommend() {
  try {
    const data: any = await apiDailyRecommend(lng.value, lat.value);
    recommends.value = data.items || [];
  } catch {}
}

function locate(manual = false) {
  if (!navigator.geolocation) {
    locationText.value = locationLabel(getSavedLocation());
    if (manual) showToast('当前浏览器不支持定位，请使用「手动设置」');
    return;
  }
  if (manual) showToast('正在重新定位...');
  navigator.geolocation.getCurrentPosition(
    (pos) => {
      lng.value = pos.coords.longitude;
      lat.value = pos.coords.latitude;
      // 定位成功即把当前位置存为「默认位置」，下次进入直接复用
      const saved = setSavedLocation({ lng: lng.value!, lat: lat.value! });
      locationText.value = locationLabel(saved);
      if (manual) showToast('定位已刷新');
      loadHome();
      loadRecommend();
      loadMerchants();
    },
    () => {
      // 定位失败：沿用上次保存的位置（若有），否则退回默认位置
      const saved = getSavedLocation();
      locationText.value = locationLabel(saved);
      if (manual) showToast(saved ? '定位失败，继续使用上次位置' : '定位失败，请使用「手动设置」');
    },
    { enableHighAccuracy: true, timeout: 8000, maximumAge: 0 }
  );
}

/** 首页「刷新定位」按钮：重新获取浏览器当前位置并刷新数据 */
function refreshLocation() {
  locate(true);
}

onMounted(() => {
  // 购物车角标每次进首页都重新拉一次（从商家页/购物车返回时会重新挂载）
  loadCartCount();
  if (cachedLocation) {
    // 有缓存：直接用上次刷新的位置，不再自动定位（直到用户手动点击刷新）
    loadHome();
    loadRecommend();
  } else {
    // 无缓存：首次进入自动定位一次
    locate();
  }
});
</script>

<style scoped>
.home {
  background: var(--wm-bg-page);
  padding-bottom: var(--wm-space-6);
}

/* ---------------- 顶部 hero ---------------- */
.hero {
  background: linear-gradient(
    160deg,
    var(--wm-primary-light) 0%,
    var(--wm-primary) 58%,
    var(--wm-primary-dark) 100%
  );
  padding: var(--wm-space-4) var(--wm-space-4) var(--wm-space-6);
  border-radius: 0 0 var(--wm-radius-xl) var(--wm-radius-xl);
}

.hero-row {
  display: flex;
  align-items: center;
  gap: var(--wm-space-2);
}

.loc-btn {
  flex: 1;
  min-width: 0;
  display: inline-flex;
  align-items: center;
  gap: var(--wm-space-2);
  min-height: var(--wm-tap-min);
  padding: 0 var(--wm-space-3);
  border: 0;
  border-radius: var(--wm-radius-full);
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
  font-size: var(--wm-font-md);
  cursor: pointer;
}

.loc-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: left;
}

.loc-arrow {
  flex-shrink: 0;
  opacity: 0.8;
}

.loc-tools {
  display: inline-flex;
  gap: var(--wm-space-1);
  flex-shrink: 0;
}

.tool-btn {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  min-width: var(--wm-tap-min);
  min-height: var(--wm-tap-min);
  border: 0;
  border-radius: var(--wm-radius-md);
  background: transparent;
  color: #fff;
  font-size: var(--wm-font-xs);
  cursor: pointer;
}

.tool-btn:active {
  background: rgba(255, 255, 255, 0.2);
}

.hero-search {
  margin-top: var(--wm-space-3);
}

.hero-search :deep(.van-search) {
  padding: 0;
}

.hero-search :deep(.van-search__content) {
  background: #fff;
  box-shadow: var(--wm-shadow-1);
}

/* ---------------- 分类 ---------------- */
.cats {
  display: flex;
  gap: var(--wm-space-2);
  overflow-x: auto;
  margin: calc(-1 * var(--wm-space-5)) var(--wm-space-3) 0;
  padding: var(--wm-space-3) var(--wm-space-3) var(--wm-space-2);
  background: var(--wm-bg-card);
  border-radius: var(--wm-radius-lg);
  box-shadow: var(--wm-shadow-2);
  scrollbar-width: none;
}

.cats::-webkit-scrollbar {
  display: none;
}

.cat {
  flex: 0 0 auto;
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: var(--wm-space-2);
  min-width: 64px;
  min-height: var(--wm-tap-min);
  padding: var(--wm-space-1) 0;
  border: 0;
  background: transparent;
  cursor: pointer;
}

.cat-icon {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  border-radius: var(--wm-radius-full);
  background: var(--wm-primary-50);
  color: var(--wm-primary);
  transition: background-color 0.18s ease, color 0.18s ease;
}

.cat--on .cat-icon {
  background: var(--wm-primary);
  color: #fff;
}

.cat-name {
  font-size: var(--wm-font-sm);
  color: var(--wm-text-2);
  white-space: nowrap;
}

.cat--on .cat-name {
  color: var(--wm-primary);
  font-weight: 600;
}

/* ---------------- 通用区块 ---------------- */
.section {
  margin: var(--wm-space-3) var(--wm-space-3) 0;
  padding: var(--wm-space-4);
  background: var(--wm-bg-card);
  border-radius: var(--wm-radius-lg);
  box-shadow: var(--wm-shadow-1);
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--wm-space-2);
  margin-bottom: var(--wm-space-3);
}

.section-title {
  font-size: var(--wm-font-lg);
  font-weight: 600;
  line-height: var(--wm-leading-tight);
  color: var(--wm-text-1);
}

.section-sub {
  font-size: var(--wm-font-sm);
  color: var(--wm-text-3);
}

.sort-btn {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  min-height: 32px;
  padding: 0 var(--wm-space-3);
  border: 1px solid var(--wm-border);
  border-radius: var(--wm-radius-full);
  background: var(--wm-bg-card);
  color: var(--wm-text-3);
  font-size: var(--wm-font-sm);
  cursor: pointer;
}

/* ---------------- AI 推荐 ---------------- */
.rec-scroll {
  display: flex;
  gap: var(--wm-space-3);
  overflow-x: auto;
  margin: 0 calc(-1 * var(--wm-space-4));
  padding: 0 var(--wm-space-4) var(--wm-space-1);
  scrollbar-width: none;
}

.rec-scroll::-webkit-scrollbar {
  display: none;
}

.rec-card {
  flex: 0 0 156px;
  display: flex;
  flex-direction: column;
  padding: var(--wm-space-3);
  border: 1px solid var(--wm-primary-100);
  border-radius: var(--wm-radius-md);
  background: var(--wm-primary-50);
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.rec-card:active {
  transform: scale(0.98);
  box-shadow: var(--wm-shadow-1);
}

.rec-badge {
  align-self: flex-start;
  padding: 1px var(--wm-space-2);
  border-radius: var(--wm-radius-full);
  background: var(--wm-primary);
  color: #fff;
  font-size: var(--wm-font-xs);
  line-height: 18px;
}

.rec-name {
  margin-top: var(--wm-space-2);
  font-size: var(--wm-font-md);
  font-weight: 600;
  color: var(--wm-text-1);
}

.rec-reason {
  margin-top: var(--wm-space-1);
  min-height: 36px;
  font-size: var(--wm-font-sm);
  line-height: var(--wm-leading-normal);
  color: var(--wm-text-3);
}

.rec-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--wm-space-2);
}

.rec-go {
  color: var(--wm-primary);
}

/* ---------------- 商家列表 ---------------- */
.m-list {
  display: flex;
  flex-direction: column;
  gap: var(--wm-space-3);
}

.m-card {
  display: flex;
  gap: var(--wm-space-3);
  padding: var(--wm-space-3);
  border: 1px solid var(--wm-border);
  border-radius: var(--wm-radius-md);
  background: var(--wm-bg-card);
  cursor: pointer;
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}

.m-card:active {
  border-color: var(--wm-primary-200);
  box-shadow: var(--wm-shadow-1);
}

.m-logo {
  flex: 0 0 56px;
  width: 56px;
  height: 56px;
  display: grid;
  place-items: center;
  border-radius: var(--wm-radius-md);
  background: linear-gradient(150deg, var(--wm-primary-light), var(--wm-primary));
  color: #fff;
  font-size: var(--wm-font-2xl);
  font-weight: 600;
  box-shadow: var(--wm-shadow-1);
}

.m-body {
  flex: 1;
  min-width: 0;
}

.m-top {
  display: flex;
  align-items: center;
  gap: var(--wm-space-2);
}

.m-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--wm-font-lg);
  font-weight: 600;
  line-height: var(--wm-leading-tight);
  color: var(--wm-text-1);
}

.m-ad {
  flex-shrink: 0;
  padding: 1px var(--wm-space-2);
  border: 1px solid var(--wm-primary-200);
  border-radius: var(--wm-radius-sm);
  color: var(--wm-primary);
  font-size: var(--wm-font-xs);
  line-height: 16px;
}

.m-rate {
  display: flex;
  align-items: center;
  gap: var(--wm-space-1);
  margin-top: var(--wm-space-2);
  font-size: var(--wm-font-sm);
  color: var(--wm-text-3);
}

.m-score {
  color: var(--wm-primary);
  font-weight: 600;
}

.m-star {
  color: var(--wm-primary);
}

.m-dot {
  color: var(--wm-text-4);
}

.m-ship {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--wm-space-1);
  margin-top: var(--wm-space-1);
  font-size: var(--wm-font-sm);
  color: var(--wm-text-3);
}

.m-range {
  margin-top: var(--wm-space-1);
  font-size: var(--wm-font-xs);
  color: var(--wm-text-4);
}

/* ---------------- 购物车悬浮入口 ---------------- */
.cart-fab {
  position: fixed;
  right: var(--wm-space-4);
  bottom: calc(66px + env(safe-area-inset-bottom));
  z-index: 20;
  width: 58px;
  height: 58px;
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1px;
  border: 0;
  border-radius: var(--wm-radius-full);
  background: linear-gradient(150deg, var(--wm-primary-light), var(--wm-primary));
  color: #fff;
  box-shadow: var(--wm-shadow-primary);
  cursor: pointer;
  transition: transform 0.18s ease;
}

.cart-fab:active {
  transform: scale(0.94);
}

.fab-text {
  font-size: var(--wm-font-xs);
  line-height: 1;
}
</style>
