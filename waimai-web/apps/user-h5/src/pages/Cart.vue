<template>
  <div class="cart-page">
    <van-nav-bar title="确认订单" left-arrow @click-left="$router.back()">
      <template #right>
        <span v-if="items.length" class="nav-clear" @click="confirmClear">清空购物车</span>
      </template>
    </van-nav-bar>

    <!-- 加载中 -->
    <van-loading v-if="loading" class="page-loading" vertical>加载中...</van-loading>

    <!-- 空购物车 -->
    <van-empty v-else-if="!items.length" description="购物车是空的">
      <van-button round type="primary" color="#ff6034" @click="$router.back()">回去逛逛</van-button>
    </van-empty>

    <template v-else>
      <!-- 店铺 -->
      <van-cell-group inset style="margin-top: 12px">
        <van-cell :title="merchantName || '商家'" icon="shop-o" :label="shopStatusLabel" />
      </van-cell-group>

      <!-- 收货地址 -->
      <van-cell-group inset style="margin-top: 12px">
        <van-cell v-if="selectedAddress" :title="selectedAddress.detail" :label="`${selectedAddress.contact} ${selectedAddress.phone}`" is-link @click="showAddress = true">
          <template #right-icon>
            <van-tag v-if="!hasCoords(selectedAddress)" type="warning" size="mini">未设位置</van-tag>
          </template>
        </van-cell>
        <van-cell v-else title="请填写收货地址" is-link @click="openAddressPicker" />
      </van-cell-group>

      <!-- 超出配送范围：下单前就拦住，别等提交才报错 -->
      <van-notice-bar
        v-if="selectedAddress && !inRange"
        wrapable
        :scrollable="false"
        left-icon="warning-o"
        color="#ed6a0c"
        background="#fffbe8"
        :text="rangeTip || '收货地址超出商家配送范围'"
        style="margin-top: 12px"
      />

      <!-- 商品 -->
      <van-cell-group inset style="margin-top: 12px">
        <div class="items-title">商品清单</div>
        <div class="cart-item" v-for="item in items" :key="item.dishId">
          <div class="ci-main">
            <div class="ci-name">
              {{ item.dishName }}
              <van-tag v-if="item.dishStatus !== undefined && item.dishStatus !== 1" type="danger" size="mini">已下架</van-tag>
              <van-tag v-else-if="isStockShort(item)" type="warning" size="mini">库存不足</van-tag>
            </div>
            <div class="ci-price">¥{{ money(item.price) }}<span class="ci-unit"> x {{ qtyOf(item) }}</span></div>
          </div>
          <van-stepper
            :model-value="qtyOf(item)"
            :min="0"
            :max="maxQty(item)"
            :disabled="item.dishStatus !== undefined && item.dishStatus !== 1"
            @change="(v: any) => onQtyChange(item, Number(v))"
          />
          <span class="ci-subtotal">¥{{ money(Number(item.price) * qtyOf(item)) }}</span>
        </div>
        <van-cell title="配送费" :value="`¥${money(deliveryFee)}`">
          <template #label>
            <span v-if="distanceKm !== null">配送距离约 {{ Number(distanceKm).toFixed(1) }} km（超出 {{ Number(radiusKm).toFixed(1) }} km 不配送）</span>
          </template>
        </van-cell>
        <van-cell title="打包费" :value="`¥${money(packageFee)}`" />
      </van-cell-group>

      <!-- 优惠券 -->
      <van-cell-group inset style="margin-top: 12px">
        <van-cell
          title="优惠券"
          :value="couponValueText"
          is-link
          @click="showCoupons = true"
        />
        <van-cell v-if="minOrderAmount > 0" title="起送价" :value="`¥${money(minOrderAmount)}`" />
        <van-cell
          v-if="gapToMin > 0"
          title="还差"
          :value="`¥${money(gapToMin)} 起送`"
          value-class="warn-text"
        />
      </van-cell-group>

      <!-- 备注 -->
      <van-field v-model="remark" label="备注" placeholder="口味、偏好等" maxlength="100" show-word-limit style="margin-top: 12px" />

      <!-- 提交 -->
      <div class="submit-bar">
        <div class="total">
          合计：<span class="price">¥{{ money(totalAmount) }}</span>
        </div>
        <van-button
          type="primary"
          color="#ff6034"
          round
          :loading="submitting"
          :disabled="!canSubmit"
          @click="submit"
        >{{ submitText }}</van-button>
      </div>
    </template>

    <!-- 地址选择 -->
    <van-popup v-model:show="showAddress" position="bottom" round :style="{ maxHeight: '70%' }">
      <div class="popup-title">选择收货地址</div>
      <div class="popup-body">
        <van-cell
          v-for="a in addresses"
          :key="a.id"
          :title="a.detail"
          :label="`${a.contact} ${a.phone}`"
          @click="chooseAddress(a)"
        >
          <template #right-icon>
            <van-icon v-if="selectedAddress?.id === a.id" name="success" color="#ff6034" />
            <van-icon name="edit" color="#969799" style="margin-left: 12px" @click.stop="editAddress(a)" />
          </template>
        </van-cell>
        <van-empty v-if="!addresses.length" description="暂无地址，请新增" />
        <div class="popup-actions">
          <van-button round block type="primary" color="#ff6034" icon="plus" @click="addAddress">新增收货地址</van-button>
        </div>
      </div>
    </van-popup>

    <!-- 地址编辑（下单时可直接填写详细地址） -->
    <van-popup v-model:show="showEditor" position="bottom" round :style="{ height: '90%' }">
      <div class="popup-title">{{ editingAddress?.id ? '编辑收货地址' : '新增收货地址' }}</div>
      <div class="popup-body">
        <AddressEditor v-if="showEditor" :address="editingAddress" @saved="onAddressSaved" />
      </div>
    </van-popup>

    <!-- 优惠券选择 -->
    <van-popup v-model:show="showCoupons" position="bottom" round :style="{ maxHeight: '60%' }">
      <div class="popup-title">选择优惠券</div>
      <van-cell title="不使用优惠券" clickable @click="clearCoupon">
        <template #right-icon>
          <van-icon v-if="!selectedCoupon" name="success" color="#ff6034" />
        </template>
      </van-cell>
      <van-cell
        v-for="c in usableCoupons"
        :key="c.couponId"
        clickable
        :title="c.name"
        :label="couponLabel(c)"
        @click="selectCoupon(c)"
      >
        <template #right-icon>
          <van-icon v-if="selectedCoupon?.couponId === c.couponId" name="success" color="#ff6034" />
        </template>
      </van-cell>
      <van-empty v-if="!usableCoupons.length" description="暂无满足条件的优惠券">
        <van-button round plain type="primary" color="#ff6034" @click="goCouponHall">
          去领券中心看看
        </van-button>
      </van-empty>
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { showToast, showConfirmDialog } from 'vant';
import AddressEditor from '@/components/AddressEditor.vue';
import {
  apiCartList, apiCartClear, apiAddresses, apiOrderCreate, apiOrderPreview, apiReceiveCoupon, apiCartUpdate,
} from '@/api';

