<template>
  <div class="me-page">
    <van-nav-bar title="我的" left-arrow @click-left="goBack" />

    <!-- 身份卡：一眼看清当前登录的是哪个骑手账号（work_status 与抢单/送达联动） -->
    <div class="id-card">
      <div class="avatar">{{ (info?.realName || '骑').charAt(0) }}</div>
      <div class="id-main">
        <div class="name">{{ info?.realName || '加载中…' }}</div>
        <div class="phone">{{ info?.phone || fallbackId }}</div>
      </div>
      <van-tag :type="statusTagType" round>{{ statusText }}</van-tag>
    </div>

    <van-cell-group inset class="grp">
      <van-cell title="骑手 ID" :value="String(info?.id ?? '—')" />
      <van-cell title="交通工具" :value="info?.vehicle || '—'" />
      <van-cell title="当前状态" :value="statusText" />
      <van-cell title="今日配送" :value="`${info?.todayOrders ?? 0} 单`" />
    </van-cell-group>

    <van-cell-group inset class="grp">
      <!-- 上下班：不开这个开关，work_status 永远停在默认值 0（休息），
           管理端「骑手上线」那个数就永远是死的 -->
      <van-cell title="上线接单" :model-value="online ? '已上线' : '休息中'">
        <template #right-icon>
          <van-switch :model-value="online" size="20" @update:model-value="toggleOnline" />
        </template>
      </van-cell>
      <van-cell title="在途订单" :value="`${info?.inFlightOrders ?? 0} 单`" />
      <van-cell title="售后工单（只读）" is-link @click="openTickets" />
      <van-cell title="订单沟通" is-link @click="router.push('/')" />
    </van-cell-group>

    <div class="logout-wrap">
      <van-button round block plain type="danger" @click="doLogout">退出登录</van-button>
    </div>

    <van-action-sheet v-model:show="tickets" title="与我相关的售后工单" cancel-text="关闭">
      <div class="ticket-sheet">
        <div v-if="!list.length" class="t-empty">暂无工单</div>
        <div v-for="t in list" :key="t.id" class="t-item">
          <div class="t-head">
            <span>{{ t.typeText }}</span>
            <span class="t-amt">¥{{ t.amount }}</span>
            <van-tag :type="t.status === 'APPROVED' ? 'success' : 'primary'" size="small">
              {{ t.statusText }}
            </van-tag>
          </div>
          <div class="t-reason">{{ t.reason }}</div>
          <div v-if="t.merchantReply" class="t-reply">商家回复：{{ t.merchantReply }}</div>
        </div>
      </div>
    </van-action-sheet>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { showToast, showConfirmDialog } from 'vant';
import { clearAuth, getToken, parseJwt } from '@waimai/shared';
import { apiRiderInfo, apiImTickets, apiSetOnline } from '@/api';

const router = useRouter();
const info = ref<any>(null);
const tickets = ref(false);
const list = ref<any[]>([]);

const WORK_TEXT: Record<number, string> = { 0: '休息中', 1: '空闲', 2: '配送中' };

const statusText = computed(() => WORK_TEXT[info.value?.workStatus ?? 0] || '未知');
const statusTagType = computed(() => {
  const s = info.value?.workStatus;
  return s === 2 ? 'warning' : s === 1 ? 'success' : 'default';
});
const online = computed(() => !!info.value?.online);

/** 打开工单面板并拉数据：原来只有 tickets = true，loadTickets 从没被调用过 */
async function openTickets() {
  tickets.value = true;
  list.value = [];
  await loadTickets();
}

async function toggleOnline(v: boolean) {
  try {
    await apiSetOnline(v);
    showToast(v ? '已上线' : '已休息');
  } catch (e: any) {
    showToast(e?.message || '切换失败');
  }
  await load();
}

/**
 * 兜底身份：/rider/info 还没回来时，先用 JWT 里的 userId 顶一下。
 * 注意 JWT 只带 sub(=userId) 和 role，**没有 phone**，别指望从 token 里取手机号。
 */
const fallbackId = computed(() => {
  const sub = (parseJwt(getToken()) as any)?.sub;
  return sub ? `用户 #${sub}` : '';
});

function goBack() {
  if (window.history.length > 1) router.back();
  else router.replace('/');
}

async function load() {
  try {
    info.value = await apiRiderInfo();
  } catch (e: any) {
    showToast(e.message || '加载失败');
  }
}

async function loadTickets() {
  try {
    list.value = (await apiImTickets()) as any[];
  } catch (e: any) {
    showToast(e.message || '加载失败');
  }
}

async function doLogout() {
  try {
    await showConfirmDialog({ title: '退出登录', message: '确定要退出当前骑手账号吗？' });
  } catch {
    return; // 用户取消
  }
  clearAuth();
  showToast('已退出');
  router.replace('/login');
}

onMounted(load);
</script>

<style scoped>
.me-page {
  min-height: 100vh;
  padding-bottom: calc(20px + env(safe-area-inset-bottom));
  background: #f7f8fa;
}

.id-card {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 12px;
  padding: 16px 14px;
  background: #fff;
  border-radius: 10px;
}
.avatar {
  width: 48px;
  height: 48px;
  flex-shrink: 0;
  border-radius: 50%;
  background: #07c160;
  color: #fff;
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.id-main {
  flex: 1;
  min-width: 0;
}
.name {
  font-size: 16px;
  font-weight: 600;
  color: #222;
}
.phone {
  margin-top: 2px;
  font-size: 13px;
  color: #999;
}

.grp {
  margin-bottom: 12px;
}

.logout-wrap {
  padding: 20px 16px;
}

.ticket-sheet {
  max-height: 60vh;
  overflow-y: auto;
  padding: 4px 16px 20px;
}
.t-empty {
  padding: 30px 0;
  text-align: center;
  color: #999;
  font-size: 13px;
}
.t-item {
  padding: 12px 0;
  border-bottom: 1px solid #f2f3f5;
}
.t-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}
.t-amt {
  font-weight: 600;
}
.t-reason {
  margin-top: 4px;
  font-size: 13px;
  color: #666;
}
.t-reply {
  margin-top: 4px;
  font-size: 12px;
  color: #07c160;
}
</style>
