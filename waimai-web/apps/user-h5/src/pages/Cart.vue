<template>
  <div class="cart-page">
    <van-nav-bar title="确认订单" left-arrow @click-left="$router.back()" />

    <!-- 收货地址 -->
    <van-cell-group inset style="margin-top: 12px">
      <van-cell v-if="selectedAddress" :title="selectedAddress.detail" :label="`${selectedAddress.contact} ${selectedAddress.phone}`" is-link @click="showAddress = true">
        <template #right-icon>
          <van-tag v-if="!hasCoords(selectedAddress)" type="warning" size="mini">未设位置</van-tag>
        </template>
      </van-cell>
      <van-cell v-else title="请填写收货地址" is-link @click="openAddressPicker" />
    </van-cell-group>

    <!-- 商品 -->
    <van-cell-group inset style="margin-top: 12px">
      <div class="items-title">商品清单</div>
      <div class="cart-item" v-for="item in items" :key="item.dishId">
        <span>{{ item.dishName }} × {{ item.quantity }}</span>
        <span class="price">{{ (item.price * item.quantity).toFixed(2) }}</span>
      </div>
      <van-cell title="配送费" :value="`¥${deliveryFee}`" />
      <van-cell title="打包费" :value="`¥${packageFee}`" />
    </van-cell-group>

    <!-- 优惠券 -->
    <van-cell-group inset style="margin-top: 12px">
      <van-cell title="优惠券" :value="discount > 0 ? `-¥${discount}` : '暂无可用'" is-link @click="showCoupons = true" />
    </van-cell-group>

    <!-- 备注 -->
    <van-field v-model="remark" label="备注" placeholder="口味、偏好等" style="margin-top: 12px" />

    <!-- 提交 -->
    <div class="submit-bar">
      <div class="total">
        合计：<span class="price">{{ totalAmount.toFixed(2) }}</span>
      </div>
      <van-button type="primary" color="#ff6034" round :loading="submitting" @click="submit">提交订单</van-button>
    </div>

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
      <van-cell v-for="c in usableCoupons" :key="c.id" :title="c.name" @click="selectCoupon(c)">
        <template #right-icon>
          <van-icon v-if="selectedCoupon?.id === c.id" name="success" color="#ff6034" />
        </template>
      </van-cell>
      <van-empty v-if="!usableCoupons.length" description="暂无可用优惠券" />
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { showToast } from 'vant';
import AddressEditor from '@/components/AddressEditor.vue';
import { apiCartList, apiAddresses, apiOrderCreate, apiUsableCoupons } from '@/api';
import type { CartItem } from '@waimai/shared';

const router = useRouter();
const items = ref<CartItem[]>([]);
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
const deliveryFee = ref(0);
const packageFee = ref(0);
const merchantId = ref<number | null>(null);

const discount = computed(() => {
  if (!selectedCoupon.value) return 0;
  return selectedCoupon.value.discountAmount || 0;
});

const dishAmount = computed(() =>
  items.value.reduce((sum, i) => sum + i.price * i.quantity, 0)
);

const totalAmount = computed(() =>
  dishAmount.value + deliveryFee.value + packageFee.value - discount.value
);

function hasCoords(a: any) {
  return a && a.lng != null && a.lat != null && Number(a.lng) !== 0 && Number(a.lat) !== 0;
}

function openAddressPicker() {
  showAddress.value = true;
}

function chooseAddress(a: any) {
  selectedAddress.value = a;
  showAddress.value = false;
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
}

function selectCoupon(c: any) {
  selectedCoupon.value = c;
  showCoupons.value = false;
}

async function submit() {
  if (!selectedAddress.value) {
    showToast('请填写收货地址');
    showAddress.value = true;
    return;
  }
  if (!selectedAddress.value.detail || !String(selectedAddress.value.detail).trim()) {
    showToast('请填写详细地址（方便骑手送达）');
    return;
  }
  submitting.value = true;
  try {
    const order: any = await apiOrderCreate({
      merchantId: merchantId.value,
      addressId: selectedAddress.value.id,
      userCouponId: selectedCoupon.value?.userCouponId,
      remark: remark.value,
    });
    const orderId = order?.id ?? order?.orderId;
    showToast('下单成功');
    router.replace(`/pay/${orderId}`);
  } catch (e: any) {
    showToast(e.message);
  } finally {
    submitting.value = false;
  }
}

async function loadAddresses() {
  try {
    const list: any = await apiAddresses();
    addresses.value = list?.records || list || [];
    if (!selectedAddress.value) {
      selectedAddress.value = addresses.value.find((a) => a.isDefault === 1) || addresses.value[0] || null;
    }
  } catch {}
}

async function load() {
  try {
    const data: any = await apiCartList();
    items.value = data.records || [];
    if (data.merchantId) merchantId.value = data.merchantId;
    if (data.deliveryFee !== undefined && data.deliveryFee !== null) deliveryFee.value = data.deliveryFee;
    if (data.packageFee !== undefined && data.packageFee !== null) packageFee.value = data.packageFee;
    if (data.merchantId) {
      const coupons: any = await apiUsableCoupons(data.merchantId, dishAmount.value);
      usableCoupons.value = coupons || [];
    }
  } catch (e: any) {
    showToast(e.message);
  }
  await loadAddresses();
}

onMounted(load);
</script>

<style scoped>
.cart-page {
  padding-bottom: 70px;
  min-height: 100vh;
  background: #f5f5f5;
}
.items-title {
  padding: 12px 16px 4px;
  font-weight: 600;
  color: #333;
}
.cart-item {
  display: flex;
  justify-content: space-between;
  padding: 8px 16px;
  font-size: 14px;
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
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.05);
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
