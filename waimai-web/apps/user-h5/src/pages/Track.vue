<template>
  <div class="track-page">
    <van-nav-bar title="骑手追踪" left-arrow @click-left="$router.back()" />
    <div id="map" class="map"></div>
    <div class="track-info">
      <div class="rider-name">骑手 {{ riderName || '配送中' }}</div>
      <div class="rider-status">{{ statusText }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue';
import { useRoute } from 'vue-router';
import { WsClient, loadAmap } from '@waimai/shared';

const route = useRoute();
const orderId = Number(route.params.orderId);
const riderName = ref('');
const statusText = ref('骑手正在赶来');
const ws = new WsClient();
let map: any = null;
let riderMarker: any = null;
let trackLine: any = null;

function initMap() {
  if (!(window as any).AMap) return;
  map = new (window as any).AMap.Map('map', { zoom: 15 });
  (window as any).AMap.plugin('AMap.Scale', () => {
    map.addControl(new (window as any).AMap.Scale());
  });
  riderMarker = new (window as any).AMap.Marker({ position: [116.39, 39.91] });
  map.add(riderMarker);
  trackLine = new (window as any).AMap.Polyline({ path: [], strokeColor: '#ff6034', strokeWeight: 4 });
  map.add(trackLine);
}

onMounted(async () => {
  // 订阅骑手位置
  ws.connect();
  ws.subscribeOrder(orderId);
  ws.on('RIDER_LOCATION', (msg) => {
    const { lng, lat } = msg;
    if (riderMarker) riderMarker.setPosition([lng, lat]);
    if (trackLine) trackLine.setPath([...trackLine.getPath(), [lng, lat]]);
  });
  ws.on('ORDER_STATUS', (msg) => {
    if (msg.to === 'DELIVERED') statusText.value = '已送达';
  });

  // 加载地图
  const ok = await loadAmap(['AMap.Scale']);
  initMap();
  if (!ok) statusText.value = '地图未配置（管理端填写 map.js_key 后可见地图）';
});

onUnmounted(() => {
  ws.unsubscribeOrder(orderId);
  ws.close();
});
</script>

<style scoped>
.map {
  width: 100%;
  height: 60vh;
}
.track-info {
  padding: 16px;
  background: #fff;
}
.rider-name {
  font-size: 16px;
  font-weight: 600;
}
.rider-status {
  color: #999;
  font-size: 13px;
  margin-top: 6px;
}
</style>
