<template>
  <div class="orders-page">
    <van-nav-bar title="我的订单" left-arrow @click-left="$router.back()" />

    <div class="tabs-shell">
      <van-tabs v-model:active="activeTab">
        <van-tab v-for="t in tabs" :key="t.key" :title="t.label" :name="t.key" />
      </van-tabs>
    </div>

    <van-list
      :key="activeTab"
      v-model:loading="loading"
      :finished="finished"
      @load="load"
      class="order-list"
    >
      <article class="order-card" v-for="o in orders" :key="o.id" @click="goDetail(o.id)">
        <div class="order-head">
          <span class="order-no">
            <van-icon name="orders-o" size="14" class="no-icon" />
            订单号 {{ o.orderNo }}
          </span>
          <span class="status">
            <i class="status-dot" aria-hidden="true"></i>
            {{ statusText(o.status) }}
          </span>
        </div>

        <div class="order-info">
          <div class="order-shop">
            <van-icon name="shop-o" size="14" class="shop-icon" />
            <span class="shop-name">{{ o.merchantName || '商家' }}</span>
          </div>
          <div class="order-goods" v-if="o.items?.length">
            {{ o.items[0].dishName }}<span v-if="o.itemCount > 1"> 等 {{ o.itemCount }} 件</span>
          </div>
          <div class="order-addr">
            <van-icon name="location-o" size="13" class="addr-icon" />
            <span class="addr-text">{{ o.addressText || o.address?.detail || '—' }}</span>
          </div>
        </div>

        <div class="order-foot">
          <span class="price">{{ money(o.payAmount) }}</span>
          <div class="actions">
            <van-button v-if="o.status === 'PENDING_PAYMENT'" size="small" type="primary" color="var(--wm-primary)" @click.stop="goPay(o.id)">去支付</van-button>
            <van-button v-if="o.status === 'DELIVERING'" size="small" type="primary" color="var(--wm-primary)" @click.stop="goTrack(o.id)">追踪骑手</van-button>
            <van-button v-if="o.status === 'DELIVERED' && !o.reviewed" size="small" @click.stop="goReview(o.id)">评价</van-button>
          </div>
        </div>
      </article>

      <van-empty v-if="finished && !orders.length && !loadFailed" description="暂无订单" />
      <div v-if="loadFailed" class="load-failed">
        <van-empty :description="failMsg" />
        <van-button type="primary" round size="small" @click="retry">重新加载</van-button>
      </div>
    </van-list>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { apiMyOrders } from '@/api';

const router = useRouter();
function money(v: any) {
  return Number(v || 0).toFixed(2);
}
const tabs = [
  { key: '', label: '全部' },
  { key: 'PENDING_PAYMENT', label: '待支付' },
  { key: 'DELIVERING', label: '进行中' },
  { key: 'DELIVERED', label: '待评价' },
];
/** 与 apiMyOrders 的默认 size 保持一致：判断「还有没有下一页」要用它 */
const PAGE_SIZE = 10;
const activeTab = ref('');
const orders = ref<any[]>([]);
const loading = ref(false);
const finished = ref(false);
const loadFailed = ref(false);
const failMsg = ref('加载失败');
const page = ref(1);

// 切换 tab 时必须把分页状态重置。之前只改 activeTab 不重新加载，
// 结果切过去看到的还是上一个 tab 的订单、而且页码已经加到第 N 页。
watch(activeTab, () => {
  orders.value = [];
  page.value = 1;
  finished.value = false;
  loading.value = false;
  loadFailed.value = false;
});

/** 订单状态文案。必须是函数：模板按 statusText(o.status) 调用。
 *  定义成对象字面量时，v-for 一渲染就会抛 "statusText is not a function"，
 *  列表直接刷不出来（且会连带损坏 vdom）。 */
function statusText(s?: string): string {
  return (
    {
      PENDING_PAYMENT: '待支付',
      PAID: '待接单',
      ACCEPTED: '备餐中',
      WAITING_PICKUP: '待取餐',
      DELIVERING: '配送中',
      DELIVERED: '已送达',
      CANCELLED: '已取消',
      REFUNDED: '已退款',
    }[s || ''] || s || ''
  );
}

function goDetail(id: number) {
  router.push(`/orders/${id}`);
}
function goPay(id: number) {
  router.push(`/pay/${id}`);
}
function goTrack(id: number) {
  router.push(`/orders/${id}/track`);
}
function goReview(id: number) {
  router.push(`/orders/${id}/review`);
}