/** 业务错误码（与后端 ResultCode 对应）：用来把「重复提交」「状态已变更」分流处理 */
const CODE_REPEAT_SUBMIT = 40013;
const CODE_ORDER_STATUS_CHANGED = 40010;
const CODE_OUT_OF_RANGE = 40014;

const router = useRouter();
const items = ref<any[]>([]);
const addresses = ref<any[]>([]);
const selectedAddress = ref<any>(null);
const usableCoupons = ref<any[]>([]);
const selectedCoupon = ref<any>(null);
const remark = ref('');
const showAddress = ref(false);
const showEditor = ref(false);
const showCoupons = ref(false);
const editingAddress = ref<any>(null);
const submitting = ref(false);
const loading = ref(true);
const deliveryFee = ref(0);
const packageFee = ref(0);
const merchantId = ref<number | null>(null);
const merchantName = ref('');
const openStatus = ref(1);
const minOrderAmount = ref(0);
// 后端 /order/preview 返回的菜品金额。之前这里用前端 reduce 自己算，
// 口径和落库金额未必一致；现在一律以后端为准。
const dishAmount = ref(0);
/** 配送距离/商家配送范围/是否在范围内 —— 全部由后端按所选地址算出 */
const distanceKm = ref<number | null>(null);
const radiusKm = ref(0);
const inRange = ref(true);
const rangeTip = ref('');
/**
 * stepper 的目标数量。用独立的 map 而不是直接改 items，
 * 这样请求失败时把值改回去能立刻反映到 stepper 上（items 值没变就不会重渲染）。
 */
