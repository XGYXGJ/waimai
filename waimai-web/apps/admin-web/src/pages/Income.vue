<template>
  <div class="income-page">
    <!-- 周期切换 -->
    <el-card>
      <template #header>
        <div class="hd">
          <span>收入结算</span>
          <el-radio-group v-model="period" size="small" @change="render">
            <el-radio-button value="today">今日</el-radio-button>
            <el-radio-button value="week">近 7 天</el-radio-button>
            <el-radio-button value="month">本月</el-radio-button>
            <el-radio-button value="total">累计</el-radio-button>
          </el-radio-group>
        </div>
      </template>

      <el-row :gutter="16">
        <el-col :span="6" v-for="c in cards" :key="c.label">
          <div class="stat">
            <div class="stat-label">{{ c.label }}</div>
            <div class="stat-value" :style="{ color: c.color }">{{ c.value }}</div>
            <div class="stat-sub">{{ c.sub }}</div>
          </div>
        </el-col>
      </el-row>

      <el-alert v-if="periodData" type="info" :closable="false" style="margin-top: 16px"
        :title="`共 ${periodData.orderCount} 笔已结算订单；平台收入 = 商家抽成 + 骑手抽成，三方之和等于用户实付（GMV）。`" />
    </el-card>

    <!-- 计价与抽成规则 -->
    <el-card style="margin-top: 16px">
      <template #header>
        <div class="hd">
          <span>当前计价与抽成规则</span>
          <el-button size="small" type="primary" plain @click="$router.push('/config')">去修改</el-button>
        </div>
      </template>
      <el-descriptions :column="4" border>
        <el-descriptions-item label="配送起步费">¥{{ num(rule.baseFee) }}</el-descriptions-item>
        <el-descriptions-item label="每公里费用">¥{{ num(rule.perKmFee) }} / km</el-descriptions-item>
        <el-descriptions-item label="起步含免费距离">{{ num(rule.freeDistanceKm) }} km</el-descriptions-item>
        <el-descriptions-item label="单笔上限">
          {{ Number(rule.maxFee) > 0 ? '¥' + num(rule.maxFee) : '不限制' }}
        </el-descriptions-item>
        <el-descriptions-item label="平台默认配送范围">{{ num(rule.radiusKm) }} km</el-descriptions-item>
        <el-descriptions-item label="商家抽成比例">{{ pct(rate.merchantRate) }}</el-descriptions-item>
        <el-descriptions-item label="骑手抽成比例">{{ pct(rate.riderRate) }}</el-descriptions-item>
        <el-descriptions-item label="配送费公式">
          起步费 + max(0, 距离 - 免费距离) × 每公里费用
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 商家收入明细 -->
    <el-card style="margin-top: 16px">
      <template #header>商家收入明细（按 GMV 倒序）</template>
      <el-table :data="rows" v-loading="loading">
        <el-table-column prop="shopName" label="商家" min-width="160" />
        <el-table-column prop="orderCount" label="订单数" width="90" />
        <el-table-column label="GMV（实付）" width="130">
          <template #default="{ row }">¥{{ num(row.gmv) }}</template>
        </el-table-column>
        <el-table-column label="商品额" width="120">
          <template #default="{ row }">¥{{ num(row.dishAmount) }}</template>
        </el-table-column>
        <el-table-column label="配送费" width="110">
          <template #default="{ row }">¥{{ num(row.deliveryFee) }}</template>
        </el-table-column>
        <el-table-column label="商家实收" width="120">
          <template #default="{ row }">
            <span class="money-red">¥{{ num(row.merchantIncome) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="平台抽成" width="120">
          <template #default="{ row }">
            <span class="money-red">¥{{ num(row.platformIncome) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="骑手收入" width="120">
          <template #default="{ row }">¥{{ num(row.riderIncome) }}</template>
        </el-table-column>
        <template #empty><el-empty description="暂无已结算订单" /></template>
      </el-table>
      <el-pagination
        style="margin-top: 12px; justify-content: flex-end"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="onPage"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { apiIncomeOverview, apiIncomeMerchants } from '@/api';

const period = ref<'today' | 'week' | 'month' | 'total'>('today');
const overview = ref<any>({});
const rows = ref<any[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(10);
const loading = ref(false);

const periodData = computed(() => overview.value?.[period.value] || null);
const rule = computed(() => overview.value?.rule || {});
const rate = computed(() => overview.value?.rate || {});

const cards = computed(() => {
  const d: any = periodData.value || {};
  return [
    { label: 'GMV（用户实付）', value: `¥${num(d.gmv)}`, color: '#409eff', sub: '含配送费与打包费' },
    { label: '平台收入', value: `¥${num(d.platformIncome)}`, color: '#f56c6c', sub: '商家抽成 + 骑手抽成' },
    { label: '商家收入', value: `¥${num(d.merchantIncome)}`, color: '#67c23a', sub: `未扣 ${pct(rate.value?.merchantRate)} 抽成后净收入` },
    { label: '骑手收入', value: `¥${num(d.riderIncome)}`, color: '#e6a23c', sub: '配送费净收入' },
  ];
});

function num(v: any) {
  return Number(v || 0).toFixed(2);
}
function pct(v: any) {
  if (v === undefined || v === null) return '—';
  return `${(Number(v) * 100).toFixed(1)}%`;
}

async function render() {
  try {
    overview.value = (await apiIncomeOverview()) || {};
  } catch {}
}

async function loadRows() {
  loading.value = true;
  try {
    const data: any = await apiIncomeMerchants(page.value, size.value);
    rows.value = data.records || [];
    total.value = Number(data.total || 0);
  } catch {
    rows.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

function onPage(p: number) {
  page.value = p;
  loadRows();
}

onMounted(() => {
  render();
  loadRows();
});
</script>

<style scoped>
.hd {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.stat {
  text-align: center;
  padding: 12px 8px;
  background: #fafafa;
  border-radius: 8px;
}
.stat-label {
  color: #909399;
  font-size: 13px;
}
.stat-value {
  font-size: 26px;
  font-weight: 600;
  margin: 6px 0 4px;
}
.stat-sub {
  color: #c0c4cc;
  font-size: 12px;
}
.money-red {
  color: #f56c6c;
  font-weight: 600;
}
</style>
