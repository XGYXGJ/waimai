<template>
  <div class="pay-page">
    <van-nav-bar title="支付" left-arrow @click-left="onBack" />

    <!-- 订单已经不能再支付（超时被系统取消 / 已支付 / 已退款） -->
    <div v-if="invalid" class="invalid-box">
      <div class="invalid-icon">
        <van-icon name="clock-o" size="34" />
      </div>
      <div class="invalid-text">{{ invalidText }}</div>
      <van-button round type="primary" color="var(--wm-primary)" class="invalid-btn" @click="goDetail">
        查看订单详情
      </van-button>
      <van-button round plain class="invalid-btn" @click="goHome">回到首页</van-button>
    </div>

    <div v-else class="pay-body">
      <!-- 支付金额 -->
      <section class="card amount-card">
        <div class="amount-label">支付金额</div>
        <!-- .price 自带 ¥ 前缀与等宽数字，不再手写货币符号 -->
        <div class="amount-value price">{{ money(amount) }}</div>
      </section>

      <!-- 支付倒计时：与后端 MQ 延迟队列（15 分钟）同源，超时会自动取消 -->
      <section class="card countdown-card" :class="{ urgent: remain <= 60 }">
        <template v-if="remain > 0">
          <van-icon name="clock-o" size="16" class="cd-icon" />
          <span class="cd-text">
            请在 <span class="cd-num">{{ mmss }}</span> 内完成支付，超时订单将自动取消
          </span>
        </template>
        <template v-else>
          <van-icon name="clock-o" size="16" class="cd-icon" />
          <span class="cd-text">支付时间已结束，正在确认订单状态…</span>
        </template>
      </section>

      <!-- 费用明细 -->
      <section class="card detail-card">
        <h2 class="card-title">
          <van-icon name="orders-o" size="16" class="title-icon" />
          费用明细
        </h2>
        <div class="dish-list" v-if="order.items && order.items.length">
          <div class="dish-row" v-for="item in order.items" :key="item.dishId">
            <span class="dish-name">{{ item.dishName }} × {{ item.quantity }}</span>
            <span class="dish-money">¥{{ money(item.price * item.quantity) }}</span>
          </div>
        </div>
        <div class="fee-row">
          <span class="fee-label">商品小计</span>
          <span class="fee-value">¥{{ money(order.dishAmount) }}</span>
        </div>
        <div class="fee-row">
          <span class="fee-label">配送费</span>
          <span class="fee-value">¥{{ money(order.deliveryFee) }}</span>
        </div>
        <div class="fee-row">
          <span class="fee-label">打包费</span>
          <span class="fee-value">¥{{ money(order.packageFee) }}</span>
        </div>
        <div class="fee-row" v-if="order.discountAmount > 0">
          <span class="fee-label">优惠券</span>
          <span class="fee-value fee-cut">-¥{{ money(order.discountAmount) }}</span>
        </div>
      </section>

      <!-- 操作区 -->
      <section class="card actions-card">
        <van-button
          round block type="primary" color="var(--wm-primary)" class="act-btn"
          :loading="paying" :disabled="expired || checking" @click="pay"
        >{{ expired ? '支付已超时' : '确认支付' }}</van-button>
        <van-button
          round block plain class="act-btn act-btn--cancel"
          :loading="cancelling" :disabled="paying" @click="cancel"
        >取消订单</van-button>
        <div class="tip">模拟支付，不产生真实扣款</div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { showToast, showConfirmDialog } from 'vant';
import { apiOrderDetail, apiOrderPay, apiOrderCancel } from '@/api';

const route = useRoute();
const router = useRouter();
const orderId = Number(route.params.orderId);

const amount = ref(0);
const order = ref<any>({});
const paying = ref(false);
const cancelling = ref(false);
/** 倒计时剩余秒数，由后端 payRemainSeconds 初始化 */
const remain = ref(0);
/** 正在刷新订单状态（倒计时归零 / 支付被拒后） */
const checking = ref(false);
/** 订单已不可支付 */
const invalid = ref(false);
const invalidText = ref('订单已失效');

// 后端 ResultCode：订单状态已变更（超时自动取消、商家已接单等）
const CODE_ORDER_STATUS_CHANGED = 40010;

