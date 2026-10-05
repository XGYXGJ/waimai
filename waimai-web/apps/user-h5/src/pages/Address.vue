<template>
  <div class="address-page">
    <van-nav-bar title="地址管理" left-arrow @click-left="$router.back()" />

    <!-- 地址列表 -->
    <section class="card">
      <template v-if="addresses.length">
        <div class="addr-item" v-for="a in addresses" :key="a.id" @click="edit(a)">
          <div class="addr-body">
            <div class="addr-top">
              <span class="addr-detail">{{ a.detail }}</span>
              <van-tag v-if="a.isDefault === 1" type="primary" size="mini" color="var(--wm-primary)">默认</van-tag>
            </div>
            <div class="addr-meta">
              <van-icon name="user-o" size="12" class="meta-icon" />
              <span>{{ a.contact }}</span>
              <van-icon name="phone-o" size="12" class="meta-icon phone-icon" />
              <span>{{ a.phone }}</span>
            </div>
          </div>
          <button class="addr-del" type="button" aria-label="删除该地址" @click.stop="remove(a.id)">
            <van-icon name="delete-o" size="18" />
          </button>
        </div>
      </template>
      <van-empty v-else description="暂无地址，点击下方新增" />
    </section>

    <!-- 底部固定操作区：避让安全区 -->
    <div class="bottom-bar">
      <van-button round block type="primary" color="var(--wm-primary)" icon="plus" @click="openAdd">新增地址</van-button>
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
.address-page {
  min-height: 100vh;
  background: var(--wm-bg-page);
  /* 底部固定按钮区（84px）+ 安全区 */
  padding-bottom: calc(84px + env(safe-area-inset-bottom));
}

/* ---------------- 通用卡片 ---------------- */
.card {
  margin: var(--wm-space-3);
  padding: var(--wm-space-4);
  background: var(--wm-bg-card);
  border-radius: var(--wm-radius-lg);
  box-shadow: var(--wm-shadow-1);
}

/* ---------------- 地址行 ---------------- */
.addr-item {
  display: flex;
  align-items: flex-start;
  gap: var(--wm-space-3);
  min-height: var(--wm-tap-min);
  padding: var(--wm-space-3) 0;
  border-bottom: 1px solid var(--wm-border);
  cursor: pointer;
}

.addr-item:first-child {
  padding-top: 0;
}

.addr-item:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}

.addr-body {
  flex: 1;
  min-width: 0;
}

.addr-top {
  display: flex;
  align-items: center;
  gap: var(--wm-space-2);
}

/* 详细地址是主信息：加大字号与字重 */
.addr-detail {
  flex: 1;
  min-width: 0;
  font-size: var(--wm-font-md);
  font-weight: 600;
  line-height: var(--wm-leading-normal);
  color: var(--wm-text-1);
}

.addr-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--wm-space-1);
  margin-top: var(--wm-space-1);
  font-size: var(--wm-font-sm);
  color: var(--wm-text-3);
}

.meta-icon {
  color: var(--wm-text-4);
}

.phone-icon {
  margin-left: var(--wm-space-2);
}

/* 删除：独立 44px 触控目标，避免误触整行 */
.addr-del {
  flex-shrink: 0;
  width: var(--wm-tap-min);
  height: var(--wm-tap-min);
  display: grid;
  place-items: center;
  border: 0;
  border-radius: var(--wm-radius-md);
  background: transparent;
  color: var(--wm-text-4);
  cursor: pointer;
}

.addr-del:active {
  background: var(--wm-bg-page);
  color: var(--wm-danger);
}

/* ---------------- 底部固定操作区 ---------------- */
.bottom-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 10;
  padding: var(--wm-space-3) var(--wm-space-4);
  padding-bottom: calc(var(--wm-space-3) + env(safe-area-inset-bottom));
  background: var(--wm-bg-card);
  border-top: 1px solid var(--wm-border);
}

.bottom-bar :deep(.van-button) {
  min-height: var(--wm-tap-min);
  font-size: var(--wm-font-lg);
  font-weight: 600;
}

/* ---------------- 编辑弹层 ---------------- */
.popup-title {
  padding: var(--wm-space-4);
  text-align: center;
  font-size: var(--wm-font-lg);
  font-weight: 600;
  color: var(--wm-text-1);
  border-bottom: 1px solid var(--wm-border);
}

.popup-body {
  /* 与标题（16px 上下内边距 + 行高 + 分隔线）对齐 */
  height: calc(100% - 57px);
  overflow-y: auto;
}
</style>
