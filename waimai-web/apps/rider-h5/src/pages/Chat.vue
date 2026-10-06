<template>
  <div class="chat-page">
    <van-nav-bar title="订单沟通" left-arrow @click-left="goBack">
      <template #right>
        <van-icon name="service-o" size="18" @click="showMembers = true" />
      </template>
    </van-nav-bar>

    <div class="party-bar">
      <span class="dot"></span>
      <span class="party-text">{{ memberText }}</span>
    </div>

    <!-- 送达后 30 分钟窗口：到点后禁言，说明原因 -->
    <van-notice-bar
      v-if="session && session.chatOpen === false"
      left-icon="warning-o"
      color="#ed6a0c"
      background="#fffbe8"
      text="沟通已关闭：订单送达后 30 分钟内可反馈问题，超时请联系客服"
    />

    <div class="msg-list" ref="listRef">
      <div v-if="!loading && !records.length" class="empty">
        <van-empty description="还没有消息，有问题可以直接问商家或顾客" />
      </div>

      <div
        v-for="m in records"
        :key="m.id"
        class="msg-row"
        :class="{ mine: m.senderRole === 'RIDER', sys: m.senderRole === 'SYSTEM' }"
      >
        <div v-if="m.senderRole === 'SYSTEM'" class="bubble sys">
          {{ m.content }}
        </div>

        <template v-else>
          <div class="avatar">{{ avatarChar(m) }}</div>
          <div class="bubble-wrap">
            <div class="who">{{ m.senderRole === 'RIDER' ? '我' : roleLabel(m.senderRole) }}</div>
            <div class="bubble">
              <!-- 图片 -->
              <div v-if="m.msgType === 'IMAGE'" class="imgs">
                <img v-for="(u, i) in imagesOf(m)" :key="i" :src="u" class="img" @click="preview(imagesOf(m), i)" />
              </div>
              <!-- 订单卡片（系统自动发的，用户/商家也能主动发） -->
              <div v-else-if="m.msgType === 'ORDER'" class="card">
                <div class="card-t">{{ m.payload?.merchantName || '订单' }}</div>
                <div class="card-s">{{ m.payload?.orderNo }} · ¥{{ m.payload?.payAmount }}</div>
              </div>
              <!-- 工单卡片（只读：骑手不参与资金处置） -->
              <div v-else-if="m.msgType === 'TICKET'" class="card ticket">
                <div class="card-t">{{ m.payload?.typeText }} ¥{{ m.payload?.amount }}</div>
                <div class="card-s">{{ m.payload?.reason }}</div>
                <div class="card-tag">{{ m.payload?.statusText }}</div>
              </div>
              <span v-else>{{ m.content }}</span>
            </div>
            <div class="time">{{ m.createdAt }}</div>
          </div>
        </template>
      </div>
    </div>

    <!-- 成员面板 -->
    <van-popup v-model:show="showMembers" position="bottom" round>
      <div class="members">
        <div class="members-t">本单沟通对象</div>
        <div class="member"><span class="k">商家</span><span class="v">{{ session?.merchantName || '—' }}</span></div>
        <div class="member"><span class="k">骑手</span><span class="v">{{ session?.riderName || '我' }}</span></div>
        <div class="member"><span class="k">订单</span><span class="v">{{ session?.orderNo || '—' }}<span v-if="session?.payAmount != null"> · ¥{{ session.payAmount }}</span></span></div>
        <div class="member" v-if="session?.chatDeadline">
          <span class="k">沟通截止</span><span class="v">{{ session.chatDeadline }}</span>
        </div>
      </div>
    </van-popup>

    <!-- 输入区：送达 30 分钟后置灰；会话还没拿到时同样置灰 ——
         否则在 apiImOpen 返回前点发送会读 session.value.id 抛 TypeError，
         catch 里只会弹一句 "Cannot read properties of null" -->
    <div class="bar">
      <van-button
        v-if="!session || session.chatOpen === false"
        size="small"
        plain
        block
        type="primary"
        disabled
      >
        {{ session ? '沟通已关闭' : '正在进入会话…' }}
      </van-button>
      <template v-else>
        <van-button size="small" icon="plus" @click="pickImage">图片</van-button>
        <van-field
          v-model="text"
          class="field"
          placeholder="说点什么…"
          :border="false"
          @keyup.enter="send"
        />
        <van-button
          size="small"
          type="primary"
          color="#07c160"
          :loading="sending"
          @click="send"
        >
          发送
        </van-button>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { showToast, showImagePreview } from 'vant';
