<template>
  <div class="cart-page">
    <van-nav-bar title="确认订单" left-arrow @click-left="$router.back()" />

    <!-- 地址 -->
    <van-cell-group inset style="margin-top: 12px">
      <van-cell v-if="selectedAddress" :title="selectedAddress.detail" :label="`${selectedAddress.contact} ${selectedAddress.phone}`" is-link @click="showAddress = true" />
      <van-cell v-else title="请选择收货地址" is-link @click="showAddress = true" />
    </van-cell-group>

    <!-- 商品 -->
    <van-cell-group inset style="margin-top: 12px">
      <div class="items-title">商品清单</div>
      <div class="cart-item" v-for="item in items" :key="item.dishId">
        <span>{{ item.dishName }} × {{ item.quantity }}</span>
        <span class="price">{{ item.price * item.quantity }}</span>
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
      <van-button type="primary" color="#ff6034" round @click="submit">提交订单</van-button>
    </div>

    <!-- 地址选择 -->
    <van-popup v-model:show="showAddress" position="bottom" style="max-height: 60%">
      <div class="popup-title">选择地址</div>
      <van-cell v-for="a in addresses" :key="a.id" :title="a.detail" :label="`${a.contact} ${a.phone}`" @click="selectedAddress = a; showAddress = false">
        <template #right-icon>
          <van-icon v-if="selectedAddress?.id === a.id" name="success" color="#ff6034" />
        </template>
      </van-cell>
      <van-empty v-if="!addresses.length" description="暂无地址，请先添加" />
    </van-popup>

    <!-- 优惠券选择 -->
    <van-popup v-model:show="showCoupons" position="bottom" style="max-height: 60%">
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
import { apiCartList, apiAddresses, apiOrderCreate, apiUsableCoupons } from '@/api';
import type { CartItem, Address } from '@waimai/shared';

const router = useRouter();
const items = ref<CartItem[]>([]);
const addresses = ref<Address[]>([]);
const selectedAddress = ref<Address | null>(null);
const usableCoupons = ref<any[]>([]);
const selectedCoupon = ref<any>(null);
const remark = ref('');
const showAddress = ref(false);
const showCoupons = ref(false);
const deliveryFee = ref(0);
const packageFee = ref(1);
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

function selectCoupon(c: any) {
  selectedCoupon.value = c;
  showCoupons.value = false;
}

async function submit() {
  if (!selectedAddress.value) {
    showToast('请选择收货地址');
    return;
  }
  try {
    const order: any = await apiOrderCreate({
      merchantId: merchantId.value,
      addressId: selectedAddress.value.id,
      userCouponId: selectedCoupon.value?.userCouponId,
      remark: remark.value,
    });
    showToast('下单成功');
    router.replace(`/pay/${order.id}`);
  } catch (e: any) {
    showToast(e.message);
  }
}

async function load() {
  try {
    const data: any = await apiCartList();
    items.value = data.items || [];
    if (data.merchantId) merchantId.value = data.merchantId;
    if (data.deliveryFee !== undefined) deliveryFee.value = data.deliveryFee;
    if (data.packageFee !== undefined) packageFee.value = data.packageFee;
    if (data.merchantId) {
      const coupons: any = await apiUsableCoupons(data.merchantId, dishAmount.value);
      usableCoupons.value = coupons || [];
    }
  } catch (e: any) {
    showToast(e.message);
  }
  try {
    const list: any = await apiAddresses();
    addresses.value = list || [];
    selectedAddress.value = addresses.value.find((a) => a.isDefault === 1) || addresses.value[0] || null;
  } catch {}
}

onMounted(load);
</script>

<style scoped>
.cart-page {
  padding-bottom: 70px;
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
</style>
