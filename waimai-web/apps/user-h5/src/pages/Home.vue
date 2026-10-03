<template>
  <div class="home">
    <!-- 顶部定位 + 搜索 -->
    <div class="header">
      <div class="location">
        <span class="loc-text" @click="goLocation">📍 {{ locationText }}</span>
        <span class="loc-actions">
          <span class="relocate" @click.stop="refreshLocation">
            <van-icon name="replay" size="12" /> 刷新定位
          </span>
          <span class="relocate" @click.stop="goLocation">
            <van-icon name="edit" size="12" /> 手动设置
          </span>
        </span>
      </div>
      <van-search v-model="keyword" placeholder="搜索商家、菜品" shape="round" @search="goSearch" />
    </div>

    <!-- 分类宫格 -->
    <van-grid :column-num="4" class="categories" v-if="categories.length">
      <van-grid-item v-for="c in categories" :key="c.id" :icon="c.icon || 'shop-o'" :text="c.name" @click="filterCategory(c.id)" />
    </van-grid>

    <!-- AI 每日推荐 -->
    <div class="section" v-if="recommends.length">
      <div class="section-title">
        <span class="title-ai">🤖 AI 每日推荐</span>
        <span class="sub">为你精选</span>
      </div>
      <div class="recommend-scroll">
        <div class="recommend-card" v-for="r in recommends" :key="r.dishId" @click="goMerchant(r.merchantId)">
          <div class="dish-name">{{ r.dishName }}</div>
          <div class="dish-reason">{{ r.reason }}</div>
          <div class="dish-price price">{{ r.price }}</div>
        </div>
      </div>
    </div>

    <!-- 附近商家 -->
    <div class="section">
      <div class="section-title">
        <span>附近商家</span>
        <span class="sub" @click="cycleSort">{{ sortLabel }}</span>
      </div>
      <van-skeleton v-if="loading" :row="3" />
      <div class="merchant-list" v-else>
        <div class="merchant-item" v-for="m in merchants" :key="m.id" @click="goMerchant(m.id)">
          <div class="merchant-left">
            <div class="merchant-logo">{{ (m.shopName || '商')[0] }}</div>
          </div>
          <div class="merchant-info">
            <div class="merchant-name">
              {{ m.shopName }}
              <van-tag v-if="m.isAd" type="warning" plain size="mini">广告</van-tag>
            </div>
            <div class="merchant-meta">⭐ {{ m.rating || 4.5 }} · 月售{{ m.monthlySales || 0 }}</div>
            <div class="merchant-meta">起送¥{{ m.minOrderAmount || 0 }} · {{ m.distanceKm ? m.distanceKm + 'km' : '' }}</div>
          </div>
        </div>
        <van-empty v-if="!merchants.length" description="暂无商家" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { showToast } from 'vant';
import { apiHome, apiMerchantList, apiDailyRecommend, apiSaveSearch } from '@/api';
import { getSavedLocation, setSavedLocation, locationLabel } from '@waimai/shared';
import type { Merchant, RecommendItem } from '@waimai/shared';

const router = useRouter();
const keyword = ref('');

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
  background: #f5f5f5;
}
.header {
  background: linear-gradient(135deg, #ff6034, #ff8a3d);
  padding: 12px 0 16px;
}
.location {
  color: #fff;
  padding: 0 16px;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.loc-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
}
.loc-actions {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}
.relocate {
  font-size: 12px;
  opacity: 0.9;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  cursor: pointer;
  white-space: nowrap;
}
.categories {
  background: #fff;
  margin-top: -1px;
}
.section {
  margin-top: 12px;
  background: #fff;
  padding: 12px 16px;
}
.section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 12px;
}
.title-ai {
  color: #ff6034;
}
.sub {
  color: #999;
  font-size: 13px;
  font-weight: normal;
}
.recommend-scroll {
  display: flex;
  overflow-x: auto;
  gap: 10px;
}
.recommend-card {
  flex: 0 0 130px;
  background: #fff7f3;
  border: 1px solid #ffd9c9;
  border-radius: 10px;
  padding: 12px;
}
.dish-name {
  font-weight: 600;
  font-size: 15px;
}
.dish-reason {
  font-size: 12px;
  color: #999;
  margin: 8px 0;
  min-height: 32px;
}
.merchant-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.merchant-item {
  display: flex;
  gap: 12px;
}
.merchant-logo {
  width: 56px;
  height: 56px;
  border-radius: 10px;
  background: #ff6034;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: bold;
}
.merchant-info {
  flex: 1;
}
.merchant-name {
  font-weight: 600;
  font-size: 15px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.merchant-meta {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}
</style>
