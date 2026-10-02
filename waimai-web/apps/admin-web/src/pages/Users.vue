<template>
  <el-card>
    <el-table :data="records">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="nickname" label="昵称" />
      <el-table-column prop="phone" label="手机号" />
      <el-table-column prop="role" label="角色" width="100" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '正常' : '封禁' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button size="small" :type="row.status === 1 ? 'danger' : 'success'" @click="toggle(row)">
            {{ row.status === 1 ? '封禁' : '解封' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { apiUsers, apiUserStatus } from '@/api';

const records = ref<any[]>([]);

async function toggle(row: any) {
  await apiUserStatus(row.id, row.status === 1 ? 0 : 1);
  ElMessage.success('操作成功');
  load();
}

async function load() {
  try {
    const data: any = await apiUsers();
    records.value = data.records || [];
  } catch {}
}

onMounted(load);
</script>
