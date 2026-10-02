<template>
  <div class="search-page">
    <van-nav-bar left-arrow @click-left="$router.back()">
      <template #title>
        <van-search v-model="keyword" placeholder="搜索商家、菜品" @search="search" autofocus />
      </template>
    </van-nav-bar>

    <div class="history" v-if="!searched && history.length">
      <div class="h-title">搜索历史</div>
      <van-tag v-for="(h, i) in history" :key="i" plain style="margin: 4px" @click="keyword = h; search()">{{ h }}</van-tag>
    </div>

    <div class="results" v-if="searched">
      <div class="r-title">搜索结果</div>
      <div class="merchant-item" v-for="m in merchants" :key="m.id" @click="$router.push(`/merchant/${m.id}`)">
        <div class="merchant-name">
          {{ m.shopName }}
          <van-tag v-if="m.isAd" type="warning" plain size="mini">广告</van-tag>
        </div>
        <div class="merchant-meta">⭐ {{ m.rating || 4.5 }} · 月售{{ m.monthlySales || 0 }}</div>
      </div>
      <div class="r-title" v-if="dishes.length" style="margin-top: 16px">相关菜品</div>
      <div class="dish-item" v-for="d in dishes" :key="d.dishId" @click="$router.push(`/merchant/${d.merchantId}`)">
        <span>{{ d.dishName }}</span>
        <span class="price">{{ d.price }}</span>
      </div>
      <van-empty v-if="!merchants.length && !dishes.length" description="未找到相关内容" />
    </div>
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
.history,
.results {
  padding: 16px;
}
.h-title,
.r-title {
  font-weight: 600;
  margin-bottom: 8px;
}
.merchant-item {
  background: #fff;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 8px;
}
.merchant-name {
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
}
.merchant-meta {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}
.dish-item {
  display: flex;
  justify-content: space-between;
  background: #fff;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 8px;
}
</style>
