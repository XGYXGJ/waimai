<template>
  <div class="im-page">
    <van-nav-bar :title="title" left-arrow @click-left="$router.back()" />

    <!-- 三方成员：用户 / 商家 / 骑手共用同一个频道 -->
    <div class="im-party">
      <span class="pdot"></span>
      <span class="ptext">{{ partyText }}</span>
    </div>

    <!-- 送达后 30 分钟窗口：到点后禁言（服务端也会拦，这里只是提前告知） -->
    <van-notice-bar
      v-if="session && session.chatOpen === false"
      left-icon="warning-o"
      color="#ed6a0c"
      background="#fffbe8"
      text="沟通已关闭：订单送达后 30 分钟内可反馈问题，超时请联系客服"
    />

    <!-- 消息流：自己发的品牌色气泡，对方用页面底色气泡 -->
    <div class="im-body" ref="bodyRef">
      <div v-for="m in messages" :key="m.id" :class="['msg', m.senderRole === 'USER' ? 'mine' : 'other']">
        <!-- 系统提示 -->
        <div v-if="m.senderRole === 'SYSTEM' && m.msgType !== 'ORDER'" class="sys-tip">
          <van-icon name="volume-o" size="12" />
          <span v-if="m.msgType === 'TICKET'">工单动态</span>
          <span v-else>{{ m.content }}</span>
        </div>

        <!-- 对方身份标签：商家 / 骑手 -->
        <div v-else-if="m.senderRole !== 'USER'" class="who">{{ roleLabel(m.senderRole) }}</div>

        <!-- 订单卡片 -->
        <div v-else-if="m.msgType === 'ORDER'" class="order-card" @click="goOrder(m.payload?.orderId)">
          <div class="oc-head">
            <span class="oc-shop">
              <van-icon name="shop-o" size="14" />
              <span class="oc-shop-name">{{ m.payload?.merchantName || '商家' }}</span>
            </span>
            <span class="oc-status">{{ orderStatusText(m.payload?.status) }}</span>
          </div>
          <div class="oc-item" v-for="(it, i) in m.payload?.items || []" :key="i">
            <span class="oc-name">{{ it.dishName }}</span>
            <span class="oc-qty">x{{ it.quantity }}</span>
          </div>
          <div class="oc-foot">
            <span class="oc-no">单号 {{ m.payload?.orderNo }}</span>
            <span class="oc-amount">¥{{ m.payload?.payAmount }}</span>
          </div>
        </div>

        <!-- 工单卡片 -->
        <div v-else-if="m.msgType === 'TICKET'" class="ticket-card">
          <div class="tc-head">
            <span class="tc-type">
              <van-icon name="records" size="14" />
              <span class="tc-type-text">{{ m.payload?.typeText || '工单' }}</span>
            </span>
            <span class="tc-amount">¥{{ m.payload?.amount ?? 0 }}</span>
            <van-tag :type="ticketTagType(m.payload?.status)" size="medium">
              {{ m.payload?.statusText || '' }}
            </van-tag>
          </div>
          <div class="tc-reason">{{ m.payload?.reason }}</div>
          <div class="tc-images" v-if="(m.payload?.images || []).length">
            <van-image
              v-for="(img, i) in m.payload.images"
              :key="i" :src="img" width="64" height="64" fit="cover" radius="4"
              @click="preview(m.payload.images, i)" />
          </div>
          <div class="tc-reply" v-if="m.payload?.merchantReply">
            商家回复：{{ m.payload.merchantReply }}
          </div>
        </div>

        <!-- 图片 -->
        <div v-else-if="m.msgType === 'IMAGE'" class="img-wrap">
          <van-image
            v-for="(img, i) in m.payload?.images || []"
            :key="i" :src="img" width="120" height="120" fit="cover" radius="6"
            @click="preview(m.payload.images, i)" />
        </div>

        <!-- 文本 -->
        <div v-else class="bubble">{{ m.content }}</div>
      </div>
    </div>

    <!-- 输入区：固定在底部，带安全区留白，保证不被 tabbar / 手势条挡住 -->
    <div class="im-input" v-if="session && session.chatOpen !== false">
      <button class="im-tool" type="button" aria-label="发送图片" @click="pickImage">
        <van-icon name="photograph" size="22" />
      </button>
      <van-field v-model="text" class="im-field" placeholder="说点什么..." @keyup.enter="sendText" />
      <van-button size="small" type="primary" round @click="sendText">发送</van-button>
      <button class="im-tool im-tool--more" type="button" aria-label="更多操作" @click="showActions = true">
        <van-icon name="plus" size="18" />
      </button>
    </div>
    <!-- 送达 30 分钟后：只读 -->
    <div v-else class="im-input im-input--closed">
      <span>沟通已关闭，如需帮助请联系客服</span>
    </div>

    <!-- 隐藏的上传控件 -->
    <van-uploader ref="uploaderRef" :after-read="afterRead" accept="image/*" :preview-image="false"
      style="display: none" />

    <!-- 更多操作 -->
    <van-action-sheet v-model:show="showActions" :actions="actions" @select="onAction" cancel-text="取消" />

    <!-- 发起工单 -->
    <van-popup v-model:show="showTicket" position="bottom" :style="{ height: '70%' }">
      <div class="ticket-form">
        <div class="tf-title">发起售后工单</div>
        <van-field label="类型" :model-value="typeText" readonly is-link @click="showTypePicker = true" />
        <van-field v-model="form.amount" label="金额" type="number" placeholder="申请金额（元）" />
        <van-field v-model="form.reason" label="原因" type="textarea" rows="3"
          placeholder="请描述具体问题，便于商家处理" />
        <div class="tf-images">
          <div class="tf-label">凭证照片</div>
          <van-uploader v-model="form.files" multiple :max-count="6" accept="image/*" />
        </div>
        <van-button block round type="primary" class="tf-submit"
          :loading="submitting" @click="submitTicket">提交工单</van-button>
      </div>
    </van-popup>

    <van-action-sheet v-model:show="showTypePicker" :actions="typeOptions" @select="onType" cancel-text="取消" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { showToast, showImagePreview } from 'vant';
