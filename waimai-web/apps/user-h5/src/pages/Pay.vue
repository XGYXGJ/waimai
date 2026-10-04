<template>
  <div class="pay-page">
    <van-nav-bar title="支付" left-arrow @click-left="onBack" />

    <!-- 订单已经不能再支付（超时被系统取消 / 已支付 / 已退款） -->
    <div v-if="invalid" class="invalid-box">
      <van-icon name="clock-o" size="56" color="#c8c9cc" />
      <div class="invalid-text">{{ invalidText }}</div>
      <van-button round type="primary" color="#ff6034" style="margin-top: 16px" @click="goDetail">
        查看订单详情
      </van-button>
      <van-button round plain style="margin-top: 8px" @click="goHome">回到首页</van-button>
    </div>

    <div v-else class="pay-body">
      <div class="amount">
        <div class="label">支付金额</div>
        <div class="value price">¥{{ money(amount) }}</div>
      </div>

      <!-- 支付倒计时：与后端 MQ 延迟队列（15 分钟）同源，超时会自动取消 -->
      <div class="countdown" :class="{ urgent: remain <= 60 }">
        <template v-if="remain > 0">
          <van-icon name="clock-o" />
          请在 <span class="cd-num">{{ mmss }}</span> 内完成支付，超时订单将自动取消
        </template>
        <template v-else>支付时间已结束，正在确认订单状态…</template>
      </div>

      <!-- 费用明细 -->
      <van-cell-group inset title="费用明细">
        <div class="detail-item" v-for="item in order.items" :key="item.dishId">
          <span>{{ item.dishName }} × {{ item.quantity }}</span>
          <span>¥{{ money(item.price * item.quantity) }}</span>
        </div>
        <van-cell title="商品小计" :value="`¥${money(order.dishAmount)}`" />
        <van-cell title="配送费" :value="`¥${money(order.deliveryFee)}`" />
        <van-cell title="打包费" :value="`¥${money(order.packageFee)}`" />
        <van-cell v-if="order.discountAmount > 0" title="优惠券" :value="`-¥${money(order.discountAmount)}`" />
      </van-cell-group>

      <div class="actions">
        <van-button
          round block type="primary" color="#ff6034"
          :loading="paying" :disabled="expired || checking" @click="pay"
        >{{ expired ? '支付已超时' : '确认支付' }}</van-button>
        <van-button
          round block plain style="margin-top: 8px"
          :loading="cancelling" :disabled="paying" @click="cancel"
        >取消订单</van-button>
      </div>
      <div class="tip">模拟支付，不产生真实扣款</div>
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
      confirmButtonColor: '#ee0a24',
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
  background: #f5f5f5;
}
.pay-body {
  padding: 24px 0 40px;
}
.amount {
  text-align: center;
  margin: 24px 0 16px;
}
.amount .label {
  color: #999;
  font-size: 14px;
}
.amount .value {
  font-size: 40px;
  font-weight: bold;
  margin-top: 12px;
}
.countdown {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  margin: 0 16px 16px;
  padding: 8px 12px;
  border-radius: 8px;
  background: #fff7f3;
  color: #ff6034;
  font-size: 13px;
}
.countdown.urgent {
  background: #fff1f0;
  color: #ee0a24;
  font-weight: 600;
}
.cd-num {
  font-variant-numeric: tabular-nums;
  font-weight: 700;
  font-size: 15px;
}
.tip {
  text-align: center;
  color: #999;
  font-size: 12px;
  margin-top: 16px;
}
.detail-item {
  display: flex;
  justify-content: space-between;
  padding: 8px 16px;
  font-size: 14px;
  color: #333;
}
.actions {
  margin: 20px 16px 0;
}
.invalid-box {
  padding: 80px 24px;
  text-align: center;
}
.invalid-text {
  margin-top: 16px;
  color: #646566;
  font-size: 15px;
}
</style>
