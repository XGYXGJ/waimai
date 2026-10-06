<template>
  <div class="delivering">
    <van-nav-bar title="进行中订单">
      <template #right>
        <van-icon name="service-o" size="18" @click="router.push('/me')" />
      </template>
    </van-nav-bar>
    <!-- 不再整卡可点：按钮就挤在卡片底部，实测很容易点到卡片而不是按钮，
         结果被跳去地图页（那里没有取餐按钮），看起来就像「取餐点不了」。
         改成显式的「地图」按钮，入口明确、不会误触。 -->
    <div class="order-card" v-for="o in orders" :key="o.orderId">
      <div class="card-head">
        <span class="shop-name">{{ o.merchantName || '未知商家' }}</span>
        <span class="order-no">{{ o.orderNo }}</span>
      </div>

      <div class="card-addr">
        <div class="addr-line">
          <van-icon name="location-o" class="ico" />
          <span class="addr-text">{{ o.addressText || '未填写收货地址' }}</span>
        </div>
        <div class="addr-line" v-if="o.contact || o.phone">
          <van-icon name="phone-o" class="ico" />
          <span class="addr-text">{{ o.contact || '匿名' }} {{ o.phone }}</span>
        </div>
      </div>

      <div class="fee-row">
        <div class="fee">
          <span class="fee-label">配送费</span>
          <span class="fee-value">{{ formatMoney(o.deliveryFee) }}</span>
        </div>
        <div class="fee">
          <span class="fee-label">配送里程</span>
          <span class="fee-value">{{ o.distanceKm == null ? '—' : formatDistance(o.distanceKm) }}</span>
        </div>
        <div class="fee income">
          <span class="fee-label">本单到手</span>
          <span class="fee-value">{{ formatMoney(o.riderIncome) }}</span>
        </div>
      </div>

      <div class="card-foot">
        <van-tag type="warning">{{ statusText(o.status) }}</van-tag>
        <div class="ops">
          <van-button size="small" plain icon="chat-o" @click="goChat(o.orderId)">沟通</van-button>
          <van-button size="small" plain icon="location-o" @click="goMap(o.orderId)">地图</van-button>
          <van-button
            v-if="o.status === 'WAITING_PICKUP'"
            size="small"
            type="primary"
            color="#07c160"
            @click="pickup(o.orderId)"
          >
            确认取餐
          </van-button>
          <van-button
            v-if="o.status === 'DELIVERING'"
            size="small"
            type="success"
            color="#07c160"
            @click="deliver(o.orderId)"
          >
            确认送达
          </van-button>
        </div>
      </div>
    </div>
    <van-empty v-if="!orders.length && !loadFailed" description="暂无进行中订单" />

    <!-- 加载失败与「没有订单」必须区分开：静默 catch 会让两者长得一模一样，
         排查时完全看不出是接口挂了还是本来就没单 -->
    <div v-if="loadFailed" class="load-failed">
      <van-empty :description="failMsg" />
      <van-button type="primary" round size="small" @click="load">重新加载</van-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { showToast } from 'vant';
import { apiDeliveries, apiPickup, apiDeliver } from '@/api';
import { formatDistance, formatMoney } from '@waimai/shared';

const router = useRouter();
const orders = ref<any[]>([]);
const loadFailed = ref(false);
const failMsg = ref('加载失败');

function goChat(orderId: number) {
  router.push(`/chat/${orderId}`);
}

/** 点整张卡片进地图：同时显示「我的位置」与「顾客位置」 */
function goMap(orderId: number) {
  router.push(`/map/${orderId}`);
}

/** 订单状态文案。必须是函数：模板里按 statusText(o.status) 调用，
 *  写成对象字面量的话 v-for 一渲染就会抛 "statusText is not a function"，
 *  连带把整个 vdom 搞坏（列表刷不出来、tabbar 也点不动）。 */
function statusText(s?: string): string {
  return (
    {
      WAITING_PICKUP: '待取餐',
      DELIVERING: '配送中',
      DELIVERED: '已送达',
    }[s || ''] || s || ''
  );
}

async function pickup(id: number) {
  try {
    await apiPickup(id);
    showToast('已取餐');
    load();
  } catch (e: any) {
    showToast(e.message);
  }
}

async function deliver(id: number) {
  try {
    await apiDeliver(id);
    showToast('已送达');
    load();
  } catch (e: any) {
    showToast(e.message);
  }
}

async function load() {
  loadFailed.value = false;
  try {
    orders.value = (await apiDeliveries()) as any[];
  } catch (e: any) {
    loadFailed.value = true;
    failMsg.value = e?.message || '加载失败，请确认后端已启动';
  }
}

onMounted(load);
</script>

<style scoped>
/* tabbar 是 fixed 的：页面必须留出它的高度 + 安全区，否则最后一张卡片会压在 tabbar 上 */
.delivering {
  min-height: 100vh;
  padding-bottom: calc(60px + env(safe-area-inset-bottom));
  background: #f5f5f5;
}
.order-card {
  background: #fff;
  margin: 10px 12px;
  border-radius: 10px;
  padding: 14px;
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.shop-name {
  font-size: 15px;
  font-weight: 600;
  color: #222;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.order-no {
  color: #999;
  font-size: 12px;
  flex-shrink: 0;
}

.card-addr {
  margin: 10px 0;
}
.addr-line {
  display: flex;
  gap: 4px;
  color: #333;
  font-size: 13px;
  line-height: 1.5;
}
.addr-line + .addr-line {
  margin-top: 2px;
}
.ico {
  flex-shrink: 0;
  margin-top: 2px;
  color: #999;
}
.addr-text {
  word-break: break-all;
}

.fee-row {
  display: flex;
  background: #f7f8fa;
  border-radius: 8px;
  padding: 8px 10px;
}
.fee {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.fee + .fee {
  border-left: 1px solid #ebedf0;
  padding-left: 10px;
}
.fee-label {
  font-size: 11px;
  color: #999;
}
.fee-value {
  font-size: 14px;
  font-weight: 600;
  color: #222;
}
.fee.income .fee-value {
  color: #07c160;
}

.card-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
}
.ops {
  display: flex;
  flex-wrap: wrap;          /* 三个按钮在窄屏换行，不被挤扁难点 */
  gap: 6px;
  justify-content: flex-end;
}
/* 触控下限 44px：手机上手指命中的目标不能太小 */
.ops :deep(.van-button) {
  min-height: 36px;
}

.load-failed {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 20px 0;
}
</style>
