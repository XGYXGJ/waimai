<template>
  <div class="map-page">
    <van-nav-bar title="配送地图" left-arrow @click-left="$router.back()" />
    <div id="map" class="map"></div>

    <!-- 图例：让人一眼分清哪个是自己、哪个是顾客 -->
    <div class="legend">
      <span class="lg"><i class="dot self"></i>我的位置</span>
      <span class="lg"><i class="dot user"></i>顾客 {{ order?.address?.contact || '' }}</span>
      <span v-if="remainKm != null" class="lg dist">直线 {{ remainKm }}km</span>
    </div>

    <div class="map-controls">
      <van-cell-group inset>
        <van-cell title="模拟骑行" center>
          <template #right-icon>
            <van-switch v-model="simulate" size="20" @change="onSimulateChange" />
          </template>
        </van-cell>
      </van-cell-group>

      <div class="status-text">{{ statusText }}</div>

      <div class="dest" v-if="destText">
        <van-icon name="location-o" size="14" />
        <span>{{ destText }}</span>
      </div>

      <!-- 地图页不是 tabbar 路由，这里必须给出出口，否则只能靠浏览器返回键 -->
      <div class="map-ops">
        <van-button size="small" plain icon="chat-o" @click="go(`/chat/${orderId}`)">沟通</van-button>
        <van-button size="small" plain icon="logistics" @click="go('/delivering')">进行中订单</van-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { WsClient, loadAmap, formatDistance, distanceKm } from '@waimai/shared';
import { apiReportLocation, apiOrderDetail } from '@/api';

const route = useRoute();
const router = useRouter();
const orderId = Number(route.params.orderId);

function go(path: string) {
  router.push(path);
}

const simulate = ref(true);
const statusText = ref('正在初始化…');
const order = ref<any>(null);
const remainKm = ref<number | null>(null);
const ws = new WsClient();

let map: any = null;
let selfMarker: any = null;
let userMarker: any = null;
let timer: number | null = null;
let geoWatch: number | null = null;

/** 骑手当前位置 */
let selfLng: number | null = null;
let selfLat: number | null = null;
/** 顾客（收货地址）坐标，来自订单地址快照 */
let destLng: number | null = null;
let destLat: number | null = null;

const destText = ref('');

function hasDest() {
  return destLng != null && destLat != null;
}

function initMap() {
  if (!(window as any).AMap) return;
  const A = (window as any).AMap;
  map = new A.Map('map', { zoom: 13 });

  // 顾客位置：固定 marker
  if (hasDest()) {
    userMarker = new A.Marker({
      position: [destLng, destLat],
      title: '顾客',
    });
    userMarker.setLabel?.({ content: '顾客', offset: new A.Pixel(-14, -34) });
    map.add(userMarker);
  }

  // 自己：骑手 marker
  if (selfLng != null && selfLat != null) {
    selfMarker = new A.Marker({ position: [selfLng, selfLat] });
    map.add(selfMarker);
  }

  fitBoth();
}

/**
 * 让两个点都在视野内 —— 这是「看双方位置」的关键。
 *
 * 注意：AMap 2.0 的 setFitView 只接受**覆盖物**（Marker/Polyline…），
 * 传 marker.getPosition() 返回的 LngLat 会抛 "getBounds is not a function"。
 */
function fitBoth() {
  if (!map) return;
  const overlays: any[] = [];
  if (selfMarker) overlays.push(selfMarker);
  if (userMarker) overlays.push(userMarker);
  if (!overlays.length) return;
  try {
    if (overlays.length === 1) {
      map.setZoomAndCenter(15, overlays[0].getPosition());
    } else {
      // immediately=false 用动画过渡；avoid 给四边留白；maxZoom 防止拉太近
      map.setFitView(overlays, false, [50, 50, 50, 50], 17);
    }
  } catch (e) {
    // 视野调整失败不该影响定位上报，退回单点定位即可
    console.warn('[map] setFitView 失败', e);
    if (overlays[0]?.getPosition) map.setZoomAndCenter(15, overlays[0].getPosition());
  }
}

function updateRemain() {
  if (!hasDest() || selfLng == null || selfLat == null) {
    remainKm.value = null;
    return;
  }
  remainKm.value = Number(
    distanceKm(selfLng, selfLat, destLng as number, destLat as number).toFixed(2)
  );
}

/** 上报位置并刷新地图 */
function applyPosition(lng: number, lat: number, report = true) {
  selfLng = lng;
  selfLat = lat;
  if (report) apiReportLocation(lng, lat, orderId).catch(() => {});
  if (selfMarker) selfMarker.setPosition([lng, lat]);
  // 剩余距离必须在「地图是否存在」的判断之前更新：
  // 没配地图 Key 时 map 一直是 null，骑手照样在移动、上报也照样在发，
  // 图例上的直线距离不能冻在打开页面那一刻的数字上。
  updateRemain();
  if (!map) return;
  // 只上报、不重算视野时不要打断用户已经缩放好的视图
  if (!userMarker) map.setZoomAndCenter(15, [lng, lat]);
}

/* ---------------- 模拟骑行：从当前位置骑向顾客 ---------------- */

let routePoints: [number, number][] = [];
let currentIndex = 0;

/** 沿直线插值生成路线，起点=自己，终点=顾客（不再凭空造点） */
function buildRoute() {
  routePoints = [];
  if (!hasDest() || selfLng == null || selfLat == null) return;
  const steps = 12;
  for (let i = 1; i <= steps; i++) {
    const t = i / steps;
    routePoints.push([
      Number((selfLng + ((destLng as number) - selfLng) * t).toFixed(6)),
      Number((selfLat + ((destLat as number) - selfLat) * t).toFixed(6)),
    ]);
  }
  currentIndex = 0;
}

