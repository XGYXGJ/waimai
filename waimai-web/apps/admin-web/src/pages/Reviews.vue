<template>
  <el-card>
    <template #header>评价治理（负面评价待处理）</template>
    <el-table :data="records">
      <el-table-column prop="rating" label="评分" width="80">
        <template #default="{ row }">⭐ {{ row.rating }}</template>
      </el-table-column>
      <el-table-column prop="content" label="内容" show-overflow-tooltip />
      <el-table-column label="情感" width="100">
        <template #default="{ row }">
          <el-tag :type="row.sentiment === 'NEG' ? 'danger' : row.sentiment === 'POS' ? 'success' : 'info'">
            {{ sentimentText(row.sentiment) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { apiReviews, apiDeleteReview } from '@/api';

const records = ref<any[]>([]);

function sentimentText(s: string) {
  return { POS: '好评', NEU: '中性', NEG: '差评' }[s] || s;
}

async function remove(row: any) {
  await ElMessageBox.confirm('确认删除该评价？', '提示', { type: 'warning' });
  await apiDeleteReview(row.id);
  ElMessage.success('已删除');
  load();
}

async function load() {
  try {
    const data: any = await apiReviews();
    records.value = data.records || [];
  } catch {}
}

onMounted(load);
</script>
