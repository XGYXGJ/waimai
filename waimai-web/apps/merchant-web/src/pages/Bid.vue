<template>
  <div>
    <el-card>
      <div style="display: flex; justify-content: space-between; margin-bottom: 16px">
        <span style="font-weight: 600">竞价投放</span>
        <el-button type="primary" @click="openCreate">新建投放</el-button>
      </div>
      <el-table :data="campaigns">
        <el-table-column prop="keyword" label="关键词" />
        <el-table-column prop="bid" label="出价（元）" width="120" />
        <el-table-column prop="dailyBudget" label="日预算" width="100" />
        <el-table-column prop="todaySpent" label="今日花费" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '投放中' : '已暂停' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button size="small" :type="row.status === 1 ? 'danger' : 'success'" @click="toggle(row)">
              {{ row.status === 1 ? '暂停' : '开启' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="showCreate" title="新建投放" width="500px">
      <el-form label-width="100px">
        <el-form-item label="关键词"><el-input v-model="form.keyword" placeholder="如：黄焖鸡、奶茶" /></el-form-item>
        <el-form-item label="出价"><el-input-number v-model="form.bid" :min="0.1" :precision="2" :step="0.5" /></el-form-item>
        <el-form-item label="日预算"><el-input-number v-model="form.dailyBudget" :min="1" :precision="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="create">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { apiCampaigns, apiCreateCampaign, apiCampaignStatus } from '@/api';

const campaigns = ref<any[]>([]);
const showCreate = ref(false);
const form = ref<any>({ bid: 2, dailyBudget: 100 });

function openCreate() {
  form.value = { keyword: '', bid: 2, dailyBudget: 100 };
  showCreate.value = true;
}

async function create() {
  try {
    await apiCreateCampaign(form.value);
    ElMessage.success('创建成功');
    showCreate.value = false;
    load();
  } catch (e: any) {
    ElMessage.error(e.message);
  }
}

async function toggle(row: any) {
  await apiCampaignStatus(row.id, row.status === 1 ? 0 : 1);
  load();
}

async function load() {
  try {
    campaigns.value = (await apiCampaigns()) as any[];
  } catch {}
}

onMounted(load);
</script>