import { apiImMessages, apiImSend, apiImTicketCreate, apiUpload } from '@/api';
import { WsClient } from '@waimai/shared';

const route = useRoute();
const router = useRouter();
const sessionId = Number(route.params.id);

const messages = ref<any[]>([]);
const title = ref('联系商家');
const session = ref<any>(null);
const text = ref('');
const lastId = ref(0);
const bodyRef = ref<HTMLElement>();
const showActions = ref(false);
const showTicket = ref(false);
const showTypePicker = ref(false);
const submitting = ref(false);
const uploaderRef = ref<any>();
const sending = ref(false);

const actions = [
  { name: '发送订单卡片' },
  { name: '发起退款/赔偿工单' },
  { name: '发送图片' },
];
const typeOptions = [
  { name: '退款', value: 'REFUND' },
  { name: '赔偿', value: 'COMPENSATE' },
  { name: '补发', value: 'REISSUE' },
  { name: '其他', value: 'OTHER' },
];
const form = ref<any>({ type: 'REFUND', amount: '', reason: '', files: [] });
const typeText = computed(
  () => typeOptions.find((t) => t.value === form.value.type)?.name || '退款'
);

const orderStatusMap: Record<string, string> = {
  PENDING_PAYMENT: '待支付', PAID: '待接单', ACCEPTED: '备餐中', WAITING_PICKUP: '待取餐',
  DELIVERING: '配送中', DELIVERED: '已送达', CANCELLED: '已取消', REFUNDED: '已退款',
};
const orderStatusText = (s?: string) => orderStatusMap[s || ''] || s || '';
const ticketTagType = (s?: string) =>
  s === 'APPROVED' ? 'success' : s === 'REJECTED' || s === 'CLOSED' ? 'danger' : 'primary';

function roleLabel(role: string) {
  return role === 'MERCHANT' ? '商家' : role === 'RIDER' ? '骑手' : role;
}

