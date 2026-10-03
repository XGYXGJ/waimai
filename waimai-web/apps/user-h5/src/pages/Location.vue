<template>
  <div class="loc-page">
    <van-nav-bar title="设置位置" left-arrow @click-left="$router.back()" />

    <div class="tip">
      设置后将作为「当前位置」，用于查看附近商家与排序；仅保存在本机，直到你下次修改。
    </div>

    <van-cell-group inset>
      <van-cell title="当前位置" :value="currentLabel" />
    </van-cell-group>

    <van-cell-group inset class="map-group">
      <div class="group-title">
        <span>在地图上选择位置</span>
        <van-button size="mini" type="primary" plain color="#ff6034" icon="location-o" @click="useCurrent">
          使用当前定位
        </van-button>
      </div>
      <div ref="mapEl" class="map" v-show="amapReady"></div>
      <div class="map-tip" v-if="!amapReady">{{ mapTip }}</div>
    </van-cell-group>

    <van-cell-group inset>
      <van-field v-model="addrKeyword" label="地址搜索" placeholder="输入地址自动定位，如：广东省阳江市江城区XX路" clearable>
        <template #button>
          <van-button size="small" type="primary" color="#ff6034" :loading="searching" @click="searchAddr">搜索</van-button>
        </template>
      </van-field>
      <van-field v-model="form.name" label="位置名称" placeholder="如：广东省阳江市江城区XX路8号" />
      <van-field v-model="form.lng" label="经度" type="number" placeholder="点击地图选点，或手动填写" />
      <van-field v-model="form.lat" label="纬度" type="number" placeholder="点击地图选点，或手动填写" />
    </van-cell-group>

    <div class="btns">
      <van-button round block type="primary" color="#ff6034" :loading="saving" @click="save">保存并使用</van-button>
      <van-button round block plain color="#969799" class="clear-btn" @click="clear">清除已保存位置</van-button>
    </div>
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
.tip {
  padding: 12px 16px;
  font-size: 12px;
  color: #999;
  line-height: 1.6;
}
.map-group {
  margin-top: 12px;
}
.group-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px 6px;
  font-size: 13px;
  color: #666;
}
.map {
  width: calc(100% - 24px);
  height: 220px;
  margin: 0 12px 8px;
  border-radius: 8px;
  overflow: hidden;
  background: #f2f2f2;
}
.map-tip {
  padding: 8px 16px;
  font-size: 12px;
  color: #999;
}
.btns {
  margin: 20px 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.clear-btn {
  border: none;
}
</style>
