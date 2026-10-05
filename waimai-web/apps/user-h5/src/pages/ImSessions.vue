<template>
  <div class="im-sessions">
    <van-nav-bar title="我的消息" left-arrow @click-left="$router.back()" />

    <!-- 会话列表：卡片化，整行可点，未读用角标 -->
    <section class="sess-card" v-if="list.length">
      <div class="sess-head">
        <h2 class="sess-title">与商家的会话</h2>
        <span class="sess-count">共 {{ list.length }} 个</span>
      </div>
      <div class="sess-row" v-for="s in list" :key="s.id" @click="go(s)">
        <div class="avatar" aria-hidden="true">{{ (s.merchantName || '商').slice(0, 1) }}</div>
        <div class="sess-body">
          <div class="sess-top">
            <span class="sess-name">{{ s.merchantName || '商家' }}</span>
            <span class="sess-time">{{ s.lastMsgAt }}</span>
          </div>
          <div class="sess-last">{{ s.lastMsg || '暂无消息' }}</div>
        </div>
        <van-badge :content="s.unread" v-if="s.unread" class="sess-badge" />
        <van-icon name="arrow" size="12" class="sess-go" />
      </div>
    </section>

    <van-empty v-else description="还没有与商家的对话" />
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
.im-sessions {
  min-height: 100vh;
  background: var(--wm-bg-page);
  padding-bottom: var(--wm-space-6);
}

.sess-card {
  margin: var(--wm-space-3);
  padding: var(--wm-space-4);
  background: var(--wm-bg-card);
  border-radius: var(--wm-radius-lg);
  box-shadow: var(--wm-shadow-1);
}

.sess-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--wm-space-2);
  margin-bottom: var(--wm-space-2);
}

.sess-title {
  font-size: var(--wm-font-lg);
  font-weight: 600;
  line-height: var(--wm-leading-tight);
  color: var(--wm-text-1);
}

.sess-count {
  font-size: var(--wm-font-sm);
  color: var(--wm-text-3);
}

/* 整行可点：高度不小于触控下限 */
.sess-row {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--wm-space-3);
  min-height: var(--wm-tap-min);
  padding: var(--wm-space-3) 0;
  border-top: 1px solid var(--wm-border);
  cursor: pointer;
}

.sess-row:first-of-type {
  border-top: 0;
}

.sess-row:active {
  background: var(--wm-primary-50);
}

.avatar {
  flex: 0 0 44px;
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  border-radius: var(--wm-radius-md);
  background: linear-gradient(150deg, var(--wm-primary-light), var(--wm-primary));
  color: #fff;
  font-size: var(--wm-font-lg);
  font-weight: 600;
}

.sess-body {
  flex: 1;
  min-width: 0;
}

.sess-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--wm-space-2);
}

.sess-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--wm-font-md);
  font-weight: 600;
  color: var(--wm-text-1);
}

.sess-time {
  flex-shrink: 0;
  font-size: var(--wm-font-sm);
  color: var(--wm-text-4);
}

.sess-last {
  margin-top: var(--wm-space-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--wm-font-sm);
  color: var(--wm-text-3);
}

.sess-badge {
  flex-shrink: 0;
  margin-right: var(--wm-space-1);
}

.sess-go {
  flex-shrink: 0;
  color: var(--wm-text-4);
}
</style>