const qtyMap = ref<Record<number, number>>({});
/** 用户主动点了「不使用优惠券」，之后调整数量就不要自动帮他选券了 */
const couponOptOut = ref(false);
/**
 * 下单幂等令牌：一个「下单意图」始终复用同一个 token。
 * 连点 / 弱网重试时后端靠它保证只落一笔订单；
 * 下单成功后页面会跳走，下次进来重新生成。
 */
const clientToken = ref(newToken());

function newToken() {
  const c = (globalThis as any).crypto;
  if (c?.randomUUID) return String(c.randomUUID()).replace(/-/g, '');
  return `t${Date.now().toString(36)}${Math.random().toString(36).slice(2, 12)}`;
}

const MAX_QTY = 99;

function money(v: any) {
  return Number(v || 0).toFixed(2);
}

const discount = computed(() => {
  const c = selectedCoupon.value;
  if (!c) return 0;
  // 必须用 discountedAmount（后端按门槛/折扣率算好的真实优惠额）。
  // discountAmount 只是券面额：折扣券(type=2) 该字段为 null，
  // 用它会导致「选了 8 折券，合计一分钱没减」。
  return Number(c.discountedAmount ?? c.discountAmount ?? 0);
});

const totalAmount = computed(() =>
  Math.max(0, dishAmount.value + deliveryFee.value + packageFee.value - discount.value)
);

const gapToMin = computed(() => Math.max(0, minOrderAmount.value - dishAmount.value));

const hasOfflineItem = computed(() => items.value.some((i) => i.dishStatus !== undefined && i.dishStatus !== 1));

const shopClosed = computed(() => openStatus.value !== 1);

const canSubmit = computed(() =>
  !submitting.value
  && items.value.length > 0
  && !hasOfflineItem.value
  && !shopClosed.value
  && gapToMin.value <= 0
  && !!selectedAddress.value
  && inRange.value
);

const submitText = computed(() => {
  if (!items.value.length) return '购物车为空';
  if (shopClosed.value) return '店铺休息中';
  if (hasOfflineItem.value) return '有商品已下架';
  if (gapToMin.value > 0) return `差 ¥${money(gapToMin.value)} 起送`;
  if (selectedAddress.value && !inRange.value) return '超出配送范围';
  return '提交订单';
});

const shopStatusLabel = computed(() => {
  const parts: string[] = [];
  if (openStatus.value !== 1) parts.push('店铺休息中，暂不能下单');
  if (minOrderAmount.value > 0) parts.push(`起送 ¥${money(minOrderAmount.value)}`);
  if (deliveryFee.value > 0) parts.push(`配送 ¥${money(deliveryFee.value)}`);
  return parts.join(' · ');
});

const couponValueText = computed(() => {
  if (discount.value > 0) return `-¥${money(discount.value)}`;
  if (usableCoupons.value.length) return `可用 ${usableCoupons.value.length} 张`;
  return '暂无可用';
});

/* ---------- 数量增减 ---------- */

function qtyOf(item: any) {
  const v = qtyMap.value[item.dishId];
  return v === undefined ? Number(item.quantity || 0) : v;
}

function maxQty(item: any) {
  const stock = Number(item.stock ?? MAX_QTY);
  if (!Number.isFinite(stock) || stock <= 0) return MAX_QTY;
  return Math.max(1, Math.min(stock, MAX_QTY));
}

function isStockShort(item: any) {
  const stock = Number(item.stock ?? 0);
  return stock > 0 && qtyOf(item) > stock;
}

function setQty(dishId: number, qty: number) {
  const next = { ...qtyMap.value };
  if (qty > 0) next[dishId] = qty;
  else delete next[dishId];
  qtyMap.value = next;
}

