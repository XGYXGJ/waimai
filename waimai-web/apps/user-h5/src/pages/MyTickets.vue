<template>
  <div class="page">
    <van-nav-bar title="我的工单" left-arrow @click-left="$router.back()" />
    <van-empty v-if="!list.length" description="暂无售后工单" />
    <van-cell-group inset v-else>
      <van-cell v-for="t in list" :key="t.id" :label="`单号 ${t.orderNo || t.orderId}`"
        @click="openSession(t)">
        <template #title>
          <span class="t-type">{{ t.typeText }}</span>
          <span class="t-amount">¥{{ t.amount }}</span>
        </template>
        <template #value>
          <van-tag :type="tagType(t.status)">{{ t.statusText }}</van-tag>
        </template>
        <template #label>
          <div class="t-reason">{{ t.reason }}</div>
          <div class="t-meta">单号 {{ t.orderNo || t.orderId }} · {{ t.createdAt }}</div>
          <div class="t-reply" v-if="t.merchantReply">商家回复：{{ t.merchantReply }}</div>
        </template>
      </van-cell>
    </van-cell-group>
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
.t-type {
  font-weight: 600;
  margin-right: 8px;
}
.t-amount {
  color: #ff6034;
}
.t-reason {
  color: #666;
  margin: 4px 0;
}
.t-meta {
  color: #999;
  font-size: 12px;
}
.t-reply {
  margin-top: 4px;
  color: #333;
}
</style>
