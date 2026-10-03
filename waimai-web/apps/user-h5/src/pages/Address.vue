<template>
  <div class="address-page">
    <van-nav-bar title="地址管理" left-arrow @click-left="$router.back()" />

    <van-cell-group inset style="margin-top: 12px">
      <van-cell
        v-for="a in addresses"
        :key="a.id"
        :title="a.detail"
        :label="`${a.contact} ${a.phone}`"
        @click="edit(a)"
      >
        <template #right-icon>
          <van-tag v-if="a.isDefault === 1" type="primary" size="mini" color="#ff6034">默认</van-tag>
          <van-icon name="cross" style="margin-left: 12px" @click.stop="remove(a.id)" />
        </template>
      </van-cell>
      <van-empty v-if="!addresses.length" description="暂无地址，点击下方新增" />
    </van-cell-group>

    <div style="margin: 24px 16px">
      <van-button round block type="primary" color="#ff6034" @click="openAdd">新增地址</van-button>
    </div>

    <van-popup v-model:show="showForm" position="bottom" round :style="{ height: '90%' }">
      <div class="popup-title">{{ editing?.id ? '编辑地址' : '新增地址' }}</div>
      <div class="popup-body">
        <AddressEditor v-if="showForm" :address="editing" @saved="onSaved" />
      </div>
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { showToast } from 'vant';
import AddressEditor from '@/components/AddressEditor.vue';
import { apiAddresses, apiAddressDelete } from '@/api';

const addresses = ref<any[]>([]);
const showForm = ref(false);
const editing = ref<any>(null);

function openAdd() {
  editing.value = null;
  showForm.value = true;
}

function edit(a: any) {
  editing.value = a;
  showForm.value = true;
}

function onSaved() {
  showForm.value = false;
  load();
}

async function remove(id: number) {
  try {
    await apiAddressDelete(id);
    showToast('已删除');
    load();
  } catch (e: any) {
    showToast(e.message);
  }
}

async function load() {
  try {
    const data: any = await apiAddresses();
    addresses.value = data?.records || data || [];
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
.popup-body {
  height: calc(100% - 52px);
  overflow-y: auto;
  padding-bottom: 24px;
}
</style>