/** 本单频道里的三方 */
const partyText = computed(() => {
  const parts: string[] = [];
  if (session.value?.merchantName) parts.push(session.value.merchantName);
  parts.push(session.value?.riderName ? `骑手 ${session.value.riderName}` : '待接单');
  return parts.join(' · ');
});

function preview(images: string[], index: number) {
  showImagePreview({ images, startPosition: index });
}
function goOrder(id?: number) {
  if (id) router.push(`/orders/${id}`);
}

async function scrollBottom() {
  await nextTick();
  if (bodyRef.value) bodyRef.value.scrollTop = bodyRef.value.scrollHeight;
}

/**
 * 增量拉取：只取 lastId 之后的新消息。
 *
 * 加锁 + 按 id 去重：WS 推送与 3 秒轮询、以及发完消息后的主动刷新可能重叠，
 * 两次都读到同一个 lastId 就会把同一批消息 push 两次（气泡重复）。
 */
async function loadNew(initial = false) {
  if (imInFlight) return;
  imInFlight = true;
  try {
    const data: any = await apiImMessages(sessionId, initial ? 0 : lastId.value);
    if (data?.session) {
      session.value = { ...session.value, ...data.session };
      if (data.session.merchantName) title.value = data.session.merchantName;
    }
    const records: any[] = data?.records || [];
    if (initial) {
      messages.value = records;
      lastId.value = data?.lastId || lastId.value;
      scrollBottom();
      return;
    }
    if (records.length) {
      const seen = new Set(messages.value.map((m: any) => m.id));
      const fresh = records.filter((m: any) => !seen.has(m.id));
      if (fresh.length) {
        messages.value.push(...fresh);
        lastId.value = data?.lastId || fresh[fresh.length - 1].id;
        scrollBottom();
      }
    }
  } catch (e: any) {
    if (initial) showToast(e.message || '加载失败');
  } finally {
    imInFlight = false;
  }
}

async function sendText() {
  const content = text.value.trim();
  if (!content || sending.value) return;
  sending.value = true;
  text.value = '';
  try {
    await apiImSend(sessionId, { msgType: 'TEXT', content });
    await loadNew();
  } catch (e: any) {
    showToast(e.message || '发送失败');
  } finally {
    sending.value = false;
  }
}

function pickImage() {
  uploaderRef.value?.chooseFile?.();
}

async function afterRead(file: any) {
  const files = Array.isArray(file) ? file : [file];
  try {
    showToast({ type: 'loading', message: '上传中', duration: 0 } as any);
    const urls: string[] = [];
    for (const f of files) {
      const url = await apiUpload(f.file);
      if (url) urls.push(url);
    }
    if (!urls.length) throw new Error('上传失败');
    await apiImSend(sessionId, { msgType: 'IMAGE', images: urls });
    await loadNew();
    showToast('已发送');
  } catch (e: any) {
    showToast(e.message || '上传失败');
  }
}

function onAction(item: any) {
  showActions.value = false;
  if (item.name === '发送订单卡片') {
    apiImSend(sessionId, { msgType: 'ORDER' }).then(() => loadNew()).catch((e: any) => showToast(e.message));
  } else if (item.name === '发起退款/赔偿工单') {
    showTicket.value = true;
  } else {
    pickImage();
  }
}

function onType(item: any) {
  form.value.type = item.value;
  showTypePicker.value = false;
}

async function submitTicket() {
  if (!form.value.reason.trim()) return showToast('请填写申请原因');
  submitting.value = true;
  try {
    const images: string[] = [];
    for (const f of form.value.files || []) {
      if (f.file) {
        const url = await apiUpload(f.file);
        if (url) images.push(url);
      } else if (f.url && f.url.startsWith('http')) {
        images.push(f.url);
      }
    }
    await apiImTicketCreate(sessionId, {
      type: form.value.type,
      amount: Number(form.value.amount || 0),
      reason: form.value.reason,
      images,
    });
    showToast('工单已提交');
    showTicket.value = false;
    form.value = { type: 'REFUND', amount: '', reason: '', files: [] };
    await loadNew();
  } catch (e: any) {
    showToast(e.message || '提交失败');
  } finally {
    submitting.value = false;
  }
}