/** 兜底用的默认支付时限，与后端 RabbitMQConfig.ORDER_PAY_TIMEOUT_MINUTES 一致 */
const DEFAULT_TIMEOUT_SECONDS = 15 * 60;

let timer: number | null = null;

const expired = computed(() => remain.value <= 0);

const mmss = computed(() => {
  const m = Math.floor(remain.value / 60);
  const s = remain.value % 60;
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
});

function money(v: any) {
  return Number(v || 0).toFixed(2);
}

function stopTimer() {
  if (timer !== null) {
    clearInterval(timer);
    timer = null;
  }
}

function startTimer() {
  stopTimer();
  timer = window.setInterval(() => {
    if (remain.value > 0) remain.value -= 1;
    if (remain.value <= 0) {
      stopTimer();
      // 本地倒计时归零不代表订单一定被取消了（MQ 可能有微量延迟），以服务端为准
      refreshStatus();
    }
  }, 1000);
}

const STATUS_TEXT: Record<string, string> = {
  PAID: '该订单已支付',
  ACCEPTED: '商家已接单，订单进入备餐',
  WAITING_PICKUP: '订单备餐完成，等待骑手取餐',
  DELIVERING: '订单正在配送中',
  DELIVERED: '订单已送达',
  CANCELLED: '订单已取消',
  REFUNDED: '订单已退款',
};

/** 拉一次订单状态；若不再是待支付，就把页面切到「不可支付」态 */
async function refreshStatus() {
  if (checking.value) return;
  checking.value = true;
  try {
    const data: any = await apiOrderDetail(orderId);
    order.value = data;
    const st = String(data?.status || '');
    if (st && st !== 'PENDING_PAYMENT') {
      invalid.value = true;
      invalidText.value = st === 'CANCELLED'
        ? '订单已超时取消（15 分钟内未支付）'
        : (STATUS_TEXT[st] || '订单状态已变更');
    } else if (typeof data?.payRemainSeconds === 'number') {
      // 服务端还认为可支付：用服务端剩余时间校准本地倒计时
      remain.value = Math.max(0, Number(data.payRemainSeconds));
      if (remain.value > 0) startTimer();
    }
  } catch {
    // 拉取失败不阻塞用户，等下一次交互再试
  } finally {
    checking.value = false;
  }
}

async function pay() {
  if (expired.value || paying.value) return;
  paying.value = true;
  try {
    await apiOrderPay(orderId);
    stopTimer();
    showToast('支付成功');
    setTimeout(() => router.replace(`/orders/${orderId}`), 800);
  } catch (e: any) {
    // 超时被系统取消 / 商家已接单 等：刷新成真实状态，而不是只弹一句 toast
    if (e?.code === CODE_ORDER_STATUS_CHANGED) {
      showToast(e.message || '订单状态已变更');
      await refreshStatus();
      return;
    }
    showToast(e.message || '支付失败');
  } finally {
    paying.value = false;
  }
}

async function cancel() {
  try {
    await showConfirmDialog({
      title: '取消订单',
      message: '取消后已使用的优惠券会退回，确定取消这笔订单吗？',
      confirmButtonText: '取消订单',
      confirmButtonColor: 'var(--wm-danger)',
      cancelButtonText: '再想想',
    });
  } catch {
    return;
  }
  cancelling.value = true;
  try {
    await apiOrderCancel(orderId);
    stopTimer();
    showToast('订单已取消');
    setTimeout(() => router.replace(`/orders/${orderId}`), 800);
  } catch (e: any) {
    showToast(e.message || '取消失败');
    await refreshStatus();
  } finally {
    cancelling.value = false;
  }
}

function goDetail() {
  router.replace(`/orders/${orderId}`);
}

function goHome() {
  router.replace('/');
}

function onBack() {
  router.back();
}

onMounted(async () => {
  try {
    const data: any = await apiOrderDetail(orderId);
    order.value = data;
    amount.value = Number(data?.payAmount || 0);
    const st = String(data?.status || '');
    if (st && st !== 'PENDING_PAYMENT') {
      invalid.value = true;
      invalidText.value = STATUS_TEXT[st] || '订单状态已变更';
      return;
    }
    remain.value = typeof data?.payRemainSeconds === 'number'
      ? Math.max(0, Number(data.payRemainSeconds))
      : DEFAULT_TIMEOUT_SECONDS;
    if (remain.value > 0) startTimer();
    else refreshStatus();
  } catch (e: any) {
    showToast(e?.message || '订单加载失败');
  }
});

