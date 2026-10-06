<template>
  <div class="order-detail">
    <van-nav-bar title="订单详情" left-arrow @click-left="$router.back()" />

    <!-- 状态卡：状态文案 + 进度时间线，关键信息放最高层级 -->
    <section class="status-card" v-if="order.id">
      <div class="status-head">
        <span class="status-icon">
          <van-icon name="clock-o" size="20" />
        </span>
        <div class="status-main">
          <div class="status-text">{{ statusText(order.status) }}</div>
          <!-- 后端 detail() 返回的是 rider 实体（OrderService:562），不是 riderName。
               原来读 order.riderName 恒为 undefined，这行永远不显示 -->
          <div class="status-sub" v-if="order.status === 'DELIVERING' && riderName">
            骑手 {{ riderName }} 正在配送
          </div>
          <div class="status-sub" v-else>订单信息已同步，可下拉进入订单列表查看</div>
        </div>
      </div>

      <ol class="timeline">
        <li class="step" :class="{ 'step--done': tlStep >= 1, 'step--now': tlStep === 1 }">
          <span class="step-dot" aria-hidden="true"></span>
          <span class="step-label">已下单</span>
        </li>
        <li class="step" :class="{ 'step--done': tlStep >= 2, 'step--now': tlStep === 2 }">
          <span class="step-dot" aria-hidden="true"></span>
          <span class="step-label">已支付</span>
        </li>
        <li class="step" :class="{ 'step--done': tlStep >= 3, 'step--now': tlStep === 3 }">
          <span class="step-dot" aria-hidden="true"></span>
          <span class="step-label">商家接单</span>
        </li>
        <li class="step" :class="{ 'step--done': tlStep >= 4, 'step--now': tlStep === 4 }">
          <span class="step-dot" aria-hidden="true"></span>
          <span class="step-label">配送中</span>
        </li>
        <li class="step" :class="{ 'step--done': tlStep >= 5, 'step--now': tlStep === 5 }">
          <span class="step-dot" aria-hidden="true"></span>
          <span class="step-label">已完成</span>
        </li>
      </ol>
    </section>

    <!-- 订单信息 -->
    <section class="card">
      <h2 class="card-title">
        <van-icon name="orders-o" size="16" class="title-icon" />
        订单信息
      </h2>
      <van-cell-group>
        <van-cell title="订单号" :value="order.orderNo" />
        <van-cell title="配送地址" :value="addressText" />
        <van-cell title="备注" :value="order.remark || '无'" />
      </van-cell-group>
    </section>

    <!-- 费用明细 -->
    <section class="card">
      <h2 class="card-title">
        <van-icon name="bill-o" size="16" class="title-icon" />
        费用明细
      </h2>
      <van-cell-group>
        <van-cell title="商品金额" :value="`¥${money(order.dishAmount)}`" />
        <van-cell title="配送费" :value="`¥${money(order.deliveryFee)}`" />
        <van-cell title="打包费" :value="`¥${money(order.packageFee)}`" />
        <van-cell title="优惠" :value="`-¥${money(order.discountAmount)}`" />
      </van-cell-group>
      <!-- 实付金额单独提级，避免和分项混在一起看不出重点 -->
      <div class="pay-total">
        <span class="pay-label">实付</span>
        <span class="price pay-value">{{ money(order.payAmount) }}</span>
      </div>
    </section>

    <!-- 操作区 -->
    <section class="actions" v-if="order.status === 'PENDING_PAYMENT'">
      <van-button round block type="primary" color="var(--wm-primary)" class="act-btn" @click="pay">去支付</van-button>
      <van-button round block plain class="act-btn act-btn--sub" @click="cancel">取消订单</van-button>
    </section>
    <section class="actions" v-if="order.status === 'DELIVERING'">
      <van-button round block type="primary" color="var(--wm-primary)" class="act-btn" @click="track">追踪骑手</van-button>
    </section>
    <section class="actions">
      <van-button round block plain class="act-btn act-btn--contact" :loading="opening" @click="contactMerchant">
        <van-icon name="chat-o" size="16" class="btn-icon" />
        联系商家 / 骑手
      </van-button>
      <div class="chat-hint">商家与骑手在同一个频道；送达后 30 分钟内可反馈问题</div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { showToast } from 'vant';
import { apiOrderDetail, apiOrderPay, apiOrderCancel, apiImOpen } from '@/api';

const route = useRoute();
const router = useRouter();
const orderId = Number(route.params.id);
const order = ref<any>({});
const opening = ref(false);

/** 打开（或创建）该订单与商家的会话 */
async function contactMerchant() {
  if (opening.value) return;
  opening.value = true;
  try {
    const session: any = await apiImOpen(orderId);
    router.push(`/im/${session.id}`);
  } catch (e: any) {
    showToast(e.message || '打开会话失败');
  } finally {
    opening.value = false;
  }
}

const addressText = computed(() => {
  const a = order.value.address;
  if (!a) return '—';
  const detail = a.detail || '（未填写详细地址）';
  const who = [a.contact, a.phone].filter(Boolean).join(' ');
  return who ? `${detail}（${who}）` : detail;
});

/**
 * 骑手姓名。detail() 返回的是 rider 实体（OrderService 里 vo.put("rider", rm)），
 * 不是 riderName —— 之前模板读 order.riderName 恒为 undefined，导致
 * 「骑手 X 正在配送」那一行永远不显示。
 */
const riderName = computed(() => {
  const r = order.value?.rider;
  return typeof r === 'string' ? r : r?.realName || '';
});

/** 订单状态文案。必须是函数（模板按 statusText(order.status) 调用）。 */
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

