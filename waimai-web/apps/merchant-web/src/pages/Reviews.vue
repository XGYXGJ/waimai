<template>
  <el-card>
    <template #header>评价管理</template>
    <el-table :data="reviews">
      <el-table-column prop="rating" label="评分" width="80">
        <template #default="{ row }">⭐ {{ row.rating }}</template>
      </el-table-column>
      <el-table-column prop="content" label="内容" show-overflow-tooltip />
      <el-table-column label="情感" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.sentiment" :type="row.sentiment === 'NEG' ? 'danger' : row.sentiment === 'POS' ? 'success' : 'info'">
            {{ sentimentText(row.sentiment) }}
          </el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="reply" label="回复" show-overflow-tooltip />
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button v-if="!row.reply" size="small" @click="reply(row)">回复</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { apiShopInfo, apiMerchantReviews, apiReplyReview } from '@/api';

const reviews = ref<any[]>([]);

function sentimentText(s: string) {
  return { POS: '好评', NEU: '中性', NEG: '差评' }[s] || s;
}

async function reply(row: any) {
  const { value } = await ElMessageBox.prompt('请输入回复内容', '回复评价');
  if (value) {
    try {
      await apiReplyReview(row.id, value);
      ElMessage.success('回复成功');
      load();
    } catch (e: any) {
      ElMessage.error(e.message);
    }
  }
}

async function load() {
  try {
    const shop: any = await apiShopInfo();
    const data: any = await apiMerchantReviews(shop.id);
    reviews.value = data || [];
  } catch {}
}

onMounted(load);
</script>
