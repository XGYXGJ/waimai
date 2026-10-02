<template>
  <div class="map-page">
    <van-nav-bar title="配送地图" left-arrow @click-left="$router.back()" />
    <div id="map" class="map"></div>
    <div class="map-controls">
      <van-cell-group inset>
        <van-cell title="模拟骑行" center>
          <template #right-icon>
            <van-switch v-model="simulate" size="20" @change="onSimulateChange" />
          </template>
        </van-cell>
      </van-cell-group>
      <div class="status-text">{{ statusText }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue';
import { useRoute } from 'vue-router';
import { WsClient, loadAmap } from '@waimai/shared';
import { apiReportLocation } from '@/api';

const route = useRoute();
const orderId = Number(route.params.orderId);
const simulate = ref(true);
const statusText = ref('模拟骑行中...');
const ws = new WsClient();
let map: any = null;
let marker: any = null;
let routePoints: any[] = [];
let currentIndex = 0;
let timer: number | null = null;
let currentLng = 116.39;
let currentLat = 39.91;

function initMap() {
  if (!(window as any).AMap) return;
  map = new (window as any).AMap.Map('map', { zoom: 15 });
  marker = new (window as any).AMap.Marker({ position: [currentLng, currentLat] });
  map.add(marker);
}

function startSimulate() {
  stopSimulate();
  // 模拟路线：围绕起点生成几个点
  const baseLng = currentLng;
  const baseLat = currentLat;
  routePoints = [];
  for (let i = 0; i <= 10; i++) {
    routePoints.push([baseLng + i * 0.002, baseLat + i * 0.0015]);
  }
  currentIndex = 0;
  timer = window.setInterval(() => {
    if (currentIndex >= routePoints.length) {
      stopSimulate();
      statusText.value = '已到达目的地';
      return;
    }
    const [lng, lat] = routePoints[currentIndex++];
    currentLng = lng;
    currentLat = lat;
    reportLocation(lng, lat);
    if (marker) marker.setPosition([lng, lat]);
  }, 2000);
}

function stopSimulate() {
  if (timer) {
    clearInterval(timer);
    timer = null;
  }
}

function reportLocation(lng: number, lat: number) {
  // HTTP 上报（与 WS 共用后端上报链路）
  apiReportLocation(lng, lat, orderId).catch(() => {});
}

function startRealGps() {
  stopSimulate();
  if (!navigator.geolocation) {
    statusText.value = '浏览器不支持定位，请使用模拟骑行';
    simulate.value = true;
    startSimulate();
    return;
  }
  navigator.geolocation.watchPosition(
    (pos) => {
      const lng = pos.coords.longitude;
      const lat = pos.coords.latitude;
      currentLng = lng;
      currentLat = lat;
      reportLocation(lng, lat);
      if (marker) marker.setPosition([lng, lat]);
      statusText.value = '真实定位上报中...';
    },
    () => {
      statusText.value = '定位失败，请使用模拟骑行';
      simulate.value = true;
      startSimulate();
    },
    { enableHighAccuracy: true, maximumAge: 3000, timeout: 5000 }
  );
}

function onSimulateChange(v: boolean) {
  if (v) {
    statusText.value = '模拟骑行中...';
    startSimulate();
  } else {
    statusText.value = '真实定位中...';
    startRealGps();
  }
}

onMounted(async () => {
  ws.connect();
  const ok = await loadAmap();
  initMap();
  if (ok) {
    startSimulate();
  } else {
    statusText.value = '未配置地图 Key（管理端「系统设置」填写 map.js_key），仍在模拟上报位置';
    startSimulate();
  }
});

onUnmounted(() => {
  stopSimulate();
  ws.close();
});
</script>

<style scoped>
.map {
  width: 100%;
  height: 55vh;
}
.map-controls {
  padding: 12px;
}
.status-text {
  text-align: center;
  color: #999;
  font-size: 13px;
  margin-top: 8px;
}
</style>