let timer: any = null;
let ws: WsClient | null = null;
let imInFlight = false;   // 增量请求锁，防止 WS 与轮询重复拉同一批消息
onMounted(async () => {
  await loadNew(true);
  // 三方频道：WS 收到「有新消息」信号就立刻增量拉一次（内容仍走接口，避免两套渲染逻辑）；
  // 3 秒轮询保留作兜底（WS 断线时仍能收到消息）。
  ws = new WsClient();
  ws.connect();
  ws.on('IM_MESSAGE', (msg) => {
    if (msg.sessionId === sessionId) loadNew(false);
  });
  timer = setInterval(() => loadNew(false), 3000);
});
onUnmounted(() => {
  if (timer) clearInterval(timer);
  ws?.close();
});
</script>

<style scoped>
.im-page {
  display: flex;
  flex-direction: column;
  /* 输入区在流内固定占用底部空间，消息区 flex:1 滚动，输入框不会被挤出屏幕 */
  height: 100vh;
  background: var(--wm-bg-page);
}

/* ---------------- 三方频道标识 ---------------- */
.im-party {
  display: flex;
  align-items: center;
  gap: var(--wm-space-1);
  padding: 6px var(--wm-space-4);
  font-size: var(--wm-font-sm);
  color: var(--wm-text-2);
  background: var(--wm-bg-card);
  border-bottom: 1px solid var(--wm-border);
}
.pdot {
  width: 6px;
  height: 6px;
  flex-shrink: 0;
  border-radius: var(--wm-radius-full);
  background: #07c160;
}

/* 对方身份标签：让用户分得清这条是商家还是骑手回的 */
.who {
  margin-bottom: 3px;
  font-size: var(--wm-font-sm);
  color: var(--wm-text-3);
}

.im-input--closed {
  justify-content: center;
  color: var(--wm-text-3);
  font-size: var(--wm-font-md);
}

.chat-hint {
  margin-top: var(--wm-space-1);
  text-align: center;
  font-size: var(--wm-font-sm);
  color: var(--wm-text-3);
}

.im-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;  padding: var(--wm-space-3);
}

.msg {
  display: flex;
  margin-bottom: var(--wm-space-3);
}
.msg.mine {
  justify-content: flex-end;
}
.msg.other {
  justify-content: flex-start;
}

/* ---------------- 系统提示 ---------------- */
.sys-tip {
  display: inline-flex;
  align-items: center;
  gap: var(--wm-space-1);
  margin: 0 auto;
  padding: var(--wm-space-1) var(--wm-space-3);
  border-radius: var(--wm-radius-full);
  background: var(--wm-border);
  color: var(--wm-text-3);
  font-size: var(--wm-font-sm);
  line-height: var(--wm-leading-normal);
}

/* ---------------- 气泡 ---------------- */
.bubble {
  max-width: 72%;
  padding: var(--wm-space-3) var(--wm-space-4);
  border-radius: var(--wm-radius-lg);
  font-size: var(--wm-font-md);
  line-height: var(--wm-leading-normal);
  word-break: break-all;
}
.msg.mine .bubble {
  background: var(--wm-primary);
  color: #fff;
  border-bottom-right-radius: var(--wm-radius-sm);
}
.msg.other .bubble {
  background: var(--wm-bg-page);
  color: var(--wm-text-2);
  border-bottom-left-radius: var(--wm-radius-sm);
}

.img-wrap {
  display: flex;
  gap: var(--wm-space-2);
  flex-wrap: wrap;
}

