<template>
  <div class="im-page">
    <van-nav-bar :title="title" left-arrow @click-left="$router.back()" />

    <div class="im-body" ref="bodyRef">
      <div v-for="m in messages" :key="m.id" :class="['msg', m.senderRole === 'USER' ? 'mine' : 'other']">
        <!-- 系统提示 -->
        <div v-if="m.senderRole === 'SYSTEM' && m.msgType !== 'ORDER'" class="sys-tip">
          <span v-if="m.msgType === 'TICKET'">工单动态</span>
          <span v-else>{{ m.content }}</span>
        </div>

        <!-- 订单卡片 -->
        <div v-else-if="m.msgType === 'ORDER'" class="order-card" @click="goOrder(m.payload?.orderId)">
          <div class="oc-head">
            <span class="oc-shop">{{ m.payload?.merchantName || '商家' }}</span>
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
            <span class="tc-type">{{ m.payload?.typeText || '工单' }}</span>
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

    <div class="im-input">
      <van-icon name="photograph" size="22" @click="pickImage" />
      <van-field v-model="text" placeholder="说点什么..." @keyup.enter="sendText" />
      <van-button size="small" type="primary" color="#ff6034" round @click="sendText">发送</van-button>
      <van-button size="small" plain round @click="showActions = true">+</van-button>
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
        <van-button block round type="primary" color="#ff6034" style="margin-top: 16px"
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

const route = useRoute();
const router = useRouter();
const sessionId = Number(route.params.id);

const messages = ref<any[]>([]);
const title = ref('联系商家');
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

/** 增量拉取：只取 lastId 之后的新消息 */
async function loadNew(initial = false) {
  try {
    const data: any = await apiImMessages(sessionId, initial ? 0 : lastId.value);
    if (data?.session?.merchantName) title.value = data.session.merchantName;
    const records = data?.records || [];
    if (initial) messages.value = records;
    else if (records.length) messages.value.push(...records);
    lastId.value = data?.lastId || lastId.value;
    if (records.length || initial) scrollBottom();
  } catch (e: any) {
    if (initial) showToast(e.message || '加载失败');
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
onMounted(async () => {
  await loadNew(true);
  timer = setInterval(() => loadNew(false), 3000);
});
onUnmounted(() => {
  if (timer) clearInterval(timer);
});
</script>

<style scoped>
.im-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
}
.im-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  background: #f5f5f5;
}
.msg {
  display: flex;
  margin-bottom: 12px;
}
.msg.mine {
  justify-content: flex-end;
}
.msg.other {
  justify-content: flex-start;
}
.sys-tip {
  margin: 0 auto;
  font-size: 12px;
  color: #999;
  background: #e6e6e6;
  padding: 2px 10px;
  border-radius: 10px;
}
.bubble {
  max-width: 72%;
  padding: 9px 12px;
  border-radius: 10px;
  font-size: 14px;
  line-height: 1.5;
  word-break: break-all;
}
.msg.mine .bubble {
  background: #ff6034;
  color: #fff;
}
.msg.other .bubble {
  background: #fff;
  color: #333;
}
.img-wrap {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.order-card {
  width: 78%;
  background: #fff;
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 13px;
}
.oc-head {
  display: flex;
  justify-content: space-between;
  font-weight: 600;
  margin-bottom: 6px;
}
.oc-status {
  color: #ff6034;
}
.oc-item {
  display: flex;
  justify-content: space-between;
  color: #666;
  margin-bottom: 2px;
}
.oc-foot {
  display: flex;
  justify-content: space-between;
  border-top: 1px solid #f0f0f0;
  margin-top: 6px;
  padding-top: 6px;
}
.oc-no {
  color: #999;
  font-size: 12px;
}
.oc-amount {
  color: #ff6034;
  font-weight: 600;
}
.ticket-card {
  width: 82%;
  background: #fff;
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 13px;
}
.tc-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}
.tc-amount {
  color: #ff6034;
}
.tc-reason {
  color: #666;
  margin: 6px 0;
  line-height: 1.5;
}
.tc-images {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.tc-reply {
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px dashed #eee;
  color: #333;
}
.im-input {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  background: #fff;
  border-top: 1px solid #eee;
}
.ticket-form {
  padding: 16px;
}
.tf-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 8px;
}
.tf-images {
  margin-top: 12px;
}
.tf-label {
  font-size: 13px;
  color: #666;
  margin-bottom: 6px;
}
</style>
