<template>
  <div>
    <el-card>
      <div style="display: flex; justify-content: space-between; margin-bottom: 16px">
        <span style="font-weight: 600">优惠券列表</span>
        <el-button type="primary" @click="openCreate">新建优惠券</el-button>
      </div>
      <el-table :data="coupons">
        <el-table-column prop="name" label="名称" />
        <el-table-column label="优惠" width="120">
          <template #default="{ row }">
            {{ row.discountAmount ? `减¥${row.discountAmount}` : `${(row.discountRate || 0) * 10}折` }}
          </template>
        </el-table-column>
        <el-table-column prop="thresholdAmount" label="门槛" width="100">
          <template #default="{ row }">满{{ row.thresholdAmount || 0 }}可用</template>
        </el-table-column>
        <el-table-column prop="receivedCount" label="已领取" width="80" />
        <el-table-column prop="endTime" label="到期" width="180" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button v-if="row.status === 1" size="small" type="danger" @click="off(row.id)">下架</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="showCreate" title="新建优惠券" width="500px">
      <el-form label-width="100px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="form.type">
            <el-radio :value="1">满减</el-radio>
            <el-radio :value="2">折扣</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.type === 1" label="满额"><el-input-number v-model="form.thresholdAmount" :min="0" :precision="2" /></el-form-item>
        <el-form-item v-if="form.type === 1" label="减额"><el-input-number v-model="form.discountAmount" :min="0" :precision="2" /></el-form-item>
        <el-form-item v-if="form.type === 2" label="折扣率"><el-input-number v-model="form.discountRate" :min="0" :max="1" :precision="2" :step="0.1" /></el-form-item>
        <el-form-item label="总量"><el-input-number v-model="form.totalCount" :min="1" /></el-form-item>
        <el-form-item label="开始时间"><el-input v-model="form.startTime" placeholder="2026-01-01 00:00:00" /></el-form-item>
        <el-form-item label="结束时间"><el-input v-model="form.endTime" placeholder="2026-12-31 23:59:59" /></el-form-item>
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
import { apiMerchantCoupons, apiCreateCoupon, apiOffCoupon } from '@/api';

const coupons = ref<any[]>([]);
const showCreate = ref(false);
const form = ref<any>({ type: 1, totalCount: 100, startTime: '2026-01-01 00:00:00', endTime: '2026-12-31 23:59:59' });

function openCreate() {
  form.value = { type: 1, totalCount: 100, startTime: '2026-01-01 00:00:00', endTime: '2026-12-31 23:59:59' };
  showCreate.value = true;
}

async function create() {
  try {
    await apiCreateCoupon(form.value);
    ElMessage.success('创建成功');
    showCreate.value = false;
    load();
  } catch (e: any) {
    ElMessage.error(e.message);
  }
}

async function off(id: number) {
  await apiOffCoupon(id);
  ElMessage.success('已下架');
  load();
}

async function load() {
  try {
    const data: any = await apiMerchantCoupons();
    coupons.value = data.records || [];
  } catch {}
}

onMounted(load);
</script>