/** 仅用于状态时间线的进度映射：不改变任何订单逻辑，只把状态翻译成第几步 */
const tlStep = computed(() => {
  switch (order.value.status) {
    case 'PENDING_PAYMENT': return 1;
    case 'PAID': return 2;
    case 'ACCEPTED': return 3;
    case 'WAITING_PICKUP':
    case 'DELIVERING': return 4;
    case 'DELIVERED': return 5;
    default: return 1;
  }
});

function money(v: any) {
  return Number(v || 0).toFixed(2);
}

async function pay() {
  try {
    await apiOrderPay(orderId);
    showToast('支付成功');
    load();
  } catch (e: any) {
    showToast(e.message);
  }
}

async function cancel() {
  try {
    await apiOrderCancel(orderId);
    showToast('已取消');
    load();
  } catch (e: any) {
    showToast(e.message);
  }
}

function track() {
  router.push(`/orders/${orderId}/track`);
}

async function load() {
  try {
    order.value = await apiOrderDetail(orderId);
  } catch {}
}

onMounted(load);
</script>

<style scoped>
.order-detail {
  min-height: 100vh;
  background: var(--wm-bg-page);
  padding-bottom: calc(var(--wm-space-6) + env(safe-area-inset-bottom));
}

/* ---------------- 卡片壳 ---------------- */
.card {
  margin: var(--wm-space-3);
  padding: var(--wm-space-4);
  background: var(--wm-bg-card);
  border-radius: var(--wm-radius-lg);
  box-shadow: var(--wm-shadow-1);
}

.card-title {
  display: flex;
  align-items: center;
  gap: var(--wm-space-2);
  margin-bottom: var(--wm-space-2);
  font-size: var(--wm-font-lg);
  font-weight: 600;
  line-height: var(--wm-leading-tight);
  color: var(--wm-text-1);
}

.title-icon {
  color: var(--wm-primary);
}

/* 卡片内的 cell-group 不再自己拉边距、不再套阴影（避免卡中卡） */
.card :deep(.van-cell-group) {
  margin: 0;
  box-shadow: none;
}

.card :deep(.van-cell) {
  padding-left: 0;
  padding-right: 0;
  background: transparent;
}

.card :deep(.van-cell:not(:last-child)::after) {
  left: 0;
  right: 0;
}

/* ---------------- 状态卡 ---------------- */
.status-card {
  margin: var(--wm-space-3);
  padding: var(--wm-space-4);
  background: var(--wm-bg-card);
  border-radius: var(--wm-radius-lg);
  box-shadow: var(--wm-shadow-1);
  border-left: 4px solid var(--wm-primary);
}

.status-head {
  display: flex;
  align-items: center;
  gap: var(--wm-space-3);
}

.status-icon {
  flex: 0 0 var(--wm-tap-min);
  width: var(--wm-tap-min);
  height: var(--wm-tap-min);
  display: grid;
  place-items: center;
  border-radius: var(--wm-radius-full);
  background: var(--wm-primary-50);
  color: var(--wm-primary);
}

.status-main {
  flex: 1;
  min-width: 0;
}

.status-text {
  font-size: var(--wm-font-2xl);
  font-weight: 600;
  line-height: var(--wm-leading-tight);
  color: var(--wm-text-1);
}

.status-sub {
  margin-top: var(--wm-space-1);
  font-size: var(--wm-font-sm);
  line-height: var(--wm-leading-normal);
  color: var(--wm-text-3);
}

/* ---------------- 进度时间线 ---------------- */
.timeline {
  display: flex;
  align-items: flex-start;
  margin-top: var(--wm-space-5);
  padding-top: var(--wm-space-4);
  border-top: 1px solid var(--wm-border);
  list-style: none;
}

.step {
  position: relative;
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--wm-space-2);
  text-align: center;
}

/* 连接线：当前步之后为浅灰，已完成段为主色 */
.step::before {
  content: '';
  position: absolute;
  top: 5px;
  right: calc(50% + 8px);
  left: calc(-50% + 8px);
  height: 2px;
  background: var(--wm-border);
}

.step:first-child::before {
  display: none;
}

.step--done::before {
  background: var(--wm-primary-200);
}

.step-dot {
  position: relative;
  z-index: 1;
  width: 12px;
  height: 12px;
  border-radius: var(--wm-radius-full);
  background: var(--wm-border);
}

.step--done .step-dot {
  background: var(--wm-primary);
}

.step--now .step-dot {
  width: 16px;
  height: 16px;
  margin-top: -2px;
  border: 3px solid var(--wm-primary-100);
  background: var(--wm-primary);
}

.step-label {
  font-size: var(--wm-font-xs);
  line-height: var(--wm-leading-tight);
  color: var(--wm-text-4);
}

.step--done .step-label {
  color: var(--wm-text-2);
}

.step--now .step-label {
  font-weight: 600;
  color: var(--wm-primary);
}

/* ---------------- 实付金额 ---------------- */
.pay-total {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--wm-space-2);
  margin-top: var(--wm-space-3);
  padding-top: var(--wm-space-3);
  border-top: 1px solid var(--wm-border);
}

.pay-label {
  font-size: var(--wm-font-md);
  color: var(--wm-text-2);
}

.pay-value {
  font-size: var(--wm-font-2xl);
  font-weight: 700;
  line-height: var(--wm-leading-tight);
}

/* ---------------- 操作区 ---------------- */
.actions {
  margin: var(--wm-space-3);
}

.act-btn {
  min-height: var(--wm-tap-min);
  font-size: var(--wm-font-lg);
}

.act-btn--sub {
  margin-top: var(--wm-space-3);
  font-size: var(--wm-font-md);
  color: var(--wm-text-2);
}

.act-btn--contact {
  font-size: var(--wm-font-md);
  color: var(--wm-text-2);
}

.btn-icon {
  margin-right: var(--wm-space-1);
  vertical-align: -2px;
}
</style>
