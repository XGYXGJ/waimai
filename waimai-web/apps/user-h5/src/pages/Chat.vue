<template>
  <div class="chat-page">
    <van-nav-bar title="AI 智能客服" left-arrow @click-left="$router.back()" />

    <!-- 会话区：卡片化消息流，自己发的用品牌色气泡 -->
    <div class="chat-body">
      <div class="chat-hero">
        <span class="chat-hero-icon">
          <van-icon name="service-o" size="18" />
        </span>
        <div class="chat-hero-text">
          <h2 class="chat-hero-title">小饿客服</h2>
          <p class="chat-hero-sub">在线为你解答订单、配送与售后问题</p>
        </div>
      </div>

      <div class="msg-list" v-if="messages.length">
        <div class="message" v-for="(m, i) in messages" :key="i" :class="m.role === 'user' ? 'user' : 'assistant'">
          <span class="bubble-avatar" v-if="m.role !== 'user'">
            <van-icon name="service-o" size="14" />
          </span>
          <div class="bubble">{{ m.content }}</div>
        </div>
      </div>

      <van-empty v-else description="你好，我是小饿客服，有什么可以帮你？" />
    </div>

    <!-- 输入区：高度由外层 calc(100vh - 50px) 保证，底部再做安全区留白 -->
    <div class="chat-input">
      <van-field v-model="input" class="chat-field" placeholder="输入你的问题..." @keyup.enter="send" />
      <van-button type="primary" color="var(--wm-primary)" round size="small" @click="send">
        <van-icon name="guide-o" size="16" />
        <span class="send-text">发送</span>
      </van-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { showToast } from 'vant';
import { apiChat } from '@/api';

const messages = ref<{ role: string; content: string }[]>([]);
const input = ref('');
const sending = ref(false);

async function send() {
  const text = input.value.trim();
  if (!text || sending.value) return;
  messages.value.push({ role: 'user', content: text });
  input.value = '';
  sending.value = true;
  try {
    const res = await apiChat(text);
    messages.value.push({ role: 'assistant', content: res.reply || '这个问题我转人工客服为您处理啦~' });
  } catch (e: any) {
    messages.value.push({ role: 'assistant', content: '网络异常，请稍后再试' });
  } finally {
    sending.value = false;
  }
}
</script>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  /* 底部有 van-tabbar（约 50px），必须留出高度，否则输入框被遮住点不到 */
  height: calc(100vh - 50px);
  background: var(--wm-bg-page);
}

/* ---------------- 会话区 ---------------- */
.chat-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: var(--wm-space-3);
}

.chat-hero {
  display: flex;
  align-items: center;
  gap: var(--wm-space-3);
  margin-bottom: var(--wm-space-3);
  padding: var(--wm-space-4);
  background: var(--wm-bg-card);
  border-radius: var(--wm-radius-lg);
  box-shadow: var(--wm-shadow-1);
}

.chat-hero-icon {
  flex: 0 0 40px;
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border-radius: var(--wm-radius-full);
  background: var(--wm-primary-50);
  color: var(--wm-primary);
}

.chat-hero-text {
  flex: 1;
  min-width: 0;
}

.chat-hero-title {
  font-size: var(--wm-font-lg);
  font-weight: 600;
  line-height: var(--wm-leading-tight);
  color: var(--wm-text-1);
}

.chat-hero-sub {
  margin-top: var(--wm-space-1);
  font-size: var(--wm-font-sm);
  color: var(--wm-text-3);
}

.msg-list {
  display: flex;
  flex-direction: column;
  gap: var(--wm-space-3);
}

.message {
  display: flex;
  align-items: flex-end;
  gap: var(--wm-space-2);
  min-width: 0;
}

.message.user {
  justify-content: flex-end;
}

.message.assistant {
  justify-content: flex-start;
}

.bubble-avatar {
  flex: 0 0 26px;
  width: 26px;
  height: 26px;
  display: grid;
  place-items: center;
  border-radius: var(--wm-radius-full);
  background: var(--wm-primary-50);
  color: var(--wm-primary);
}

.bubble {
  max-width: 72%;
  padding: var(--wm-space-3) var(--wm-space-4);
  border-radius: var(--wm-radius-lg);
  font-size: var(--wm-font-md);
  line-height: var(--wm-leading-normal);
  word-break: break-all;
}

.message.user .bubble {
  background: var(--wm-primary);
  color: #fff;
  border-bottom-right-radius: var(--wm-radius-sm);
}

.message.assistant .bubble {
  background: var(--wm-bg-page);
  color: var(--wm-text-2);
  border-bottom-left-radius: var(--wm-radius-sm);
}

/* ---------------- 输入区 ---------------- */
.chat-input {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: var(--wm-space-2);
  padding: var(--wm-space-2) var(--wm-space-3);
  padding-bottom: calc(var(--wm-space-2) + env(safe-area-inset-bottom));
  background: var(--wm-bg-card);
  border-top: 1px solid var(--wm-border);
}

.chat-field {
  flex: 1;
  min-width: 0;
  min-height: var(--wm-tap-min);
  padding: var(--wm-space-1) var(--wm-space-3);
  border-radius: var(--wm-radius-full);
  background: var(--wm-bg-page);
  font-size: var(--wm-font-md);
}

.send-text {
  margin-left: var(--wm-space-1);
  font-size: var(--wm-font-sm);
}

.chat-input :deep(.van-button) {
  min-height: var(--wm-tap-min);
}
</style>
