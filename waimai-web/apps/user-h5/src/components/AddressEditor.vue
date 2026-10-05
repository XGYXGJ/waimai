<template>
  <div class="addr-editor">
    <van-form @submit="onSubmit">
      <!-- 联系人信息 -->
      <section class="card">
        <h2 class="block-title">
          <van-icon name="user-o" size="14" />
          <span>联系人信息</span>
        </h2>
        <van-field
          v-model="form.contact"
          label="联系人"
          placeholder="收货人姓名"
          :rules="[{ required: true, message: '请填写联系人' }]"
        />
        <van-field
          v-model="form.phone"
          label="电话"
          type="tel"
          maxlength="11"
          placeholder="手机号"
          :rules="[
            { required: true, message: '请填写手机号' },
            { pattern: /^1\d{10}$/, message: '手机号格式不正确' },
          ]"
        />
        <van-field
          v-model="form.detail"
          label="详细地址"
          type="textarea"
          rows="2"
          autosize
          placeholder="小区/写字楼 + 楼栋门牌号，如：XX路8号XX小区3栋502"
          :rules="[{ required: true, message: '请填写详细地址（方便骑手送达）' }]"
        />
      </section>

      <!-- 收货位置（地图选点） -->
      <section class="card">
        <div class="block-head">
          <h2 class="block-title">
            <van-icon name="location-o" size="14" />
            <span>收货位置</span>
          </h2>
          <van-button size="small" type="primary" plain color="var(--wm-primary)" icon="location-o" @click="useCurrent">
            使用当前定位
          </van-button>
        </div>
        <p class="block-sub">用于骑手导航</p>
        <div ref="mapEl" class="map" v-show="amapReady"></div>
        <div class="map-tip" v-if="!amapReady">{{ mapTip }}</div>
        <van-field v-model="addrKeyword" label="地址搜索" placeholder="输入地址自动定位，如：XX市XX区XX路" clearable>
          <template #button>
            <van-button size="small" type="primary" color="var(--wm-primary)" :loading="searching" @click="searchAddr">搜索</van-button>
          </template>
        </van-field>
        <van-field v-model="form.lng" label="经度" type="number" placeholder="点击地图选点，或手动填写" />
        <van-field v-model="form.lat" label="纬度" type="number" placeholder="点击地图选点，或手动填写" />
      </section>

      <!-- 默认地址 -->
      <section class="card">
        <div class="switch-row">
          <span class="switch-label">设为默认地址</span>
          <van-switch v-model="isDefault" size="20" />
        </div>
      </section>

      <!-- 保存：#submit 粘在滚动区底部，始终可点，并避让安全区 -->
      <div class="submit">
        <van-button round block type="primary" color="var(--wm-primary)" native-type="submit" :loading="saving">
          保存
        </van-button>
      </div>
    </van-form>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue';
import { showToast } from 'vant';
import { loadAmap, geocodeAddress } from '@waimai/shared';
import { apiAddressSave } from '@/api';

const props = defineProps<{ address?: any }>();
const emit = defineEmits<{ (e: 'saved', id: number): void }>();

const form = ref<any>({ contact: '', phone: '', detail: '', lng: '', lat: '' });
const isDefault = ref(false);
const saving = ref(false);
const addrKeyword = ref('');
const searching = ref(false);
const amapReady = ref(false);
const mapTip = ref('地图加载中...');
const mapEl = ref<HTMLElement | null>(null);
let map: any = null;
let marker: any = null;

function reset() {
  const a = props.address || {};
  form.value = {
    contact: a.contact || '',
    phone: a.phone || '',
    detail: a.detail || '',
    lng: a.lng != null && a.lng !== '' ? String(a.lng) : '',
    lat: a.lat != null && a.lat !== '' ? String(a.lat) : '',
  };
  isDefault.value = a.isDefault === 1;
}

function setCoords(lng: number | string, lat: number | string) {
  form.value.lng = Number(lng).toFixed(6);
  form.value.lat = Number(lat).toFixed(6);
  if (marker) marker.setPosition([Number(form.value.lng), Number(form.value.lat)]);
}

async function initMap() {
  const ok = await loadAmap();
  if (!ok) {
    mapTip.value = '未配置高德地图，请手动填写经纬度';
    return;
  }
  amapReady.value = true;
  await nextTick();
  const el = mapEl.value;
  if (!el) return;
  const lng = Number(form.value.lng) || 116.397128;
  const lat = Number(form.value.lat) || 39.916527;
  map = new (window as any).AMap.Map(el, { zoom: 15, center: [lng, lat] });
  marker = new (window as any).AMap.Marker({ position: [lng, lat], draggable: true });
  map.add(marker);
  map.on('click', (e: any) => setCoords(e.lnglat.lng, e.lnglat.lat));
  marker.on('dragend', (e: any) => setCoords(e.lnglat.lng, e.lnglat.lat));
}

