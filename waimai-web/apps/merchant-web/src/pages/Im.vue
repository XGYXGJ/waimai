<template>
  <div class="im-wrap">
    <el-tabs v-model="tab">
      <!-- ========== 顾客会话 ========== -->
      <el-tab-pane label="顾客会话" name="chat">
        <div class="chat-layout">
          <!-- 左：会话列表 -->
          <div class="session-list">
            <div class="sl-head">共 {{ sessions.length }} 个会话</div>
            <div
              v-for="s in sessions" :key="s.id"
              :class="['sl-item', currentId === s.id ? 'active' : '']"
              @click="select(s)"
            >
              <div class="sl-line">
                <span class="sl-name">订单 {{ s.orderNo || s.orderId }}</span>
                <el-badge :value="s.unread" :hidden="!s.unread" />
              </div>
              <div class="sl-msg">{{ s.lastMsg || '暂无消息' }}</div>
              <div class="sl-time">{{ s.lastMsgAt }}</div>
            </div>
            <el-empty v-if="!sessions.length" description="暂无顾客会话" :image-size="60" />
          </div>

          <!-- 右：聊天区 -->
          <div class="chat-box">
            <template v-if="currentId">
              <div class="cb-head">
                订单 {{ current?.orderNo || current?.orderId }}
                <el-tag size="small" type="info">顾客 #{{ current?.userId }}</el-tag>
                <el-tag size="small" :type="current?.status === 'CLOSED' ? 'danger' : 'success'">
                  {{ current?.status === 'CLOSED' ? '已关闭' : '进行中' }}
                </el-tag>
              </div>

              <div class="cb-body" ref="bodyRef">
                <div v-for="m in messages" :key="m.id" :class="['msg', m.senderRole === 'MERCHANT' ? 'mine' : 'other']">
                  <!-- 订单卡片 -->
                  <div v-if="m.msgType === 'ORDER'" class="order-card">
                    <div class="oc-head">
                      <b>订单信息</b>
                      <span>{{ orderStatusText(m.payload?.status) }}</span>
                    </div>
                    <div v-for="(it, i) in m.payload?.items || []" :key="i" class="oc-item">
                      <span>{{ it.dishName }}</span><span>x{{ it.quantity }}</span>
                    </div>
                    <div class="oc-foot">
                      <span>{{ m.payload?.orderNo }}</span>
                      <b class="price-num">¥{{ m.payload?.payAmount }}</b>
                    </div>
                  </div>

                  <!-- 工单卡片 -->
                  <div v-else-if="m.msgType === 'TICKET'" class="ticket-card">
                    <div class="tc-head">
                      <el-tag size="small" type="warning">{{ m.payload?.typeText }}</el-tag>
                      <b class="price-num">¥{{ m.payload?.amount ?? 0 }}</b>
                      <el-tag size="small" :type="tagType(m.payload?.status)">{{ m.payload?.statusText }}</el-tag>
                    </div>
                    <div class="tc-reason">{{ m.payload?.reason }}</div>
                    <div class="tc-images" v-if="(m.payload?.images || []).length">
                      <el-image
                        v-for="(img, i) in m.payload.images" :key="i" :src="img"
                        :preview-src-list="m.payload.images" :initial-index="i"
                        style="width: 64px; height: 64px; margin-right: 6px" fit="cover" />
                    </div>
                    <div class="tc-reply" v-if="m.payload?.merchantReply">处理意见：{{ m.payload.merchantReply }}</div>
                    <div class="tc-actions" v-if="m.payload?.status === 'PENDING'">
                      <el-button size="small" type="success" @click="openHandle(m.payload, 'APPROVED')">同意</el-button>
                      <el-button size="small" type="danger" @click="openHandle(m.payload, 'REJECTED')">驳回</el-button>
                      <el-button size="small" @click="openHandle(m.payload, 'PROCESSING')">标记处理中</el-button>
                    </div>
                  </div>

                  <!-- 图片 -->
                  <div v-else-if="m.msgType === 'IMAGE'" class="img-wrap">
                    <el-image
                      v-for="(img, i) in m.payload?.images || []" :key="i" :src="img"
                      :preview-src-list="m.payload.images" :initial-index="i"
                      style="width: 110px; height: 110px; margin-right: 6px" fit="cover" />
                  </div>

                  <!-- 文本 -->
                  <div v-else class="bubble">
                    <div class="bubble-role" v-if="m.senderRole === 'SYSTEM'">系统</div>
                    <div class="bubble-role" v-else-if="m.senderRole === 'USER'">顾客</div>
                    <div class="bubble-role rider" v-else-if="m.senderRole === 'RIDER'">骑手</div>
                    {{ m.content }}
                  </div>
                </div>
              </div>

              <div class="cb-input">
                <el-input v-model="text" placeholder="回复顾客/骑手..." @keyup.enter="sendText" />
                <el-upload :show-file-list="false" :http-request="uploadImage" accept="image/*">
                  <el-button>图片</el-button>
                </el-upload>
                <!-- 把当前工单卡片再发一次，等于在频道里「答复工单」 -->
                <el-button
                  v-if="currentSession?.chatOpen !== false"
                  :disabled="!hasTicket"
                  title="把最近一张工单卡片发到频道里"
                  @click="sendTicketCard"
                >
                  答复工单
                </el-button>
                <el-button type="primary" @click="sendText" :loading="sending">发送</el-button>
              </div>
              <div class="cb-hint">
                本频道由顾客、商家、骑手共用；送达后 30 分钟内可沟通
              </div>
            </template>
            <el-empty v-else description="选择左侧会话开始沟通" />
          </div>
        </div>
      </el-tab-pane>

      <!-- ========== 售后工单 ========== -->
      <el-tab-pane name="ticket">
        <template #label>
          售后工单<el-badge v-if="pendingCount" :value="pendingCount" class="tab-badge" />
        </template>
        <el-select v-model="ticketStatus" size="small" style="width: 150px; margin-bottom: 12px"
          @change="loadTickets">
          <el-option label="全部" value="" />
          <el-option label="待处理" value="PENDING" />
          <el-option label="处理中" value="PROCESSING" />
          <el-option label="已同意" value="APPROVED" />
          <el-option label="已驳回" value="REJECTED" />
        </el-select>

        <el-table :data="tickets" border size="small">
          <el-table-column prop="id" label="工单号" width="80" />
          <el-table-column label="类型" width="90">
            <template #default="{ row }">{{ row.typeText }}</template>
          </el-table-column>
          <el-table-column label="金额" width="90">
            <template #default="{ row }"><span class="price-num">¥{{ row.amount }}</span></template>
          </el-table-column>
          <el-table-column prop="reason" label="原因" min-width="180" show-overflow-tooltip />
          <el-table-column label="凭证" width="110">
            <template #default="{ row }">
              <el-image
                v-if="(row.images || []).length" :src="row.images[0]" :preview-src-list="row.images"
                style="width: 40px; height: 40px" fit="cover" />
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="tagType(row.status)">{{ row.statusText }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="merchantReply" label="处理意见" min-width="140" show-overflow-tooltip />
          <el-table-column prop="createdAt" label="申请时间" width="140" />
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button size="small" type="success" :disabled="row.status === 'APPROVED'"
                @click="openHandle(row, 'APPROVED')">同意</el-button>
              <el-button size="small" type="danger" :disabled="row.status === 'REJECTED'"
                @click="openHandle(row, 'REJECTED')">驳回</el-button>
              <el-button size="small" link @click="gotoSession(row)">查看会话</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!tickets.length" description="该状态下暂无工单" />
      </el-tab-pane>
    </el-tabs>

    <!-- 工单处理弹窗 -->
    <el-dialog v-model="showHandle" :title="handleTitle" width="420px">
      <el-input v-model="handleReply" type="textarea" :rows="3" placeholder="填写处理意见（同意 / 驳回必填）" />
      <template #footer>
        <el-button @click="showHandle = false">取消</el-button>
        <el-button type="primary" :loading="handling" @click="submitHandle">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue';
import { ElMessage } from 'element-plus';
import { WsClient } from '@waimai/shared';
import {
  apiImSessions, apiImMessages, apiImSend, apiImTickets, apiImHandleTicket, apiUpload,
} from '@/api';

const tab = ref('chat');
const sessions = ref<any[]>([]);
const messages = ref<any[]>([]);
const currentId = ref<number | null>(null);
const current = ref<any>(null);
const text = ref('');
const lastId = ref(0);
const sending = ref(false);
const bodyRef = ref<HTMLElement>();

let msgInFlight = false;   // 消息请求锁，防 WS 与轮询重复拉取
let loadToken = 0;         // 会话令牌，快速切换时丢弃过期响应

const tickets = ref<any[]>([]);
const ticketStatus = ref('');
const showHandle = ref(false);
const handleReply = ref('');
const handling = ref(false);
const handleAction = ref('APPROVED');
const handleTicketId = ref<number | null>(null);

const pendingCount = computed(
  () => tickets.value.filter((t) => t.status === 'PENDING').length
);

/** 当前会话里是否已有工单（决定「答复工单」按钮可不可点） */
const hasTicket = computed(() =>
  messages.value.some((m) => m.msgType === 'TICKET')
);
/** 服务端返回的会话快照里带 chatOpen / 骑手信息 */
const currentSession = computed(() => current.value);
const handleTitle = computed(() =>
  ({ APPROVED: '同意工单', REJECTED: '驳回工单', PROCESSING: '标记处理中', CLOSED: '关闭工单' } as any)[handleAction.value]
);

const orderStatusMap: Record<string, string> = {
  PENDING_PAYMENT: '待支付', PAID: '待接单', ACCEPTED: '备餐中', WAITING_PICKUP: '待取餐',
  DELIVERING: '配送中', DELIVERED: '已送达', CANCELLED: '已取消', REFUNDED: '已退款',
};
const orderStatusText = (s?: string) => orderStatusMap[s || ''] || s || '';
const tagType = (s?: string) =>
  s === 'APPROVED' ? 'success' : s === 'REJECTED' || s === 'CLOSED' ? 'danger' : 'warning';

async function scrollBottom() {
  await nextTick();
  if (bodyRef.value) bodyRef.value.scrollTop = bodyRef.value.scrollHeight;
}

async function loadSessions() {
  try {
    sessions.value = (await apiImSessions()) || [];
  } catch (e: any) {
    ElMessage.error(e.message || '加载会话失败');
  }
}

/**
 * 拉取当前会话的消息。
 *
 * 两处必须防：
 * 1) 请求锁 —— WS 推送与 3 秒轮询、以及发完消息后的主动刷新可能重叠，
 *    两次都读到同一个 lastId 就会 push 同一批消息（气泡重复）。
 * 2) 会话令牌 —— 快速点 A→B 时，A 的响应可能后到并覆盖 B 的内容，
 *    且 lastId 被写成 A 的最大 id，导致 B 的历史消息从此永远拉不到。
 */
async function loadMessages(initial = false) {
  if (!currentId.value) return;
  if (msgInFlight) return;
  const requestedId = currentId.value;
  const token = ++loadToken;
  msgInFlight = true;
  try {
    const data: any = await apiImMessages(requestedId, initial ? 0 : lastId.value);
    if (token !== loadToken || currentId.value !== requestedId) return;   // 已切走，丢弃
    current.value = data?.session || current.value;
    const records: any[] = data?.records || [];
    if (initial) {
      messages.value = records;
      lastId.value = data?.lastId || 0;
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
  } catch {
    /* 轮询失败静默 */
  } finally {
    if (token === loadToken) msgInFlight = false;
  }
}

async function select(s: any) {
  currentId.value = s.id;
  current.value = s;
  lastId.value = 0;
  await loadMessages(true);
  loadSessions();
}

async function sendText() {
  const content = text.value.trim();
  if (!content || !currentId.value || sending.value) return;
  sending.value = true;
  text.value = '';
  try {
    await apiImSend(currentId.value, { msgType: 'TEXT', content });
    await loadMessages();
    loadSessions();
  } catch (e: any) {
    ElMessage.error(e.message || '发送失败');
  } finally {
    sending.value = false;
  }
}

/** 商家答复工单：把最近一张工单卡片发进频道，三方都能看到处理结果 */
async function sendTicketCard() {
  if (!currentId.value) return;
  try {
    await apiImSend(currentId.value, { msgType: 'TICKET' });
    await loadMessages();
    loadSessions();
    ElMessage.success('工单卡片已发送到频道');
  } catch (e: any) {
    ElMessage.error(e.message || '发送失败');
  }
}

/** el-upload 自定义上传：先拿 URL，再作为图片消息发出 */
async function uploadImage(opts: any) {
  try {
    const res: any = await apiUpload(opts.file);
    if (!res?.url) throw new Error('上传失败');
    await apiImSend(currentId.value as number, { msgType: 'IMAGE', images: [res.url] });
    await loadMessages();
    loadSessions();
    ElMessage.success('已发送');
  } catch (e: any) {
    ElMessage.error(e.message || '上传失败');
  }
}

async function loadTickets() {
  try {
    tickets.value = (await apiImTickets(ticketStatus.value || undefined)) || [];
  } catch (e: any) {
    ElMessage.error(e.message || '加载工单失败');
  }
}

function openHandle(row: any, action: string) {
  handleTicketId.value = row.ticketId || row.id;
  handleAction.value = action;
  handleReply.value = row.merchantReply || '';
  showHandle.value = true;
}

async function submitHandle() {
  const reply = handleReply.value.trim();
  if ((handleAction.value === 'APPROVED' || handleAction.value === 'REJECTED') && !reply) {
    return ElMessage.warning('请填写处理意见');
  }
  handling.value = true;
  try {
    await apiImHandleTicket(handleTicketId.value as number, handleAction.value, reply);
    ElMessage.success('已处理');
    showHandle.value = false;
    await loadTickets();
    if (currentId.value) await loadMessages();
  } catch (e: any) {
    ElMessage.error(e.message || '处理失败');
  } finally {
    handling.value = false;
  }
}

function gotoSession(row: any) {
  tab.value = 'chat';
  const s = sessions.value.find((x) => x.id === row.sessionId);
  if (s) select(s);
}

let timer: any = null;
let ws: WsClient | null = null;
onMounted(async () => {
  await loadSessions();
  await loadTickets();
  // 三方频道：WS 收到新消息信号就立刻增量拉取；3 秒轮询保留兜底
  ws = new WsClient();
  ws.connect();
  ws.on('IM_MESSAGE', () => {
    if (currentId.value) loadMessages();
    loadSessions();
  });
  timer = setInterval(() => {
    if (currentId.value) loadMessages();
    if (tab.value === 'ticket') loadTickets();
  }, 3000);
});
onUnmounted(() => {
  if (timer) clearInterval(timer);
  ws?.close();
});
</script>

<style scoped>
.im-wrap {
  height: calc(100vh - 140px);
  display: flex;
  flex-direction: column;
}
.bubble-role.rider {
  color: #e6a23c;
}
.cb-hint {
  margin-top: 6px;
  font-size: 12px;
  color: #999;
}
.chat-layout {
  display: flex;
  gap: 12px;
  height: calc(100vh - 220px);
  min-height: 420px;
}
.session-list {
  width: 260px;
  background: #fff;
  border-radius: 6px;
  overflow-y: auto;
  padding: 8px;
}
.sl-head {
  font-size: 12px;
  color: #999;
  padding: 4px 6px;
}
.sl-item {
  padding: 8px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 4px;
}
.sl-item:hover {
  background: #f5f7fa;
}
.sl-item.active {
  background: #ecf5ff;
}
.sl-line {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.sl-name {
  font-size: 14px;
  font-weight: 600;
}
.sl-msg {
  font-size: 12px;
  color: #888;
  margin: 4px 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sl-time {
  font-size: 11px;
  color: #bbb;
}
.chat-box {
  flex: 1;
  background: #fff;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
}
.cb-head {
  padding: 10px 14px;
  border-bottom: 1px solid #eee;
  display: flex;
  gap: 8px;
  align-items: center;
}
.cb-body {
  flex: 1;
  overflow-y: auto;
  padding: 14px;
  background: #f5f5f5;
}
.msg {
  display: flex;
  margin-bottom: 12px;
}
.msg.mine {
  justify-content: flex-end;
}
.bubble {
  max-width: 70%;
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.5;
  word-break: break-all;
  background: #fff;
}
.msg.mine .bubble {
  background: #409eff;
  color: #fff;
}
.bubble-role {
  font-size: 11px;
  opacity: 0.7;
}
.order-card,
.ticket-card {
  width: 76%;
  background: #fff;
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 13px;
}
.oc-head,
.tc-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}
.oc-item {
  display: flex;
  justify-content: space-between;
  color: #666;
}
.oc-foot {
  display: flex;
  justify-content: space-between;
  border-top: 1px solid #f0f0f0;
  margin-top: 6px;
  padding-top: 6px;
}
.tc-reason {
  color: #666;
  margin-bottom: 6px;
  line-height: 1.5;
}
.tc-images {
  display: flex;
}
.tc-reply {
  margin-top: 6px;
  color: #333;
}
.tc-actions {
  margin-top: 8px;
  display: flex;
  gap: 6px;
}
.price-num {
  color: #f56c6c;
}
.cb-input {
  display: flex;
  gap: 8px;
  padding: 10px;
  border-top: 1px solid #eee;
}
.tab-badge {
  margin-left: 6px;
}
</style>
