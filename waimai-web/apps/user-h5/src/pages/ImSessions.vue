<template>
  <div class="page">
    <van-nav-bar title="我的消息" left-arrow @click-left="$router.back()" />
    <van-empty v-if="!list.length" description="还没有与商家的对话" />
    <van-cell-group inset>
      <van-cell v-for="s in list" :key="s.id" :title="s.merchantName || '商家'"
        :label="s.lastMsg || '暂无消息'" :value="s.lastMsgAt" is-link @click="go(s)">
        <template #icon>
          <div class="avatar">{{ (s.merchantName || '商').slice(0, 1) }}</div>
        </template>
        <template #right-icon>
          <van-badge :content="s.unread" v-if="s.unread" />
        </template>
      </van-cell>
    </van-cell-group>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { showToast } from 'vant';
import { apiImSessions } from '@/api';

const router = useRouter();
const list = ref<any[]>([]);

function go(s: any) {
  router.push(`/im/${s.id}`);
}

onMounted(async () => {
  try {
    list.value = (await apiImSessions()) || [];
  } catch (e: any) {
    showToast(e.message || '加载失败');
  }
});
</script>

<style scoped>
.avatar {
  width: 36px;
  height: 36px;
  border-radius: 6px;
  background: #ff6034;
  color: #fff;
  font-size: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 10px;
}
</style>
