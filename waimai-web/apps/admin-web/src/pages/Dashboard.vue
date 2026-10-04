<template>
  <div class="dashboard">
    <el-row :gutter="16">
      <el-col :span="6" v-for="c in stats" :key="c.label">
        <el-card class="stat-card">
          <div class="stat-label">{{ c.label }}</div>
          <div class="stat-value">{{ c.value }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="12">
        <el-card>
          <template #header>订单量趋势（近7天）</template>
          <div ref="trendRef" style="height: 300px"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>GMV 概览</template>
          <div class="gmv-list">
            <div class="gmv-item" v-for="(v, k) in gmv" :key="k">
              <span>{{ gmvLabel(k) }}</span>
              <span class="gmv-value">¥{{ v }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 收入构成：平台 / 商家 / 骑手（今日 · 本月 · 累计） -->
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="24">
        <el-card>
          <template #header>
            <div class="hd">
              <span>收入构成（今日 · 本月 · 累计）</span>
              <el-button size="small" type="primary" plain @click="$router.push('/income')">收入结算</el-button>
            </div>
          </template>
          <div class="gmv-list">
            <div class="gmv-item" v-for="r in incomeRows" :key="r.label">
              <span>{{ r.label }}</span>
              <span class="gmv-value">
                今日 ¥{{ money(r.today) }} · 本月 ¥{{ money(r.month) }} · 累计 ¥{{ money(r.total) }}
              </span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue';
import * as echarts from 'echarts';
import { apiDashboard } from '@/api';

const stats = ref([
  { label: '用户总数', value: '0' },
  { label: '商户总数', value: '0' },
  { label: '订单总数', value: '0' },
  { label: '骑手在线', value: '0' },
]);
const gmv = ref<any>({});
const trendRef = ref<any>(null);
/** 收入构成三行：平台 / 商家 / 骑手 */
const incomeRows = ref<any[]>([]);

function money(v: any) {
  return Number(v || 0).toFixed(2);
}

function buildIncomeRows(income: any) {
  const t = income?.today || {};
  const mo = income?.month || {};
  const all = income?.total || {};
  return [
    { label: '平台抽成收入', today: t.platformIncome, month: mo.platformIncome, total: all.platformIncome },
    { label: '商家实收', today: t.merchantIncome, month: mo.merchantIncome, total: all.merchantIncome },
    { label: '骑手配送收入', today: t.riderIncome, month: mo.riderIncome, total: all.riderIncome },
  ];
}

function gmvLabel(k: string) {
  return { today: '今日 GMV', week: '近7天 GMV', month: '本月 GMV' }[k] || k;
}

onMounted(async () => {
  try {
    const data: any = await apiDashboard();
    stats.value[0].value = String(data.userCount || 0);
    stats.value[1].value = String(data.merchantCount || 0);
    stats.value[2].value = String(data.orderCount || 0);
    stats.value[3].value = String(data.riderOnline || 0);
    gmv.value = data.gmv || {};
    incomeRows.value = buildIncomeRows(data.income);

    await nextTick();
    if (trendRef.value) {
      const chart = echarts.init(trendRef.value);
      const dates = (data.orderTrend || []).map((t: any) => t.date.slice(5));
      const counts = (data.orderTrend || []).map((t: any) => t.count);
      chart.setOption({
        tooltip: { trigger: 'axis' },
        xAxis: { type: 'category', data: dates },
        yAxis: { type: 'value' },
        series: [{ type: 'line', data: counts, smooth: true, areaStyle: { opacity: 0.2 }, itemStyle: { color: '#ff6034' } }],
      });
    }
  } catch {}
});
</script>

<style scoped>
.stat-card {
  text-align: center;
}
.hd {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.stat-label {
  color: #999;
  font-size: 13px;
}
.stat-value {
  font-size: 28px;
  font-weight: 600;
  margin-top: 8px;
  color: #ff6034;
}
.gmv-list {
  padding: 20px;
}
.gmv-item {
  display: flex;
  justify-content: space-between;
  padding: 16px 0;
  border-bottom: 1px solid #f0f0f0;
  font-size: 15px;
}
.gmv-value {
  font-weight: 600;
  color: #ff6034;
}
</style>
