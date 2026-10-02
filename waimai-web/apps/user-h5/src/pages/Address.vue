<template>
  <div class="address-page">
    <van-nav-bar title="地址管理" left-arrow @click-left="$router.back()" />
    <van-cell-group inset style="margin-top: 12px">
      <van-cell v-for="a in addresses" :key="a.id" :title="a.detail" :label="`${a.contact} ${a.phone}`" @click="edit(a)">
        <template #right-icon>
          <van-tag v-if="a.isDefault === 1" type="primary" size="mini" color="#ff6034">默认</van-tag>
          <van-icon name="cross" style="margin-left: 12px" @click.stop="remove(a.id)" />
        </template>
      </van-cell>
    </van-cell-group>

    <div style="margin: 24px 16px">
      <van-button round block type="primary" color="#ff6034" @click="openAdd">新增地址</van-button>
    </div>

    <van-popup v-model:show="showForm" position="bottom" style="max-height: 80%">
      <div class="popup-title">{{ editing?.id ? '编辑地址' : '新增地址' }}</div>
      <van-form style="padding: 16px" @submit="save">
        <van-field v-model="form.contact" label="联系人" placeholder="姓名" />
        <van-field v-model="form.phone" label="电话" placeholder="手机号" type="tel" />
        <van-field v-model="form.detail" label="详细地址" placeholder="小区/街道/门牌号" />
        <van-field v-model="form.lng" label="经度" placeholder="经度（可选）" type="number" />
        <van-field v-model="form.lat" label="纬度" placeholder="纬度（可选）" type="number" />
        <div style="margin: 16px 0">
          <van-button round block type="primary" color="#ff6034" native-type="submit">保存</van-button>
        </div>
      </van-form>
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { showToast } from 'vant';
import { apiAddresses, apiAddressSave, apiAddressDelete } from '@/api';

const addresses = ref<any[]>([]);
const showForm = ref(false);
const editing = ref<any>(null);
const form = ref<any>({});

function openAdd() {
  editing.value = null;
  form.value = { contact: '', phone: '', detail: '', lng: '', lat: '' };
  showForm.value = true;
}

function edit(a: any) {
  editing.value = a;
  form.value = { ...a };
  showForm.value = true;
}

async function save() {
  try {
    await apiAddressSave({
      id: editing.value?.id,
      contact: form.value.contact,
      phone: form.value.phone,
      detail: form.value.detail,
      lng: form.value.lng ? Number(form.value.lng) : undefined,
      lat: form.value.lat ? Number(form.value.lat) : undefined,
    });
    showToast('保存成功');
    showForm.value = false;
    load();
  } catch (e: any) {
    showToast(e.message);
  }
}

async function remove(id: number) {
  try {
    await apiAddressDelete(id);
    load();
  } catch (e: any) {
    showToast(e.message);
  }
}

async function load() {
  try {
    const data: any = await apiAddresses();
    addresses.value = data || [];
  } catch {}
}

onMounted(load);
</script>

<style scoped>
.popup-title {
  text-align: center;
  padding: 16px;
  font-weight: 600;
}
</style>