import { apiImOpen, apiImMessages, apiImSend, apiUpload } from '@/api';
import { WsClient } from '@waimai/shared';

const route = useRoute();
const router = useRouter();
const orderId = Number(route.params.orderId);

const session = ref<any>(null);
const records = ref<any[]>([]);
const text = ref('');
const loading = ref(true);
const sending = ref(false);
const showMembers = ref(false);
const listRef = ref<HTMLElement | null>(null);

let ws: WsClient | null = null;
let timer: number | null = null;
let lastId = 0;
let inFlight = false;

const memberText = computed(() => {
  if (!session.value) return '加载中…';
  const parts = ['顾客'];
  if (session.value.merchantName) parts.push(session.value.merchantName);
  parts.push('你');
  return parts.join(' · ');
});

function roleLabel(role: string) {
  return role === 'USER' ? '顾客' : role === 'MERCHANT' ? '商家' : '骑手';
}

/** 头像首字：自己发显示「我」，与气泡上方的身份标签保持同一套文案 */
function avatarChar(m: any): string {
  return (m.senderRole === 'RIDER' ? '我' : roleLabel(m.senderRole)).charAt(0);
}

function imagesOf(m: any): string[] {
  const arr = m?.payload?.images;
  return Array.isArray(arr) ? arr : [];
}

function preview(urls: string[], index: number) {
  showImagePreview({ images: urls, startPosition: index });
}

async function scrollBottom() {
  await nextTick();
  const el = listRef.value;
  if (el) el.scrollTop = el.scrollHeight;
}

/**
 * 增量拉取。WS 推的只是「有新消息」的信号，真正的内容仍走接口，避免两套渲染逻辑。
 *
 * 两个必要的防护：
 * 1) loading 锁：WS 推送与 5 秒轮询、以及发完消息后的主动刷新，可能在同一请求窗口内重叠，
 *    两次都会读到同一个 lastId 并 push 同一批消息 → 气泡重复渲染。
 * 2) 按 id 去重：即便有锁，也不该依赖时序，去重才是最终保证。
 */
async function load(silent = false) {
  if (!session.value?.id) return;
  if (inFlight) return;
  inFlight = true;
  if (!silent) loading.value = true;
  try {
    const data: any = await apiImMessages(session.value.id, lastId || undefined);
    if (data.session) session.value = { ...session.value, ...data.session };
    const list: any[] = data.records || [];
    if (list.length) {
      const seen = new Set(records.value.map((r: any) => r.id));
      const fresh = list.filter((r: any) => !seen.has(r.id));
      if (fresh.length) {
        records.value.push(...fresh);
        lastId = data.lastId || fresh[fresh.length - 1].id;
        scrollBottom();
      }
    }
  } catch (e: any) {
    if (!silent) showToast(e.message || '加载失败');
  } finally {
    inFlight = false;
    loading.value = false;
  }
}

async function send() {
  const content = text.value.trim();
  if (!content || sending.value) return;
  if (session.value && session.value.chatOpen === false) {
    showToast('沟通已关闭');
    return;
  }
  sending.value = true;
  try {
    await apiImSend(session.value.id, { msgType: 'TEXT', content });
    text.value = '';
    await load(true);
  } catch (e: any) {
    showToast(e.message || '发送失败');
  } finally {
    sending.value = false;
  }
}

