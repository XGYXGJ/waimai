<template>
  <el-card>
    <el-tabs v-model="activeTab" @tab-change="load">
      <el-tab-pane label="待审核" name="0" />
      <el-tab-pane label="已通过" name="1" />
      <el-tab-pane label="已驳回" name="2" />
    </el-tabs>
    <el-table :data="records">
      <el-table-column prop="shopName" label="店铺名" />
      <el-table-column prop="phone" label="电话" />
      <el-table-column prop="address" label="地址" show-overflow-tooltip />
      <el-table-column prop="rating" label="评分" width="80" />
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <template v-if="row.auditStatus === 0">
            <el-button size="small" type="primary" @click="audit(row, true)">通过</el-button>
            <el-button size="small" type="danger" @click="audit(row, false)">驳回</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { apiMerchants, apiAuditMerchant } from '@/api';

const activeTab = ref('0');
const records = ref<any[]>([]);

async function audit(row: any, pass: boolean) {
  try {
    await apiAuditMerchant(row.id, pass, pass ? '' : '资质不符');
    ElMessage.success('操作成功');
    load();
  } catch (e: any) {
    ElMessage.error(e.message);
  }
}

async function load() {
  try {
    const data: any = await apiMerchants(Number(activeTab.value));
    records.value = data.records || [];
  } catch {}
}

onMounted(load);
</script>
