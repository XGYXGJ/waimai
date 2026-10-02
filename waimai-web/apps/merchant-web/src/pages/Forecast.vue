<template>
  <el-card>
    <template #header>AI 销量预测</template>
    <el-table :data="items">
      <el-table-column prop="dishName" label="菜品" />
      <el-table-column prop="forecast" label="明日预计销量（份）" width="160" />
      <el-table-column label="建议备货（份）" width="140">
        <template #default="{ row }">
          <el-tag type="warning">{{ row.suggestion }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="近28天趋势" min-width="200">
        <template #default="{ row }">
          <div :ref="(el) => setChartRef(row.dishId, el)" style="width: 100%; height: 40px"></div>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue';
import * as echarts from 'echarts';
import { apiForecast } from '@/api';

const items = ref<any[]>([]);
const chartRefs = new Map<number, any>();

function setChartRef(dishId: number, el: any) {
  if (el) chartRefs.set(dishId, el);
}

function renderCharts() {
  nextTick(() => {
    chartRefs.forEach((el, dishId) => {
      const item = items.value.find((i) => i.dishId === dishId);
      if (!item || !el) return;
      const chart = echarts.init(el);
      const dates = (item.history || []).map((h: any) => h.date);
      const qtys = (item.history || []).map((h: any) => h.qty);
      chart.setOption({
        grid: { top: 5, bottom: 5, left: 5, right: 5 },
        xAxis: { type: 'category', data: dates, show: false },
        yAxis: { type: 'value', show: false },
        series: [{ type: 'line', data: qtys, smooth: true, showSymbol: false, lineStyle: { color: '#ff6034' }, areaStyle: { opacity: 0.1 } }],
      });
    });
  });
}

onMounted(async () => {
  try {
    const data: any = await apiForecast();
    items.value = data.items || [];
    renderCharts();
  } catch {}
});
</script>
