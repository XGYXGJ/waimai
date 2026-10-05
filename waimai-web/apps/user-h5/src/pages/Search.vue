<template>
  <div class="search-page">
    <van-nav-bar left-arrow @click-left="$router.back()">
      <template #title>
        <van-search v-model="keyword" placeholder="搜索商家、菜品" @search="search" autofocus />
      </template>
    </van-nav-bar>

    <!-- 搜索历史 -->
    <section class="card" v-if="!searched && history.length">
      <h2 class="block-title">
        <van-icon name="clock-o" size="14" />
        <span>搜索历史</span>
      </h2>
      <div class="tags">
        <van-tag v-for="(h, i) in history" :key="i" plain class="h-tag" @click="keyword = h; search()">{{ h }}</van-tag>
      </div>
    </section>

    <!-- 搜索结果 -->
    <section class="card" v-if="searched">
      <h2 class="block-title">
        <van-icon name="shop-o" size="14" />
        <span>搜索结果</span>
      </h2>

      <div class="list">
        <div class="merchant-item" v-for="m in merchants" :key="m.id" @click="$router.push(`/merchant/${m.id}`)">
          <div class="merchant-body">
            <div class="merchant-name">
              <span class="merchant-title">{{ m.shopName }}</span>
              <van-tag v-if="m.isAd" type="warning" plain size="mini">广告</van-tag>
            </div>
            <div class="merchant-meta">
              <van-icon name="star" size="11" class="meta-icon" />
              <span>{{ m.rating || 4.5 }}</span>
              <span class="meta-dot">·</span>
              <span>月售{{ m.monthlySales || 0 }}</span>
            </div>
          </div>
          <van-icon name="arrow" size="12" class="item-arrow" />
        </div>
      </div>

      <template v-if="dishes.length">
        <h2 class="block-title dish-title">相关菜品</h2>
        <div class="dish-item" v-for="d in dishes" :key="d.dishId" @click="$router.push(`/merchant/${d.merchantId}`)">
          <span class="dish-name">{{ d.dishName }}</span>
          <span class="price">{{ d.price }}</span>
        </div>
      </template>

      <van-empty v-if="!merchants.length && !dishes.length" description="未找到相关内容" />
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { apiMerchantList, apiSearchDishes, apiSearchHistory, apiSaveSearch } from '@/api';

const route = useRoute();
const keyword = ref((route.query.keyword as string) || '');
const history = ref<string[]>([]);
const merchants = ref<any[]>([]);
const dishes = ref<any[]>([]);
const searched = ref(false);

async function search() {
  const k = keyword.value.trim();
  if (!k) return;
  searched.value = true;
  apiSaveSearch(k);
  try {
    const m: any = await apiMerchantList({ keyword: k });
    merchants.value = m.records || [];
  } catch {}
  try {
    const d: any = await apiSearchDishes(k);
    dishes.value = d.records || [];
  } catch {}
}

onMounted(async () => {
  try {
    const data: any = await apiSearchHistory();
    history.value = (data.records || []).map((h: any) => h.keyword);
  } catch {}
  if (keyword.value) search();
});
</script>

<style scoped>
.search-page {
  min-height: 100vh;
  background: var(--wm-bg-page);
  padding-bottom: calc(var(--wm-space-6) + env(safe-area-inset-bottom));
}

/* 导航栏里的搜索框：撑到 44px 的可点高度 */
.search-page :deep(.van-nav-bar) {
  background: var(--wm-bg-card);
}

.search-page :deep(.van-search) {
  padding: 0;
}

.search-page :deep(.van-search__content) {
  display: flex;
  align-items: center;
  min-height: var(--wm-tap-min);
  background: var(--wm-bg-page);
}

.search-page :deep(.van-field__control) {
  color: var(--wm-text-1);
  font-size: var(--wm-font-md);
}

.search-page :deep(.van-field__control)::placeholder {
  color: var(--wm-text-4);
}

/* ---------------- 通用卡片 ---------------- */
.card {
  margin: var(--wm-space-3);
  padding: var(--wm-space-4);
  background: var(--wm-bg-card);
  border-radius: var(--wm-radius-lg);
  box-shadow: var(--wm-shadow-1);
}

.block-title {
  display: inline-flex;
  align-items: center;
  gap: var(--wm-space-1);
  margin-bottom: var(--wm-space-3);
  font-size: var(--wm-font-lg);
  font-weight: 600;
  line-height: var(--wm-leading-tight);
  color: var(--wm-text-1);
}

.dish-title {
  margin-top: var(--wm-space-5);
}

/* ---------------- 搜索历史 ---------------- */
.tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--wm-space-2);
}

.h-tag {
  display: inline-flex;
  align-items: center;
  min-height: var(--wm-tap-min);
  padding: 0 var(--wm-space-4);
  border: 1px solid var(--wm-primary-100);
  border-radius: var(--wm-radius-full);
  background: var(--wm-primary-50);
  color: var(--wm-primary-dark);
  font-size: var(--wm-font-sm);
  cursor: pointer;
}

.h-tag:active {
  border-color: var(--wm-primary-200);
}

/* ---------------- 结果列表 ---------------- */
.merchant-item {
  display: flex;
  align-items: center;
  gap: var(--wm-space-3);
  min-height: var(--wm-tap-min);
  padding: var(--wm-space-3) 0;
  border-bottom: 1px solid var(--wm-border);
  cursor: pointer;
}

.merchant-item:first-child {
  padding-top: 0;
}

.merchant-item:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}

.merchant-body {
  flex: 1;
  min-width: 0;
}

.merchant-name {
  display: flex;
  align-items: center;
  gap: var(--wm-space-2);
  min-width: 0;
}

.merchant-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--wm-font-lg);
  font-weight: 600;
  color: var(--wm-text-1);
}

.merchant-meta {
  display: flex;
  align-items: center;
  gap: var(--wm-space-1);
  margin-top: var(--wm-space-1);
  font-size: var(--wm-font-sm);
  color: var(--wm-text-3);
}

.meta-icon {
  color: var(--wm-primary);
}

.meta-dot {
  color: var(--wm-text-4);
}

.item-arrow {
  flex-shrink: 0;
  color: var(--wm-text-4);
}

.dish-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--wm-space-3);
  min-height: var(--wm-tap-min);
  padding: var(--wm-space-3) 0;
  border-bottom: 1px solid var(--wm-border);
  cursor: pointer;
}

.dish-item:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}

.dish-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--wm-font-md);
  color: var(--wm-text-2);
}
</style>
