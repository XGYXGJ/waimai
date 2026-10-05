<template>
  <div class="my-tickets">
    <van-nav-bar title="我的工单" left-arrow @click-left="$router.back()" />

    <!-- 工单列表：一张卡一个工单，状态用标签，整块可点回到会话 -->
    <section class="tickets" v-if="list.length">
      <div class="tickets-head">
        <h2 class="tickets-title">售后工单</h2>
        <span class="tickets-count">共 {{ list.length }} 条</span>
      </div>

      <article class="t-card" v-for="t in list" :key="t.id" @click="openSession(t)">
        <div class="t-head">
          <span class="t-type">
            <van-icon name="records" size="14" />
            <span class="t-type-text">{{ t.typeText }}</span>
          </span>
          <span class="t-amount">¥{{ t.amount }}</span>
          <van-tag :type="tagType(t.status)">{{ t.statusText }}</van-tag>
        </div>

        <p class="t-reason">{{ t.reason }}</p>

        <div class="t-meta">
          <van-icon name="orders-o" size="12" />
          <span>单号 {{ t.orderNo || t.orderId }} · {{ t.createdAt }}</span>
        </div>

        <div class="t-reply" v-if="t.merchantReply">
          <van-icon name="chat-o" size="13" class="t-reply-icon" />
          <span>商家回复：{{ t.merchantReply }}</span>
        </div>

        <div class="t-go">
          <span>查看会话</span>
          <van-icon name="arrow" size="12" />
        </div>
      </article>
    </section>

    <van-empty v-else description="暂无售后工单" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { showToast } from 'vant';
import { apiImTickets } from '@/api';

const router = useRouter();
const list = ref<any[]>([]);

const tagType = (s: string) =>
  s === 'APPROVED' ? 'success' : s === 'REJECTED' || s === 'CLOSED' ? 'danger' : 'primary';

function openSession(t: any) {
  if (t.sessionId) router.push(`/im/${t.sessionId}`);
}

onMounted(async () => {
  try {
    list.value = (await apiImTickets()) || [];
  } catch (e: any) {
    showToast(e.message || '加载失败');
  }
});
</script>

<style scoped>
.my-tickets {
  min-height: 100vh;
  background: var(--wm-bg-page);
  padding-bottom: var(--wm-space-6);
}

.tickets {
  margin: var(--wm-space-3);
  padding: var(--wm-space-4);
  background: var(--wm-bg-card);
  border-radius: var(--wm-radius-lg);
  box-shadow: var(--wm-shadow-1);
}

.tickets-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--wm-space-2);
  margin-bottom: var(--wm-space-2);
}

.tickets-title {
  font-size: var(--wm-font-lg);
  font-weight: 600;
  line-height: var(--wm-leading-tight);
  color: var(--wm-text-1);
}

.tickets-count {
  font-size: var(--wm-font-sm);
  color: var(--wm-text-3);
}

/* 单条工单：用分隔线而不是嵌套卡片，避免卡片套卡片 */
.t-card {
  min-height: var(--wm-tap-min);
  padding: var(--wm-space-3) 0;
  border-top: 1px solid var(--wm-border);
  cursor: pointer;
}

.t-card:first-of-type {
  border-top: 0;
}

.t-card:active {
  background: var(--wm-primary-50);
}

.t-head {
  display: flex;
  align-items: center;
  gap: var(--wm-space-2);
}

.t-type {
  display: inline-flex;
  align-items: center;
  gap: var(--wm-space-1);
  font-size: var(--wm-font-md);
  font-weight: 600;
  color: var(--wm-text-1);
}

.t-type-text {
  white-space: nowrap;
}

.t-amount {
  margin-right: auto;
  color: var(--wm-primary);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.t-reason {
  margin-top: var(--wm-space-2);
  font-size: var(--wm-font-md);
  line-height: var(--wm-leading-normal);
  color: var(--wm-text-2);
}

.t-meta {
  display: flex;
  align-items: center;
  gap: var(--wm-space-1);
  margin-top: var(--wm-space-2);
  font-size: var(--wm-font-sm);
  color: var(--wm-text-4);
}

.t-reply {
  display: flex;
  align-items: flex-start;
  gap: var(--wm-space-1);
  margin-top: var(--wm-space-2);
  padding: var(--wm-space-2) var(--wm-space-3);
  border-radius: var(--wm-radius-sm);
  background: var(--wm-primary-50);
  color: var(--wm-primary-dark);
  font-size: var(--wm-font-sm);
  line-height: var(--wm-leading-normal);
}

.t-reply-icon {
  margin-top: 2px;
  flex-shrink: 0;
}

.t-go {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--wm-space-1);
  margin-top: var(--wm-space-2);
  font-size: var(--wm-font-sm);
  color: var(--wm-text-3);
}
</style>