/* ---------------- 订单卡片 ---------------- */
.order-card {
  width: 78%;
  padding: var(--wm-space-4);
  background: var(--wm-bg-card);
  border-radius: var(--wm-radius-lg);
  box-shadow: var(--wm-shadow-1);
  font-size: var(--wm-font-sm);
  cursor: pointer;
}
.oc-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--wm-space-2);
  margin-bottom: var(--wm-space-2);
  font-size: var(--wm-font-md);
  font-weight: 600;
}
.oc-shop {
  display: inline-flex;
  align-items: center;
  gap: var(--wm-space-1);
  min-width: 0;
  color: var(--wm-text-1);
}
.oc-shop-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.oc-status {
  flex-shrink: 0;
  color: var(--wm-primary);
}
.oc-item {
  display: flex;
  justify-content: space-between;
  gap: var(--wm-space-2);
  margin-bottom: var(--wm-space-1);
  color: var(--wm-text-2);
}
.oc-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.oc-qty {
  flex-shrink: 0;
  color: var(--wm-text-3);
}
.oc-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--wm-space-2);
  margin-top: var(--wm-space-2);
  padding-top: var(--wm-space-2);
  border-top: 1px solid var(--wm-border);
}
.oc-no {
  font-size: var(--wm-font-xs);
  color: var(--wm-text-4);
}
.oc-amount {
  color: var(--wm-primary);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

/* ---------------- 工单卡片 ---------------- */
.ticket-card {
  width: 82%;
  padding: var(--wm-space-4);
  background: var(--wm-bg-card);
  border-radius: var(--wm-radius-lg);
  box-shadow: var(--wm-shadow-1);
  font-size: var(--wm-font-sm);
}
.tc-head {
  display: flex;
  align-items: center;
  gap: var(--wm-space-2);
  font-size: var(--wm-font-md);
  font-weight: 600;
}
.tc-type {
  display: inline-flex;
  align-items: center;
  gap: var(--wm-space-1);
  color: var(--wm-text-1);
}
.tc-amount {
  margin-right: auto;
  color: var(--wm-primary);
  font-variant-numeric: tabular-nums;
}
.tc-reason {
  margin: var(--wm-space-2) 0;
  color: var(--wm-text-2);
  line-height: var(--wm-leading-normal);
}
.tc-images {
  display: flex;
  gap: var(--wm-space-2);
  flex-wrap: wrap;
}
.tc-reply {
  margin-top: var(--wm-space-2);
  padding-top: var(--wm-space-2);
  border-top: 1px dashed var(--wm-border);
  color: var(--wm-text-3);
  line-height: var(--wm-leading-normal);
}

/* ---------------- 输入区 ---------------- */
.im-input {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: var(--wm-space-2);
  padding: var(--wm-space-2) var(--wm-space-3);
  padding-bottom: calc(var(--wm-space-2) + env(safe-area-inset-bottom));
  background: var(--wm-bg-card);
  border-top: 1px solid var(--wm-border);
}

.im-tool {
  flex: 0 0 var(--wm-tap-min);
  width: var(--wm-tap-min);
  height: var(--wm-tap-min);
  display: grid;
  place-items: center;
  border: 0;
  border-radius: var(--wm-radius-md);
  background: transparent;
  color: var(--wm-text-3);
  cursor: pointer;
}
.im-tool:active {
  background: var(--wm-bg-page);
}
.im-tool--more {
  color: var(--wm-primary);
  border: 1px solid var(--wm-primary-200);
}

.im-input :deep(.van-field) {
  flex: 1;
  min-width: 0;
  min-height: var(--wm-tap-min);
  padding: var(--wm-space-1) var(--wm-space-3);
  border-radius: var(--wm-radius-full);
  background: var(--wm-bg-page);
  font-size: var(--wm-font-md);
}

.im-input :deep(.van-button) {
  flex-shrink: 0;
  min-height: var(--wm-tap-min);
  padding: 0 var(--wm-space-4);
}

/* ---------------- 工单表单 ---------------- */
.ticket-form {
  padding: var(--wm-space-4);
  padding-bottom: calc(var(--wm-space-4) + env(safe-area-inset-bottom));
  height: 100%;
  overflow-y: auto;
  background: var(--wm-bg-card);
}
.tf-title {
  margin-bottom: var(--wm-space-4);
  font-size: var(--wm-font-lg);
  font-weight: 600;
  color: var(--wm-text-1);
}
.tf-images {
  margin-top: var(--wm-space-3);
}
.tf-label {
  margin-bottom: var(--wm-space-2);
  font-size: var(--wm-font-md);
  color: var(--wm-text-2);
}
.tf-submit {
  margin-top: var(--wm-space-4);
  min-height: var(--wm-tap-min);
}
</style>