async function searchAddr() {
  if (!addrKeyword.value.trim()) {
    showToast('请输入要搜索的地址');
    return;
  }
  searching.value = true;
  try {
    const p = await geocodeAddress(addrKeyword.value);
    if (!p) {
      showToast('未找到该地址，请在地图上选点');
      return;
    }
    setCoords(p.lng, p.lat);
    if (map) map.setZoomAndCenter(16, [p.lng, p.lat]);
    if (!form.value.detail && p.address) form.value.detail = p.address;
    showToast('已定位到该地址');
  } finally {
    searching.value = false;
  }
}

function useCurrent() {
  if (!navigator.geolocation) {
    showToast('当前浏览器不支持定位');
    return;
  }
  showToast('定位中...');
  navigator.geolocation.getCurrentPosition(
    (pos) => {
      setCoords(pos.coords.longitude, pos.coords.latitude);
      if (map) map.setCenter([Number(form.value.lng), Number(form.value.lat)]);
      showToast('已定位到当前位置');
    },
    () => showToast('定位失败，请在地图上选点或手动填写经纬度'),
    { enableHighAccuracy: true, timeout: 8000 }
  );
}

async function onSubmit() {
  saving.value = true;
  try {
    const res: any = await apiAddressSave({
      id: props.address?.id,
      contact: form.value.contact,
      phone: form.value.phone,
      detail: form.value.detail,
      lng: form.value.lng ? Number(form.value.lng) : undefined,
      lat: form.value.lat ? Number(form.value.lat) : undefined,
      isDefault: isDefault.value ? 1 : 0,
    });
    showToast('保存成功');
    emit('saved', res?.id ?? props.address?.id);
  } catch (e: any) {
    showToast(e.message || '保存失败');
  } finally {
    saving.value = false;
  }
}

onMounted(() => {
  reset();
  initMap();
});

onUnmounted(() => {
  if (map) {
    map.destroy();
    map = null;
    marker = null;
  }
});
</script>

<style scoped>
/* ---------------- 通用卡片 ---------------- */
.card {
  margin: var(--wm-space-3);
  padding: var(--wm-space-4);
  background: var(--wm-bg-card);
  border-radius: var(--wm-radius-lg);
  box-shadow: var(--wm-shadow-1);
}

/* ---------------- 区块标题 ---------------- */
.block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--wm-space-2);
}

.block-title {
  display: inline-flex;
  align-items: center;
  gap: var(--wm-space-1);
  font-size: var(--wm-font-lg);
  font-weight: 600;
  line-height: var(--wm-leading-tight);
  color: var(--wm-text-1);
}

.block-sub {
  margin-top: var(--wm-space-1);
  font-size: var(--wm-font-sm);
  color: var(--wm-text-4);
}

/* ---------------- 表单 ---------------- */
/* 输入控件可点高度不低于 44px */
.card :deep(.van-field) {
  min-height: var(--wm-tap-min);
  padding: var(--wm-space-3) 0;
  background: transparent;
}

/* 标签用次要色，输入值用主文字色，层级清晰 */
.card :deep(.van-field__label) {
  color: var(--wm-text-3);
  font-size: var(--wm-font-md);
}

.card :deep(.van-field__control) {
  color: var(--wm-text-1);
  font-size: var(--wm-font-md);
}

.card :deep(.van-field__control)::placeholder {
  color: var(--wm-text-4);
}

/* 校验错误提示统一用语义危险色 */
.card :deep(.van-field__error-message) {
  color: var(--wm-danger);
  font-size: var(--wm-font-sm);
}

.card :deep(.van-field::after) {
  left: 0;
  right: 0;
  border-bottom-color: var(--wm-border);
}

.card :deep(.van-field:last-child::after) {
  display: none;
}

/* 次级按钮同样满足 44px 触控下限 */
.card :deep(.van-button--small) {
  min-height: var(--wm-tap-min);
  padding: 0 var(--wm-space-4);
}

/* ---------------- 地图 ---------------- */
.map {
  width: 100%;
  height: 200px;
  margin: var(--wm-space-2) 0;
  border-radius: var(--wm-radius-md);
  overflow: hidden;
  background: var(--wm-border);
}

.map-tip {
  margin-top: var(--wm-space-2);
  padding: var(--wm-space-3);
  border-radius: var(--wm-radius-md);
  background: var(--wm-bg-page);
  font-size: var(--wm-font-sm);
  color: var(--wm-text-3);
}

/* ---------------- 默认地址开关 ---------------- */
.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--wm-space-3);
  min-height: var(--wm-tap-min);
}

.switch-label {
  font-size: var(--wm-font-md);
  color: var(--wm-text-2);
}

/* ---------------- 保存 ---------------- */
.submit {
  position: sticky;
  bottom: 0;
  z-index: 5;
  margin-top: var(--wm-space-4);
  padding: var(--wm-space-3) var(--wm-space-4);
  padding-bottom: calc(var(--wm-space-3) + env(safe-area-inset-bottom));
  background: var(--wm-bg-card);
  border-top: 1px solid var(--wm-border);
}

.submit :deep(.van-button) {
  min-height: var(--wm-tap-min);
  font-size: var(--wm-font-lg);
  font-weight: 600;
}
</style>