/** 数量变化：乐观更新 → 调后端（set 语义）→ 重拉预览；失败回滚 */
async function onQtyChange(item: any, v: number) {
  if (submitting.value) return;
  const qty = Math.max(0, Math.min(Number(v) || 0, maxQty(item)));
  const old = qtyOf(item);
  if (qty === old) return;
  setQty(item.dishId, qty);
  try {
    await apiCartUpdate(item.dishId, qty);
    await refreshAfterCartChange();
  } catch (e: any) {
    setQty(item.dishId, old);
    showToast(e.message || '修改数量失败');
  }
}

/** 金额/优惠券都是后端算的，改完数量必须重拉，否则合计还是旧的 */
async function refreshAfterCartChange() {
  await fetchPreview();
  if (!items.value.length) {
    selectedCoupon.value = null;
    usableCoupons.value = [];
    return;
  }
  reconcileCoupon();
}

/** 数量变了之后，选中的券可能不再满足门槛 —— 要么同步成新的券对象，要么失效清掉 */
function reconcileCoupon() {
  const c = selectedCoupon.value;
  if (c) {
    const hit = usableCoupons.value.find((x: any) => x.couponId === c.couponId);
    if (hit) {
      // 不能直接沿用旧对象：门槛变了 discountedAmount 也变了，合计会算错
      selectedCoupon.value = hit;
      return;
    }
    selectedCoupon.value = null;
  }
  if (!couponOptOut.value) autoPickCoupon();
}

/* ---------- 优惠券 ---------- */

/** 券面描述：告诉用户「这券能减多少 / 还没领」 */
function couponLabel(c: any) {
  const d = Number(c.discountedAmount ?? 0);
  const rule = Number(c.thresholdAmount) > 0
    ? `满 ${c.thresholdAmount} 减 ${d}`
    : `立减 ${d}`;
  return c.received ? rule : `${rule}（未领取，选用后自动领取）`;
}

function clearCoupon() {
  couponOptOut.value = true;
  selectedCoupon.value = null;
  showCoupons.value = false;
}

function goCouponHall() {
  showCoupons.value = false;
  router.push('/coupon-hall');
}

async function selectCoupon(c: any) {
  showCoupons.value = false;
  couponOptOut.value = false;
  if (c.received) {
    selectedCoupon.value = c;
    return;
  }
  // 未领取的券必须先领：下单接口只认 userCouponId，
  // 直接选用会导致 create() 里 loadCoupon 抛「优惠券不可用」。
  try {
    await apiReceiveCoupon(c.couponId);
    await fetchPreview();
    const hit = usableCoupons.value.find((x: any) => x.couponId === c.couponId);
    if (hit) {
      selectedCoupon.value = hit;
      showToast(`已领取，立减 ¥${money(hit.discountedAmount)}`);
    } else {
      showToast('已领取，请重新选择');
    }
  } catch (e: any) {
    showToast(e.message || '领取失败');
  }
}

/**
 * 默认自动选中优惠最大的券（列表已按 discountedAmount 降序）。
 * 若这张券还没领，顺手替用户领掉——否则「金额明明够了满减却一分没减」。
 * 领取失败就退回不使用优惠券，不挡下单。
 */
async function autoPickCoupon() {
  if (couponOptOut.value) return;
  const best = usableCoupons.value[0];
  if (!best) {
    selectedCoupon.value = null;
    return;
  }
  if (best.received) {
    selectedCoupon.value = best;
    return;
  }
  try {
    await apiReceiveCoupon(best.couponId);
    await fetchPreview(); // 重新拉一次，这次该券会带上 userCouponId
    selectedCoupon.value =
      usableCoupons.value.find((x: any) => x.couponId === best.couponId) || null;
  } catch {
    selectedCoupon.value = null;
  }
}

/* ---------- 地址 ---------- */

function hasCoords(a: any) {
  return a && a.lng != null && a.lat != null && Number(a.lng) !== 0 && Number(a.lat) !== 0;
}

function openAddressPicker() {
  showAddress.value = true;
}

