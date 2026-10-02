<template>
  <el-card>
    <template #header>店铺设置</template>
    <el-form label-width="100px" style="max-width: 640px">
      <el-form-item label="店铺Logo">
        <el-upload
          :show-file-list="false"
          :http-request="uploadLogo"
          accept="image/*"
        >
          <div v-if="form.logo" class="logo-preview">
            <img :src="resolveUrl(form.logo)" alt="店铺Logo" />
            <span class="logo-tip">点击更换</span>
          </div>
          <el-button v-else type="primary" plain>上传图片</el-button>
        </el-upload>
      </el-form-item>
      <el-form-item label="经营分类">
        <el-select v-model="form.categoryId" placeholder="选择经营分类" style="width: 100%">
          <el-option v-for="c in categoryOptions" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="店铺名称"><el-input v-model="form.shopName" /></el-form-item>
      <el-form-item label="公告"><el-input v-model="form.notice" type="textarea" /></el-form-item>
      <el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item>

      <el-form-item label="店铺位置" required>
        <div class="locator">
          <div id="shop-map" class="shop-map"></div>
          <div class="locator-bar">
            <el-button size="small" type="primary" plain @click="locateToCurrent">📍 定位到当前位置</el-button>
            <span class="locator-hint">{{ locating ? '定位中...' : '点击地图或拖动标记选择店铺位置' }}</span>
          </div>
          <div class="coords-row">
            <el-input v-model="form.lng" placeholder="经度" readonly style="width: 140px" />
            <el-input v-model="form.lat" placeholder="纬度" readonly style="width: 140px" />
          </div>
        </div>
      </el-form-item>

      <el-form-item label="地址"><el-input v-model="form.address" /></el-form-item>
      <el-form-item label="营业时间"><el-input v-model="form.businessHours" placeholder="如 09:00-22:00" /></el-form-item>
      <el-form-item label="起送价"><el-input-number v-model="form.minOrderAmount" :min="0" :precision="2" /></el-form-item>
      <el-form-item label="配送费"><el-input-number v-model="form.deliveryFee" :min="0" :precision="2" /></el-form-item>
      <el-form-item label="营业状态">
        <el-switch v-model="form.openStatus" :active-value="1" :inactive-value="0" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="save">保存</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue';
import { ElMessage } from 'element-plus';
import { loadAmap } from '@waimai/shared';
import { apiShopInfo, apiUpdateShop, apiUpload, apiCategoryOptions } from '@/api';

const form = ref<any>({ openStatus: 1 });
const categoryOptions = ref<any[]>([]);
const locating = ref(false);

let map: any = null;
let marker: any = null;

function resolveUrl(url?: string) {
  if (!url) return '';
  if (url.startsWith('http')) return url;
  return url;
}

function ensureAmap(): Promise<boolean> {
  return loadAmap();
}

function initMap() {
  const el = document.getElementById('shop-map');
  if (!el || !(window as any).AMap) return;
  const lng = Number(form.value.lng) || 116.397128;
  const lat = Number(form.value.lat) || 39.916527;
  map = new (window as any).AMap.Map('shop-map', { zoom: 15, center: [lng, lat] });
  marker = new (window as any).AMap.Marker({
    position: [lng, lat],
    draggable: true,
  });
  map.add(marker);
  // 点击地图选点
  map.on('click', (e: any) => {
    const { lng: clng, lat: clat } = e.lnglat;
    setCoords(clng, clat);
  });
  // 拖动标记选点
  marker.on('dragend', (e: any) => {
    const { lng: dlng, lat: dlat } = e.lnglat;
    setCoords(dlng, dlat);
  });
}

function setCoords(lng: number, lat: number) {
  form.value.lng = lng.toFixed(6);
  form.value.lat = lat.toFixed(6);
}

function locateToCurrent() {
  if (!map || !navigator.geolocation) {
    ElMessage.warning('地图未就绪或浏览器不支持定位');
    return;
  }
  locating.value = true;
  navigator.geolocation.getCurrentPosition(
    (pos) => {
      const lng = pos.coords.longitude;
      const lat = pos.coords.latitude;
      setCoords(lng, lat);
      map.setCenter([lng, lat]);
      marker.setPosition([lng, lat]);
      locating.value = false;
      ElMessage.success('已定位到当前位置');
    },
    () => {
      locating.value = false;
      ElMessage.warning('定位失败，请手动在地图上选择');
    },
    { enableHighAccuracy: true, timeout: 6000 }
  );
}

async function uploadLogo(opt: any) {
  try {
    const res: any = await apiUpload(opt.file);
    form.value.logo = res.url;
    ElMessage.success('Logo 上传成功');
  } catch (e: any) {
    ElMessage.error(e.message || '上传失败');
  }
}

async function save() {
  if (!form.value.lng || !form.value.lat) {
    ElMessage.warning('请选择店铺位置');
    return;
  }
  try {
    await apiUpdateShop({
      shopName: form.value.shopName,
      notice: form.value.notice,
      phone: form.value.phone,
      address: form.value.address,
      logo: form.value.logo,
      categoryId: form.value.categoryId,
      lng: Number(form.value.lng),
      lat: Number(form.value.lat),
      businessHours: form.value.businessHours,
      minOrderAmount: form.value.minOrderAmount,
      deliveryFee: form.value.deliveryFee,
      openStatus: form.value.openStatus,
    });
    ElMessage.success('保存成功');
  } catch (e: any) {
    ElMessage.error(e.message);
  }
}

onMounted(async () => {
  try {
    categoryOptions.value = (await apiCategoryOptions()) as any[];
  } catch {}
  try {
    const data: any = await apiShopInfo();
    form.value = { ...data, openStatus: data.openStatus ?? 1 };
  } catch {}

  // 加载地图
  const ok = await ensureAmap();
  if (ok) {
    initMap();
    // 若没有坐标（新商户），尝试自动定位当前位置
    if (!form.value.lng || !form.value.lat || Number(form.value.lng) === 0) {
      locateToCurrent();
    }
  } else {
    ElMessage.warning('未配置高德地图 Key/安全密钥，请到管理端「系统设置」填写 map.js_key 和 map.security_code 后刷新');
  }
});

onUnmounted(() => {
  if (map) {
    map.destroy();
    map = null;
  }
});
</script>

<style scoped>
.logo-preview {
  position: relative;
  width: 96px;
  height: 96px;
  border: 1px dashed #dcdfe6;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}
.logo-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.logo-tip {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 12px;
  text-align: center;
  line-height: 22px;
}
.locator {
  width: 100%;
}
.shop-map {
  width: 100%;
  height: 260px;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
  margin-bottom: 8px;
}
.locator-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}
.locator-hint {
  font-size: 12px;
  color: #999;
}
.coords-row {
  display: flex;
  gap: 8px;
}
</style>