function startSimulate() {
  stopSimulate();
  // 切回模拟必须把真实 GPS 的 watch 停掉。原来只在 startRealGps 里 clearWatch，
  // 于是「真实定位 → 切回模拟」后 watch 仍在跑：下一个真实坐标会调用 applyPosition
  // 覆盖掉模拟坐标（并再次上报给后端），蓝点还会倒退。
  if (geoWatch != null) {
    navigator.geolocation.clearWatch(geoWatch);
    geoWatch = null;
  }
  buildRoute();
  if (!routePoints.length) {
    // 没有顾客坐标就原地不动，只维持上报
    statusText.value = '该订单没有收货坐标，已按当前位置循环上报';
    timer = window.setInterval(() => {
      if (selfLng != null && selfLat != null) applyPosition(selfLng, selfLat);
    }, 5000);
    return;
  }
  statusText.value = '模拟骑行中…';
  timer = window.setInterval(() => {
    if (currentIndex >= routePoints.length) {
      stopSimulate();
      statusText.value = hasDest()
        ? `已到达顾客位置（直线 ${formatDistance(remainKm.value ?? 0)}）`
        : '已到达';
      return;
    }
    const [lng, lat] = routePoints[currentIndex++];
    applyPosition(lng, lat);
    // 快到终点时把视野收过去，看得见自己正在靠近顾客
    if (currentIndex >= routePoints.length - 2) fitBoth();
  }, 2000);
}

function stopSimulate() {
  if (timer) {
    clearInterval(timer);
    timer = null;
  }
}

/* ---------------- 真实 GPS ---------------- */

function startRealGps() {
  stopSimulate();
  if (geoWatch != null) {
    navigator.geolocation.clearWatch(geoWatch);
    geoWatch = null;
  }
  if (!navigator.geolocation) {
    statusText.value = '浏览器不支持定位，请使用模拟骑行';
    simulate.value = true;
    startSimulate();
    return;
  }
  statusText.value = '真实定位上报中…';
  geoWatch = navigator.geolocation.watchPosition(
    (pos) => {
      applyPosition(pos.coords.longitude, pos.coords.latitude);
      statusText.value = '真实定位上报中…';
    },
    () => {
      statusText.value = '定位失败，已切换为模拟骑行';
      simulate.value = true;
      startSimulate();
    },
    { enableHighAccuracy: true, maximumAge: 3000, timeout: 5000 }
  );
}

function onSimulateChange(v: boolean) {
  if (v) {
    startSimulate();
  } else {
    startRealGps();
  }
}

/** 订单详情拿顾客地址与联系方式 */
async function loadOrder() {
  try {
    const data: any = await apiOrderDetail(orderId);
    order.value = data;
    const addr = data?.address;
    if (addr) {
      const lng = Number(addr.lng);
      const lat = Number(addr.lat);
      if (Number.isFinite(lng) && Number.isFinite(lat) && lng !== 0 && lat !== 0) {
        destLng = lng;
        destLat = lat;
      }
      destText.value = addr.detail || '';
    }
    return true;
  } catch {
    destText.value = '';
    return false;
  }
}

/** 骑手自己的初始位置：优先真实 GPS，拿不到再退到商家/顾客附近 */
function initSelfPosition() {
  return new Promise<void>((resolve) => {
    if (!navigator.geolocation) return resolve();
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        applyPosition(pos.coords.longitude, pos.coords.latitude);
        resolve();
      },
      () => resolve(),
      { enableHighAccuracy: true, timeout: 5000, maximumAge: 60000 }
    );
  });
}

onMounted(async () => {
  ws.connect();
  await loadOrder();

  // 先把两个点都算出来，再初始化地图，保证 fitView 能同时框住两者
  await initSelfPosition();
  if (selfLng == null && hasDest()) {
    // 拿不到 GPS 时以顾客位置为起点，保证地图上一定有两个点
    selfLng = destLng;
    selfLat = destLat;
  }

  // 地图渲染失败（Key 失效 / API 变更 / 网络拦截）不能拖垮位置上报这条业务链路，
  // 所以整段包起来，失败只降级为「无地图但继续上报」。
  try {
    const mapOk = await loadAmap();
    if (!mapOk) {
      statusText.value = '未配置地图 Key（管理端「系统设置」填写 map.js_key），仍在模拟上报位置';
    }
    initMap();
    updateRemain();
  } catch (e: any) {
    console.warn('[map] 初始化失败，降级为无地图上报', e);
    statusText.value = '地图初始化失败，仍在按当前位置上报';
  }

  // 无论地图成不成都要跑起来：位置上报是骑手端的核心业务
  startSimulate();
});

onUnmounted(() => {
  stopSimulate();
  if (geoWatch != null) navigator.geolocation.clearWatch(geoWatch);
  ws.close();
});
</script>

<style scoped>
.map {
  width: 100%;
  height: 48vh;
}

.legend {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 7px 14px;
  font-size: 12px;
  color: #555;
  background: #fff;
  border-bottom: 1px solid #eee;
}
.lg {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
}
.dot.self {
  background: #1989fa;
}
.dot.user {
  background: #ee0a24;
}
.dist {
  margin-left: auto;
  color: #07c160;
  font-weight: 600;
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
.dest {
  display: flex;
  align-items: flex-start;
  gap: 5px;
  margin-top: 10px;
  padding: 9px 11px;
  background: #fff;
  border-radius: 8px;
  font-size: 13px;
  color: #333;
  line-height: 1.5;
}
.map-ops {
  display: flex;
  gap: 8px;
  justify-content: center;
  margin-top: 12px;
}
</style>