function chooseAddress(a: any) {
  selectedAddress.value = a;
  showAddress.value = false;
  // 配送费随收货地址变化（按距离计费），换地址必须重新预览
  fetchPreview().catch(() => {});
}

function addAddress() {
  editingAddress.value = null;
  showAddress.value = false;
  showEditor.value = true;
}

function editAddress(a: any) {
  editingAddress.value = a;
  showAddress.value = false;
  showEditor.value = true;
}

async function onAddressSaved(id: number) {
  showEditor.value = false;
  await loadAddresses();
  const hit = addresses.value.find((a) => a.id === id);
  if (hit) selectedAddress.value = hit;
  showAddress.value = true;
  fetchPreview().catch(() => {});
}

/* ---------- 下单 ---------- */

async function submit() {
  if (!items.value.length) {
    showToast('购物车是空的');
    return;
  }
  if (shopClosed.value) {
    showToast('店铺休息中，暂不能下单');
    return;
  }
  if (hasOfflineItem.value) {
    showToast('有商品已下架，请先移除');
    return;
  }
  if (gapToMin.value > 0) {
    showToast(`还差 ¥${money(gapToMin.value)} 起送`);
    return;
  }
  if (!selectedAddress.value) {
    showToast('请填写收货地址');
    showAddress.value = true;
    return;
  }
  if (!inRange.value) {
    showToast(rangeTip.value || '收货地址超出商家配送范围，请更换地址');
    return;
  }
  if (!selectedAddress.value.detail || !String(selectedAddress.value.detail).trim()) {
    showToast('请填写详细地址（方便骑手送达）');
    return;
  }
  submitting.value = true;
  try {
    // 兜底：万一选中的券还没领（例如自动领取那一步失败了），这里再补一次，
    // 否则后端 loadCoupon 找不到 userCouponId 会直接抛「优惠券不可用」。
    let couponId = selectedCoupon.value?.userCouponId;
    if (selectedCoupon.value && !selectedCoupon.value.received) {
      try {
        await apiReceiveCoupon(selectedCoupon.value.couponId);
        await fetchPreview();
        couponId = usableCoupons.value
          .find((x: any) => x.couponId === selectedCoupon.value.couponId)?.userCouponId;
      } catch {
        couponId = undefined; // 领不到就按不使用优惠券下单
      }
    }
    const order: any = await apiOrderCreate({
      merchantId: merchantId.value,
      addressId: selectedAddress.value.id,
      userCouponId: couponId,
      remark: remark.value,
      clientToken: clientToken.value,
    });
    const orderId = order?.id ?? order?.orderId;
    if (!orderId) {
      // 别让用户跳到一个 /pay/undefined 的死页面
      showToast('下单成功，请到订单列表查看');
      router.replace('/orders');
      return;
    }
    // idempotent=true 说明这一单其实之前已经提交过（连点/弱网重试命中幂等），
    // 不重复提示「下单成功」，直接带去支付页。
    showToast(order?.idempotent ? '该订单已提交，正在跳转' : '下单成功');
    router.replace(`/pay/${orderId}`);
  } catch (e: any) {
    if (e?.code === CODE_REPEAT_SUBMIT) {
      // 并发的另一笔请求刚刚落库：订单已经是存在的，别让用户再点一次
      showToast(e.message || '订单已提交');
      setTimeout(() => router.replace('/orders'), 800);
      return;
    }
    showToast(e.message || '下单失败');
    if (e?.code === CODE_OUT_OF_RANGE) {
      // 服务端判定超范围（例如地址改了但前端还没刷新）：同步一次真实状态
      fetchPreview().catch(() => {});
      return;
    }
    if (e?.code === CODE_ORDER_STATUS_CHANGED) {
      router.replace('/orders');
      return;
    }
    // 下单失败多半是库存被抢完了，刷新一下让用户看到最新数量/总价
    try { await refreshAfterCartChange(); } catch {}
  } finally {
    submitting.value = false;
  }
}

/* ---------- 清空购物车 ---------- */