async function load() {
  // 只能挡 finished。
  // 之前还挡了 loading —— 但 van-list 的触发顺序是「先把 loading 置 true → 同步 emit
  // update:loading → 再 emit load」，到我们的 handler 执行时 loading 已经是 true，
  // 于是每次都在第一行 return：列表永远空、finished 永远 false（连空态都不显示）、
  // 转圈停不下来。必须靠 van-list 自身的 loading prop 来防重入，不能自己读它。
  if (finished.value) return;
  loading.value = true;
  try {
    const data: any = await apiMyOrders(activeTab.value, page.value);
    const records = data?.records ?? [];
    orders.value = page.value === 1 ? records : [...orders.value, ...records];
    page.value++;
    finished.value = records.length < PAGE_SIZE;
  } catch (e: any) {
    // 这里必须 catch：@waimai/shared 的响应拦截器对一切失败都 reject，
    // 而 van-list 看到 loading=false 会自动再触发一次 @load —— 不接住异常就会变成
    // 「请求失败 → 无限重试 → 页面永远转圈」。失败时标记 finished 断开重试，并给出重试入口。
    finished.value = true;
    loadFailed.value = true;
    failMsg.value = e?.message || '加载失败，请检查网络或后端是否启动';
  } finally {
    loading.value = false;
  }
}

/** 手动重试：重新打开自动加载并从第一页拉 */
function retry() {
  loadFailed.value = false;
  failMsg.value = '加载失败';
  orders.value = [];
  page.value = 1;
  finished.value = false;
  load();
}
</script>

<style scoped>
.orders-page {
  min-height: 100vh;
  background: var(--wm-bg-page);
  /* 底部固定 tabbar（50px）+ 安全区，避免最后一个卡片被压住 */
  padding-bottom: calc(50px + env(safe-area-inset-bottom));
}

/* ---------------- 状态筛选 Tab ---------------- */
.tabs-shell {
  margin: var(--wm-space-3);
  background: var(--wm-bg-card);
  border-radius: var(--wm-radius-lg);
  box-shadow: var(--wm-shadow-1);
  overflow: hidden;
}

.tabs-shell :deep(.van-tabs__wrap) {
  height: var(--wm-tap-min);
}

.tabs-shell :deep(.van-tabs__nav) {
  background: var(--wm-bg-card);
  padding: 0 var(--wm-space-2);
}

.tabs-shell :deep(.van-tab) {
  font-size: var(--wm-font-md);
  color: var(--wm-text-2);
}

.tabs-shell :deep(.van-tab--active) {
  color: var(--wm-primary);
  font-weight: 600;
}

.tabs-shell :deep(.van-tabs__line) {
  width: 20px;
  height: 3px;
  /* Vant 按 translateX 定位下划线，改成短横线后以自身中心对齐文字 */
  transform-origin: center;
  border-radius: var(--wm-radius-full);
  background: var(--wm-primary);
}

/* ---------------- 订单列表 ---------------- */
.order-list {
  margin-top: 0;
}

.order-card {
  margin: var(--wm-space-3);
  padding: var(--wm-space-4);
  background: var(--wm-bg-card);
  border-radius: var(--wm-radius-lg);
  box-shadow: var(--wm-shadow-1);
  cursor: pointer;
  transition: box-shadow 0.18s ease;
}

.order-card:active {
  box-shadow: var(--wm-shadow-2);
}

.order-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--wm-space-2);
  padding-bottom: var(--wm-space-2);
  border-bottom: 1px solid var(--wm-border);
  font-size: var(--wm-font-sm);
}

.order-no {
  display: inline-flex;
  align-items: center;
  gap: var(--wm-space-1);
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--wm-text-3);
}

.no-icon {
  flex-shrink: 0;
  color: var(--wm-text-4);
}

/* 状态：关键信息，用主色 + 圆点提升层级 */
.status {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: var(--wm-space-1);
  font-size: var(--wm-font-md);
  font-weight: 600;
  color: var(--wm-primary);
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: var(--wm-radius-full);
  background: var(--wm-primary);
}

.order-info {
  padding: var(--wm-space-2) 0;
}

.order-shop {
  display: flex;
  align-items: center;
  gap: var(--wm-space-1);
  font-size: var(--wm-font-lg);
  font-weight: 600;
  line-height: var(--wm-leading-tight);
  color: var(--wm-text-1);
}

.shop-icon {
  flex-shrink: 0;
  color: var(--wm-primary);
}

.shop-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-goods {
  margin-top: var(--wm-space-2);
  font-size: var(--wm-font-md);
  line-height: var(--wm-leading-normal);
  color: var(--wm-text-2);
}

.order-addr {
  display: flex;
  align-items: center;
  gap: var(--wm-space-1);
  margin-top: var(--wm-space-1);
  font-size: var(--wm-font-sm);
  color: var(--wm-text-3);
}

.addr-icon {
  flex-shrink: 0;
  color: var(--wm-text-4);
}

.addr-text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--wm-space-2);
  padding-top: var(--wm-space-2);
  border-top: 1px solid var(--wm-border);
}

.order-foot .price {
  font-size: var(--wm-font-xl);
  line-height: var(--wm-leading-tight);
}

.actions {
  display: flex;
  gap: var(--wm-space-2);
  flex-shrink: 0;
}

.actions :deep(.van-button) {
  /* 次级按钮也要满足 44px 触控下限 */
  min-height: var(--wm-tap-min);
  padding: 0 var(--wm-space-4);
  font-size: var(--wm-font-md);
}

/* ---------------- 加载失败 ---------------- */
.load-failed {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--wm-space-2);
  padding: var(--wm-space-4) 0;
}

.load-failed :deep(.van-empty) {
  padding: var(--wm-space-3) 0;
}
</style>
