<template>
  <div>
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>系统参数配置</span>
          <el-tag type="info">修改后立即生效</el-tag>
        </div>
      </template>
      <el-alert type="info" :closable="false" style="margin-bottom: 16px"
        title="此处配置 AI 大模型 API Key、高德地图 Key、推荐算法权重等核心参数，保存后即时生效，无需重启。" />

      <el-table :data="configs">
        <el-table-column prop="configKey" label="配置项" width="220" />
        <el-table-column prop="description" label="说明" width="260" />
        <el-table-column label="值">
          <template #default="{ row }">
            <el-input v-model="row.configValue" :type="row.configKey.includes('api_key') ? 'password' : 'text'" placeholder="请输入值" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="save(row)">保存</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { apiConfigs, apiUpdateConfig } from '@/api';

const configs = ref<any[]>([]);

async function save(row: any) {
  try {
    await apiUpdateConfig(row.configKey, row.configValue || '');
    ElMessage.success('保存成功');
  } catch (e: any) {
    ElMessage.error(e.message);
  }
}

onMounted(async () => {
  try {
    configs.value = (await apiConfigs()) as any[];
  } catch {}
});
</script>