onUnmounted(stopTimer);
</script>

<style scoped>
.pay-page {
  min-height: 100vh;
  background: var(--wm-bg-page);
  padding-bottom: calc(var(--wm-space-6) + env(safe-area-inset-bottom));
}

.pay-body {
  padding-top: var(--wm-space-3);
}

/* ---------------- 卡片：与 Home / MerchantDetail 同一套壳 ---------------- */
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
  margin-bottom: var(--wm-space-3);
  font-size: var(--wm-font-lg);
  font-weight: 600;
  line-height: var(--wm-leading-tight);
  color: var(--wm-text-1);
}

.title-icon {
  color: var(--wm-primary);
}

/* ---------------- 支付金额 ---------------- */
.amount-card {
  text-align: center;
}

.amount-label {
  font-size: var(--wm-font-sm);
  color: var(--wm-text-3);
}

.amount-value {
  margin-top: var(--wm-space-2);
  font-size: var(--wm-font-2xl);
  font-weight: 700;
  line-height: var(--wm-leading-tight);
}

/* ---------------- 倒计时 ---------------- */
.countdown-card {
  display: flex;
  align-items: center;
  gap: var(--wm-space-2);
  background: var(--wm-primary-50);
  box-shadow: none;
}

.cd-icon {
  flex-shrink: 0;
  color: var(--wm-primary);
}

.cd-text {
  font-size: var(--wm-font-sm);
  line-height: var(--wm-leading-normal);
  color: var(--wm-primary-dark);
}

.cd-num {
  font-size: var(--wm-font-lg);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--wm-primary);
}

.countdown-card.urgent .cd-icon,
.countdown-card.urgent .cd-num {
  color: var(--wm-danger);
}

.countdown-card.urgent .cd-text {
  color: var(--wm-danger);
}

/* ---------------- 费用明细 ---------------- */
.dish-list {
  padding-bottom: var(--wm-space-2);
  margin-bottom: var(--wm-space-2);
  border-bottom: 1px solid var(--wm-border);
}

.dish-row,
.fee-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--wm-space-3);
  min-height: var(--wm-tap-min);
  font-size: var(--wm-font-md);
}

.dish-name,
.fee-label {
  flex: 1;
  min-width: 0;
  color: var(--wm-text-2);
}

.dish-money,
.fee-value {
  flex-shrink: 0;
  color: var(--wm-text-1);
  font-variant-numeric: tabular-nums;
}

.fee-cut {
  color: var(--wm-primary);
}

/* ---------------- 操作区 ---------------- */
.actions-card {
  padding-bottom: var(--wm-space-3);
}

.act-btn {
  min-height: var(--wm-tap-min);
  font-size: var(--wm-font-lg);
}

.act-btn--cancel {
  margin-top: var(--wm-space-3);
  font-size: var(--wm-font-md);
  color: var(--wm-text-2);
}

.tip {
  margin-top: var(--wm-space-3);
  text-align: center;
  font-size: var(--wm-font-xs);
  color: var(--wm-text-4);
}

/* ---------------- 不可支付态 ---------------- */
.invalid-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin: var(--wm-space-3);
  padding: var(--wm-space-8) var(--wm-space-4);
  background: var(--wm-bg-card);
  border-radius: var(--wm-radius-lg);
  box-shadow: var(--wm-shadow-1);
  text-align: center;
}

.invalid-icon {
  width: calc(var(--wm-tap-min) + var(--wm-space-5));
  height: calc(var(--wm-tap-min) + var(--wm-space-5));
  display: grid;
  place-items: center;
  border-radius: var(--wm-radius-full);
  background: var(--wm-primary-50);
  color: var(--wm-text-4);
}

.invalid-text {
  margin-top: var(--wm-space-4);
  font-size: var(--wm-font-lg);
  font-weight: 600;
  line-height: var(--wm-leading-normal);
  color: var(--wm-text-1);
}

.invalid-btn {
  min-height: var(--wm-tap-min);
  margin-top: var(--wm-space-4);
  align-self: stretch;
}
</style>
