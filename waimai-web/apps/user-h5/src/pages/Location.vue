<template>
  <div class="loc-page">
    <van-nav-bar title="设置位置" left-arrow @click-left="$router.back()" />

    <!-- 说明 -->
    <section class="card tip-card">
      <van-icon name="info-o" size="14" class="tip-icon" />
      <p class="tip-text">
        设置后将作为「当前位置」，用于查看附近商家与排序；仅保存在本机，直到你下次修改。
      </p>
    </section>

    <!-- 当前位置 -->
    <section class="card">
      <div class="row">
        <span class="row-label">当前位置</span>
        <span class="row-value">{{ currentLabel }}</span>
      </div>
    </section>

    <!-- 地图选点 -->
    <section class="card">
      <div class="block-head">
        <h2 class="block-title">
          <van-icon name="location-o" size="14" />
          <span>在地图上选择位置</span>
        </h2>
        <van-button size="small" type="primary" plain color="var(--wm-primary)" icon="location-o" @click="useCurrent">
          使用当前定位
        </van-button>
      </div>
      <div ref="mapEl" class="map" v-show="amapReady"></div>
      <div class="map-tip" v-if="!amapReady">{{ mapTip }}</div>
    </section>

    <!-- 位置信息 -->
    <section class="card">
      <h2 class="block-title">
        <van-icon name="edit" size="14" />
        <span>位置信息</span>
      </h2>
      <van-field v-model="addrKeyword" label="地址搜索" placeholder="输入地址自动定位，如：广东省阳江市江城区XX路" clearable>
        <template #button>
          <van-button size="small" type="primary" color="var(--wm-primary)" :loading="searching" @click="searchAddr">搜索</van-button>
        </template>
      </van-field>
      <van-field v-model="form.name" label="位置名称" placeholder="如：广东省阳江市江城区XX路8号" />
      <van-field v-model="form.lng" label="经度" type="number" placeholder="点击地图选点，或手动填写" />
      <van-field v-model="form.lat" label="纬度" type="number" placeholder="点击地图选点，或手动填写" />
    </section>

    <!-- 保存 / 清除 -->
    <section class="card">
      <van-button class="save-btn" round block type="primary" color="var(--wm-primary)" :loading="saving" @click="save">保存并使用</van-button>
      <van-button class="clear-btn" round block plain color="var(--wm-text-3)" @click="clear">清除已保存位置</van-button>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue';
import { useRouter } from 'vue-router';
import { showToast } from 'vant';
import { loadAmap, geocodeAddress, getSavedLocation, setSavedLocation, clearSavedLocation, locationLabel } from '@waimai/shared';

const router = useRouter();
const saving = ref(false);
const searching = ref(false);
const addrKeyword = ref('');
const amapReady = ref(false);
const mapTip = ref('地图加载中...');
const mapEl = ref<HTMLElement | null>(null);
const saved = ref(getSavedLocation());
let map: any = null;
let marker: any = null;

const form = ref<any>({
  name: saved.value?.text || '',
  lng: saved.value?.lng != null ? String(saved.value.lng) : '',
  lat: saved.value?.lat != null ? String(saved.value.lat) : '',
});

const currentLabel = computed(() => locationLabel(saved.value));

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
  map = new (window as any).AMap.Map(el, { zoom: 14, center: [lng, lat] });
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
    if (!form.value.name && p.address) form.value.name = p.address;
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

function save() {
  const lng = Number(form.value.lng);
  const lat = Number(form.value.lat);
  if (!form.value.lng || !form.value.lat || Number.isNaN(lng) || Number.isNaN(lat)) {
    showToast('请先在地图上选点或填写经纬度');
    return;
  }
  saving.value = true;
  saved.value = setSavedLocation({ lng, lat, text: form.value.name || undefined });
  saving.value = false;
  showToast('位置已更新');
  router.back();
}

function clear() {
  clearSavedLocation();
  saved.value = null;
  form.value = { name: '', lng: '', lat: '' };
  if (marker) marker.setPosition([116.397128, 39.916527]);
  if (map) map.setCenter([116.397128, 39.916527]);
  showToast('已清除，将使用默认位置');
}

onMounted(initMap);

onUnmounted(() => {
  if (map) {
    map.destroy();
    map = null;
    marker = null;
  }
});
</script>

<style scoped>
.loc-page {
  min-height: 100vh;
  background: var(--wm-bg-page);
  padding-bottom: calc(var(--wm-space-6) + env(safe-area-inset-bottom));
}

/* ---------------- 通用卡片 ---------------- */
.card {
  margin: var(--wm-space-3);
  padding: var(--wm-space-4);
  background: var(--wm-bg-card);
  border-radius: var(--wm-radius-lg);
  box-shadow: var(--wm-shadow-1);
}

/* ---------------- 说明 ---------------- */
.tip-card {
  display: flex;
  align-items: flex-start;
  gap: var(--wm-space-2);
}

.tip-icon {
  flex-shrink: 0;
  margin-top: 2px;
  color: var(--wm-primary);
}

.tip-text {
  flex: 1;
  font-size: var(--wm-font-sm);
  line-height: var(--wm-leading-normal);
  color: var(--wm-text-3);
}

/* ---------------- 当前位置 ---------------- */
.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--wm-space-3);
  min-height: var(--wm-tap-min);
}

.row-label {
  flex-shrink: 0;
  font-size: var(--wm-font-md);
  color: var(--wm-text-3);
}

.row-value {
  max-width: 68%;
  text-align: right;
  word-break: break-all;
  font-size: var(--wm-font-md);
  font-weight: 600;
  color: var(--wm-text-1);
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

/* ---------------- 地图 ---------------- */
.map {
  width: 100%;
  height: 220px;
  margin-top: var(--wm-space-2);
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

/* ---------------- 表单 ---------------- */
/* 输入控件可点高度不低于 44px */
.card :deep(.van-field) {
  min-height: var(--wm-tap-min);
  padding: var(--wm-space-3) 0;
  background: transparent;
}

/* 标签用次要色，输入值用主文字色 */
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

/* ---------------- 操作按钮 ---------------- */
.save-btn {
  min-height: var(--wm-tap-min);
  font-size: var(--wm-font-lg);
  font-weight: 600;
}

.clear-btn {
  min-height: var(--wm-tap-min);
  margin-top: var(--wm-space-3);
  border-color: var(--wm-border);
  font-size: var(--wm-font-md);
}
</style>
