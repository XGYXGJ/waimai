<template>
  <div class="addr-editor">
    <van-form @submit="onSubmit">
      <van-cell-group inset>
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
      </van-cell-group>

      <van-cell-group inset class="loc-group">
        <div class="group-title">
          <span>收货位置（用于骑手导航）</span>
          <van-button size="mini" type="primary" plain color="#ff6034" icon="location-o" @click="useCurrent">
            使用当前定位
          </van-button>
        </div>
        <div ref="mapEl" class="map" v-show="amapReady"></div>
        <div class="map-tip" v-if="!amapReady">{{ mapTip }}</div>
        <van-field v-model="addrKeyword" label="地址搜索" placeholder="输入地址自动定位，如：XX市XX区XX路" clearable>
          <template #button>
            <van-button size="small" type="primary" color="#ff6034" :loading="searching" @click="searchAddr">搜索</van-button>
          </template>
        </van-field>
        <van-field v-model="form.lng" label="经度" type="number" placeholder="点击地图选点，或手动填写" />
        <van-field v-model="form.lat" label="纬度" type="number" placeholder="点击地图选点，或手动填写" />
      </van-cell-group>

      <van-cell-group inset>
        <van-cell title="设为默认地址" center>
          <template #right-icon>
            <van-switch v-model="isDefault" size="20" />
          </template>
        </van-cell>
      </van-cell-group>

      <div class="submit">
        <van-button round block type="primary" color="#ff6034" native-type="submit" :loading="saving">
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
.loc-group {
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
  height: 200px;
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
.submit {
  margin: 16px;
}
</style>