async function pickImage() {
  // <input type=file> 藏在页面里，骑手端不引入额外上传组件
  const input = document.createElement('input');
  input.type = 'file';
  input.accept = 'image/*';
  input.onchange = async () => {
    const file = input.files?.[0];
    if (!file) return;
    try {
      const url = await apiUpload(file);
      if (!url) throw new Error('上传失败');
      await apiImSend(session.value.id, { msgType: 'IMAGE', images: [url] });
      await load(true);
    } catch (e: any) {
      showToast(e.message || '图片发送失败');
    }
  };
  input.click();
}

function goBack() {
  if (window.history.length > 1) router.back();
  else router.push('/delivering');
}

onMounted(async () => {
  try {
    session.value = await apiImOpen(orderId);
    await load();
  } catch (e: any) {
    showToast(e.message || '无法进入会话');
    goBack();
    return;
  }
  // WS 收到信号就增量拉一次；失败也能靠轮询兜住
  ws = new WsClient();
  ws.connect();
  ws.on('IM_MESSAGE', (msg) => {
    if (msg.sessionId === session.value?.id) load(true);
  });
  timer = window.setInterval(() => load(true), 5000);
});

onUnmounted(() => {
  if (timer) clearInterval(timer);
  ws?.close();
});
</script>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 50px);
  background: #f7f8fa;
}
.party-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  font-size: 12px;
  color: #666;
  background: #fff;
  border-bottom: 1px solid #eee;
}
.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #07c160;
}

.msg-list {
  flex: 1;
  overflow-y: auto;
  padding: 10px 12px;
}
.empty {
  padding-top: 60px;
}

.msg-row {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
}
.msg-row.mine {
  flex-direction: row-reverse;
}
.msg-row.sys {
  justify-content: center;
}

.avatar {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border-radius: 50%;
  background: #07c160;
  color: #fff;
  font-size: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.msg-row.mine .avatar {
  background: #1989fa;
}

.bubble-wrap {
  max-width: 72%;
}
.who {
  font-size: 11px;
  color: #999;
  margin-bottom: 3px;
}
.msg-row.mine .who {
  text-align: right;
}

.bubble {
  background: #fff;
  border-radius: 8px;
  padding: 8px 10px;
  font-size: 14px;
  line-height: 1.5;
  word-break: break-all;
}
.msg-row.mine .bubble {
  background: #95ec69;
}
.bubble.sys {
  background: transparent;
  color: #999;
  font-size: 12px;
  text-align: center;
}

.imgs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.img {
  width: 110px;
  height: 110px;
  object-fit: cover;
  border-radius: 6px;
}

.card {
  background: rgba(255, 255, 255, 0.75);
  border-radius: 6px;
  padding: 6px 8px;
  min-width: 140px;
}
.card-t {
  font-weight: 600;
  font-size: 13px;
}
.card-s {
  font-size: 12px;
  color: #666;
  margin-top: 2px;
}
.card-tag {
  display: inline-block;
  margin-top: 4px;
  font-size: 11px;
  color: #ed6a0c;
}

.time {
  font-size: 11px;
  color: #bbb;
  margin-top: 3px;
}
.msg-row.mine .time {
  text-align: right;
}

.bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px calc(8px + env(safe-area-inset-bottom));
  background: #fff;
  border-top: 1px solid #eee;
}
.field {
  flex: 1;
  padding: 6px 10px;
  background: #f7f8fa;
  border-radius: 16px;
}

.members {
  padding: 18px 16px calc(18px + env(safe-area-inset-bottom));
}
.members-t {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 12px;
}
.member {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  font-size: 13px;
  border-bottom: 1px solid #f2f3f5;
}
.member .k {
  color: #999;
}
.member .v {
  color: #333;
  max-width: 65%;
  text-align: right;
}
</style>
