<template>
  <div class="chat-page">
    <van-nav-bar title="AI 智能客服" left-arrow @click-left="$router.back()" />
    <div class="chat-body">
      <div class="message" v-for="(m, i) in messages" :key="i" :class="m.role === 'user' ? 'user' : 'assistant'">
        <div class="bubble">{{ m.content }}</div>
      </div>
      <van-empty v-if="!messages.length" description="你好，我是小饿客服，有什么可以帮你？" />
    </div>
    <div class="chat-input">
      <van-field v-model="input" placeholder="输入你的问题..." @keyup.enter="send" />
      <van-button type="primary" color="#ff6034" round size="small" @click="send">发送</van-button>
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
}
.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: #f5f5f5;
}
.message {
  display: flex;
  margin-bottom: 12px;
}
.message.user {
  justify-content: flex-end;
}
.message.assistant {
  justify-content: flex-start;
}
.bubble {
  max-width: 70%;
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  word-break: break-all;
}
.message.user .bubble {
  background: #ff6034;
  color: #fff;
}
.message.assistant .bubble {
  background: #fff;
  color: #333;
}
.chat-input {
  display: flex;
  gap: 8px;
  padding: 8px 12px;
  background: #fff;
  align-items: center;
  border-top: 1px solid #eee;
}
</style>
