<template>
  <div class="track-page">
    <van-nav-bar title="骑手追踪" left-arrow @click-left="$router.back()" />

    <!-- 地图容器：必须有明确高度，改成自适应高度会导致地图塌陷 -->
    <div class="map-card">
      <div id="map" class="map"></div>
    </div>

    <section class="track-card">
      <div class="rider-row">
        <span class="rider-avatar">
          <van-icon name="logistics" size="20" />
        </span>
        <div class="rider-body">
          <h2 class="rider-name">骑手 {{ riderName || '配送中' }}</h2>
          <div class="rider-status">
            <span class="rider-dot" aria-hidden="true"></span>
            <span>{{ statusText }}</span>
          </div>
        </div>
      </div>
      <div class="track-tip">
        <van-icon name="location-o" size="13" />
        <span>地图上的标记会随骑手位置实时移动</span>
      </div>
    </section>
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
  // 高德地图是 canvas 渲染，参数直接进绘图管线，不接受 CSS 变量，必须用真实色值
  // （此处与 style.css 的 --wm-primary 保持一致，改主色时需同步）
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
.track-page {
  min-height: 100vh;
  background: var(--wm-bg-page);
  padding-bottom: var(--wm-space-6);
}

/* 地图外层只做圆角裁切，不改变 #map 的高度语义 */
.map-card {
  overflow: hidden;
  margin: var(--wm-space-3) var(--wm-space-3) 0;
  border-radius: var(--wm-radius-lg);
  background: var(--wm-bg-card);
  box-shadow: var(--wm-shadow-1);
}

/* 明确高度：60vh，禁止改成 fit-content / auto */
.map {
  width: 100%;
  height: 60vh;
}

.track-card {
  margin: var(--wm-space-3);
  padding: var(--wm-space-4);
  background: var(--wm-bg-card);
  border-radius: var(--wm-radius-lg);
  box-shadow: var(--wm-shadow-1);
}

.rider-row {
  display: flex;
  align-items: center;
  gap: var(--wm-space-3);
}

.rider-avatar {
  flex: 0 0 44px;
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  border-radius: var(--wm-radius-full);
  background: var(--wm-primary-50);
  color: var(--wm-primary);
}

.rider-body {
  flex: 1;
  min-width: 0;
}

.rider-name {
  font-size: var(--wm-font-lg);
  font-weight: 600;
  line-height: var(--wm-leading-tight);
  color: var(--wm-text-1);
}

.rider-status {
  display: flex;
  align-items: center;
  gap: var(--wm-space-2);
  margin-top: var(--wm-space-1);
  font-size: var(--wm-font-sm);
  line-height: var(--wm-leading-normal);
  color: var(--wm-text-3);
}

.rider-dot {
  flex-shrink: 0;
  width: 8px;
  height: 8px;
  border-radius: var(--wm-radius-full);
  background: var(--wm-success);
}

.track-tip {
  display: flex;
  align-items: center;
  gap: var(--wm-space-1);
  margin-top: var(--wm-space-3);
  padding-top: var(--wm-space-3);
  border-top: 1px solid var(--wm-border);
  font-size: var(--wm-font-sm);
  color: var(--wm-text-4);
}
</style>
