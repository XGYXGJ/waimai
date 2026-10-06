<template>
  <div class="hall">
    <van-nav-bar title="抢单大厅">
      <template #right>
        <van-icon name="service-o" size="18" @click="router.push('/me')" />
      </template>
    </van-nav-bar>

    <div v-if="hasLoc" class="loc-bar">
      <van-icon name="location-o" />
      <span>已按距离排序，优先派最近的单</span>
    </div>
    <div v-else class="loc-bar warn">
      <van-icon name="info-o" />
      <span>未上报位置，按下单时间排序</span>
    </div>

    <van-pull-refresh v-model="refreshing" @refresh="load">
      <div class="order-card" v-for="o in orders" :key="o.orderId">
        <div class="card-head">
          <div class="shop">
            <span class="shop-name">{{ o.merchantName || '未知商家' }}</span>
            <van-tag v-if="o.pickupDistanceKm != null" type="primary" plain round>
              取货 {{ formatDistance(o.pickupDistanceKm) }}
            </van-tag>
            <van-tag v-else type="default" plain round>距离未知</van-tag>
          </div>
          <span class="order-no">{{ o.orderNo }}</span>
        </div>

        <div class="card-addr">
          <div class="addr-line">
            <van-icon name="location-o" class="ico" />
            <span class="addr-text">{{ o.addressText || '未填写收货地址' }}</span>
          </div>
          <div class="addr-line" v-if="o.contact || o.phone">
            <van-icon name="phone-o" class="ico" />
            <span class="addr-text">
              {{ o.contact || '匿名' }} {{ o.phone }}
            </span>
          </div>
        </div>

        <div class="fee-row">
          <div class="fee">
            <span class="fee-label">配送费</span>
            <span class="fee-value">{{ formatMoney(o.deliveryFee) }}</span>
          </div>
          <div class="fee">
            <span class="fee-label">配送里程</span>
            <span class="fee-value">{{ distText(o.distanceKm) }}</span>
          </div>
          <div class="fee income">
            <span class="fee-label">预计到手</span>
            <span class="fee-value">{{ formatMoney(o.riderIncome) }}</span>
          </div>
        </div>

        <div class="card-foot">
          <span class="time">{{ formatDateTime(o.createdAt) }} 下单</span>
          <van-button size="small" type="primary" color="#07c160" round @click="grab(o.orderId)">
            抢单
          </van-button>
        </div>
      </div>
      <van-empty v-if="!orders.length && !loadFailed" description="暂无待接订单" />
      <!-- 区分「加载失败」与「暂无订单」：静默 catch 会让两者看起来一样 -->
      <div v-if="loadFailed" class="load-failed">
        <van-empty :description="failMsg" />
        <van-button type="primary" round size="small" @click="load">重新加载</van-button>
      </div>
    </van-pull-refresh>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { showToast } from 'vant';
import { apiHall, apiGrab } from '@/api';
import { formatDistance, formatDateTime, formatMoney } from '@waimai/shared';

const router = useRouter();
const orders = ref<any[]>([]);
const refreshing = ref(false);
const loadFailed = ref(false);
const failMsg = ref('加载失败');

/** 至少有一单算出了「骑手 → 商家」距离，说明位置已上报 */
const hasLoc = computed(() => orders.value.some((o) => o.pickupDistanceKm != null));

/** 商家 → 收货地址 的里程（商家可没配坐标，这时给个占位而不是空白） */
function distText(km?: number | null): string {
  return km == null ? '—' : formatDistance(km);
}

async function grab(id: number) {
  try {
    await apiGrab(id);
    showToast('抢单成功');
    router.push(`/map/${id}`);
  } catch (e: any) {
    showToast(e.message);
    load();
  }
}

/**
 * 大厅里不给「沟通」入口。
 * 未接单时骑手还不是本单的配送人，服务端 checkOwner 会以「该订单还没有骑手接单」
 * 直接拒掉；等抢单成功后到「进行中」里再沟通，语义才对。
 */

async function load() {
  loadFailed.value = false;
  try {
    orders.value = (await apiHall()) as any[];
  } catch (e: any) {
    loadFailed.value = true;
    failMsg.value = e?.message || '加载失败，请确认后端已启动';
  }
  refreshing.value = false;
}

onMounted(load);
</script>

<style scoped>
/* tabbar 是 fixed 的：页面必须留出它的高度 + 安全区 */
.hall {
  min-height: 100vh;
  padding-bottom: calc(60px + env(safe-area-inset-bottom));
  background: #f5f5f5;
}
.loc-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  font-size: 12px;
  color: #1989fa;
  background: #ecf9ff;
}
.loc-bar.warn {
  color: #ee0a24;
  background: #ffeced;
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
.shop {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
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
.time {
  color: #999;
  font-size: 12px;
}

.load-failed {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 20px 0;
}
</style>