async function confirmClear() {
  try {
    await showConfirmDialog({
      title: '清空购物车',
      message: '确定要清空购物车吗？清空后需要重新选购。',
      confirmButtonText: '清空',
      confirmButtonColor: '#ee0a24',
    });
  } catch {
    return; // 用户点了取消
  }
  try {
    await apiCartClear();
    items.value = [];
    qtyMap.value = {};
    usableCoupons.value = [];
    selectedCoupon.value = null;
    couponOptOut.value = false;
    dishAmount.value = 0;
    showToast('已清空');
  } catch (e: any) {
    showToast(e.message || '清空失败');
  }
}

/* ---------- 加载 ---------- */

async function loadAddresses() {
  try {
    const list: any = await apiAddresses();
    addresses.value = list?.records || list || [];
    if (!selectedAddress.value) {
      selectedAddress.value = addresses.value.find((a) => a.isDefault === 1) || addresses.value[0] || null;
    }
  } catch {}
}

/** 金额、动态配送费与可用券全部以后端 /order/preview 为准，避免前端自己算导致显示与实付不一致 */
async function fetchPreview() {
  const data: any = await apiOrderPreview(merchantId.value as number, selectedAddress.value?.id);
  items.value = data.items || [];
  merchantName.value = data.merchantName || merchantName.value;
  if (data.openStatus !== undefined) openStatus.value = Number(data.openStatus);
  dishAmount.value = Number(data.dishAmount || 0);
  deliveryFee.value = Number(data.deliveryFee || 0);
  packageFee.value = Number(data.packageFee || 0);
  minOrderAmount.value = Number(data.minOrderAmount || 0);
  // 距离与范围校验结果
  distanceKm.value = data.distanceKm === undefined || data.distanceKm === null ? null : Number(data.distanceKm);
  radiusKm.value = Number(data.deliveryRadiusKm || 0);
  inRange.value = data.inRange !== false;
  rangeTip.value = data.rangeTip || '';
  usableCoupons.value = data.couponOptions || [];
  // qtyMap 与后端对齐，避免本地残留一个后端并不存在的数量
  const next: Record<number, number> = {};
  for (const it of items.value) next[it.dishId] = Number(it.quantity || 0);
  qtyMap.value = next;
}

async function load() {
  loading.value = true;
  try {
    // 先取地址：配送费按「商家 → 收货地址」的距离算，没有地址就只能给起步费
    await loadAddresses();
    const cart: any = await apiCartList();
    items.value = cart.records || [];
    if (cart.merchantId) merchantId.value = cart.merchantId;
    if (cart.merchantName) merchantName.value = cart.merchantName;
    if (cart.openStatus !== undefined) openStatus.value = Number(cart.openStatus);
    if (merchantId.value && items.value.length) {
      await fetchPreview();
      await autoPickCoupon();
    }
  } catch (e: any) {
    showToast(e.message || '加载购物车失败');
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.cart-page {
  padding-bottom: 90px;
  min-height: 100vh;
  background: #f5f5f5;
}
.page-loading {
  padding: 60px 0;
}
.nav-clear {
  color: #969799;
  font-size: 13px;
  cursor: pointer;
}
.items-title {
  padding: 12px 16px 4px;
  font-weight: 600;
  color: #333;
}
.cart-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  font-size: 14px;
}
.ci-main {
  flex: 1;
  min-width: 0;
}
.ci-name {
  display: flex;
  align-items: center;
  gap: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ci-price {
  color: #ff6034;
  font-size: 13px;
  margin-top: 2px;
}
.ci-unit {
  color: #999;
}
.ci-subtotal {
  min-width: 56px;
  text-align: right;
  color: #333;
}
/* value-class 作用在 van-cell 内部的 value 元素上，必须用 :deep 才能命中（scoped 只到子组件根节点） */
:deep(.warn-text) {
  color: #ff6034 !important;
  font-weight: 600;
}
.submit-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: #fff;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  padding-bottom: calc(10px + env(safe-area-inset-bottom));
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.05);
  z-index: 10;
}
.popup-title {
  text-align: center;
  padding: 16px;
  font-weight: 600;
}
.popup-body {
  max-height: 70vh;
  overflow-y: auto;
  padding-bottom: 24px;
}
.popup-actions {
  margin: 16px;
}
</style>
