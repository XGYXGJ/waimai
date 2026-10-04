<template>
  <div>
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>AI 模型管理</span>
          <div style="display: flex; gap: 8px; align-items: center">
            <el-tag :type="sourceTag">配置来源：{{ sourceText }}</el-tag>
            <el-button @click="load">刷新状态</el-button>
            <el-button type="primary" @click="openAdd">新增模型</el-button>
          </div>
        </div>
      </template>

      <el-alert type="info" :closable="false" style="margin-bottom: 12px">
        <template #title>
          列表顺序即调用优先级：系统从第 1 个开始尝试，某个模型未启用、未填 API Key 或调用失败时，
          自动降级到下一个模型，全部失败才返回降级结果。某个模型失败后会进入短暂冷却，
          冷却期内不再排在前面，避免每次都先等它超时一遍。
        </template>
      </el-alert>

      <el-descriptions :column="3" border size="small" style="margin-bottom: 16px">
        <el-descriptions-item label="配置来源">
          <el-tag :type="sourceTag" size="small">{{ sourceText }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="当前生效模型">
          <span v-if="runtime.current">
            {{ runtime.current.name }}（{{ runtime.current.modelId }}）
          </span>
          <span v-else style="color: #909399">尚无成功调用</span>
        </el-descriptions-item>
        <el-descriptions-item label="冷却中">
          <span v-if="coolingCount">{{ coolingCount }} 个模型失败后暂不优先尝试</span>
          <span v-else style="color: #909399">无</span>
        </el-descriptions-item>
      </el-descriptions>

      <el-alert
        v-if="runtime.mysqlError"
        type="warning" :closable="false" style="margin-bottom: 16px"
        :title="'AI 服务读不到 ai_model 表，已回退到兜底配置：' + runtime.mysqlError" />
      <el-alert
        v-else-if="runtime.reachable === false"
        type="error" :closable="false" style="margin-bottom: 16px"
        :title="runtime.message || 'AI 服务不可达，下方状态可能不是实时的'" />
      <el-alert
        v-else-if="runtime.ok === false && runtime.message"
        type="warning" :closable="false" style="margin-bottom: 16px"
        :title="runtime.message" />

      <el-table :data="models" v-loading="loading" row-key="id">
        <el-table-column label="优先级" width="90" align="center">
          <template #default="{ $index }">
            <el-tag :type="$index === 0 ? 'success' : 'info'">{{ $index + 1 }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="name" label="名称" width="170" />

        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            <el-tag :type="providerTag(row.provider)" size="small">
              {{ providerLabel(row.provider) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="modelId" label="模型标识" width="200" />

        <el-table-column label="密钥" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.provider !== 'openai'" type="success" size="small">免密钥</el-tag>
            <el-tag v-else :type="row.hasKey ? 'success' : 'warning'" size="small">
              {{ row.hasKey ? '已配置' : '未填写' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="启用" width="80" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled === 1"
              @change="(v: boolean) => toggle(row, v)" />
          </template>
        </el-table-column>

        <el-table-column label="排序" width="160">
          <template #default="{ row, $index }">
            <el-button size="small" :disabled="$index === 0" @click="move(row, 'up')">上移</el-button>
            <el-button size="small" :disabled="$index === 0" @click="move(row, 'top')">置顶</el-button>
            <el-button
              size="small"
              :disabled="$index === models.length - 1"
              @click="move(row, 'down')">下移</el-button>
          </template>
        </el-table-column>

        <el-table-column label="最近调用" width="150">
          <template #default="{ row }">
            <template v-if="statusOf(row)">
              <el-tag v-if="statusOf(row).ok" type="success" size="small">正常</el-tag>
              <el-tooltip v-else :content="statusOf(row).error || '调用失败'" placement="top">
                <el-tag type="danger" size="small">
                  失败{{ statusOf(row).cooldownSec ? ` · 冷却 ${statusOf(row).cooldownSec}s` : '' }}
                </el-tag>
              </el-tooltip>
            </template>
            <span v-else style="color: #c0c4cc; font-size: 12px">未调用</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="success" :loading="row.__testing" @click="testRow(row)">
              测试
            </el-button>
            <el-popconfirm title="确定删除该模型？" @confirm="remove(row)">
              <template #reference>
                <el-button size="small" type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>

        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
      </el-table>
    </el-card>

    <!-- 可用模型列表 -->
    <el-dialog v-model="catalogVisible" title="选择可用模型" width="520px" append-to-body>
      <div style="color: #909399; font-size: 12px; margin-bottom: 8px">
        来源：{{ catalogTarget }}
      </div>
      <el-input v-model="catalogKeyword" placeholder="搜索模型名" clearable style="margin-bottom: 8px" />
      <div style="max-height: 380px; overflow-y: auto">
        <div
          v-for="m in filteredCatalog" :key="m.id"
          class="catalog-item" @click="pickModel(m)"
        >
          <span>{{ m.id }}</span>
          <el-tag v-if="m.free" size="small" type="success">免费</el-tag>
        </div>
        <el-empty v-if="!filteredCatalog.length" description="没有拉到模型，检查地址 / Key 是否正确" />
      </div>
      <template #footer>
        <el-button @click="catalogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="dialog" :title="form.id ? '编辑模型' : '新增模型'" width="560px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="如：DeepSeek / OpenCode Zen 免费模型" />
        </el-form-item>

        <el-form-item label="服务商类型" required>
          <el-select v-model="form.provider" style="width: 100%" @change="onProviderChange">
            <el-option label="OpenCode Zen 免费模型（免密钥）" value="zen" />
            <el-option label="OpenAI 兼容（需 API Key）" value="openai" />
            <el-option label="本地 Ollama（免密钥）" value="ollama" />
          </el-select>
        </el-form-item>

        <el-form-item label="模型标识" required>
          <div style="display: flex; gap: 8px; width: 100%">
            <el-select
              v-if="form.provider === 'zen'"
              v-model="form.modelId"
              filterable allow-create default-first-option
              placeholder="选择或输入免费模型名" style="flex: 1">
              <el-option v-for="m in zenModels" :key="m" :label="m" :value="m" />
            </el-select>
            <el-input v-else v-model="form.modelId" :placeholder="modelIdPlaceholder" style="flex: 1" />
            <el-button :loading="catalogLoading" @click="openCatalog">拉取可用模型</el-button>
          </div>
          <div style="color: #909399; font-size: 12px; margin-top: 4px">
            <template v-if="form.provider === 'zen'">
              免费模型池按地区轮换（你当前网络下开放的可能与示例不同），点「拉取可用模型」拿实时列表。
            </template>
            <template v-else>
              模型名必须与该端点自己的模型一致：填 zen 的名字到 DeepSeek 会报
              “supported API model names are ...”。不确定就点「拉取可用模型」。
            </template>
          </div>
        </el-form-item>

        <el-form-item v-if="form.provider !== 'zen'" label="API 地址">
          <el-input v-model="form.baseUrl" :placeholder="baseUrlPlaceholder" />
        </el-form-item>

        <el-form-item v-if="form.provider === 'openai'" label="API Key">
          <el-input v-model="form.apiKey" type="password" show-password placeholder="留空则调用时自动跳过" />
        </el-form-item>

        <el-form-item label="超时(秒)">
          <el-input-number v-model="form.timeout" :min="5" :max="300" />
        </el-form-item>

        <el-form-item label="启用">
          <el-switch :model-value="form.enabled === 1" @change="(v: boolean) => (form.enabled = v ? 1 : 0)" />
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button :loading="testing" @click="testForm">测试连接</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import {
  apiAiModels, apiSaveAiModel, apiDeleteAiModel, apiToggleAiModel,
  apiMoveAiModel, apiTestAiModel, apiTestAiModelById, apiAiModelCatalog,
  apiAiModelStatus,
} from '@/api';

/* ---- 可用模型列表 ---- */
const catalogVisible = ref(false);
const catalogLoading = ref(false);
const catalogKeyword = ref('');
const catalogList = ref<any[]>([]);
const catalogTarget = ref('');

const filteredCatalog = computed(() => {
  const kw = catalogKeyword.value.trim().toLowerCase();
  return kw ? catalogList.value.filter((m: any) => m.id.toLowerCase().includes(kw)) : catalogList.value;
});

async function openCatalog() {
  catalogLoading.value = true;
  catalogKeyword.value = '';
  try {
    const data: any = await apiAiModelCatalog({
      provider: form.value.provider,
      baseUrl: form.value.baseUrl,
      apiKey: form.value.apiKey,
    });
    catalogList.value = data?.models || [];
    catalogTarget.value = form.value.provider === 'zen'
      ? 'opencode zen 免费池（实时）'
      : (form.value.baseUrl || '未填写地址');
    catalogVisible.value = true;
    if (!catalogList.value.length) {
      ElMessage.warning(data?.message || '未拉到模型列表');
    }
  } catch (e: any) {
    ElMessage.error(e.message || '拉取失败');
  } finally {
    catalogLoading.value = false;
  }
}

function pickModel(m: any) {
  form.value.modelId = m.id;
  catalogVisible.value = false;
  ElMessage.success(`已选择 ${m.id}`);
}

const models = ref<any[]>([]);
const loading = ref(false);
const dialog = ref(false);
const saving = ref(false);
const testing = ref(false);

/* ---- 运行状态（来自 AI 服务的模型池注册表） ---- */
const runtime = ref<any>({ source: '', current: null, models: [], reachable: null });

const SOURCE_TEXT: Record<string, string> = {
  mysql: '数据库', json: '本地快照', default: '内置默认',
  init: '初始化中', unreachable: 'AI 服务不可达',
};
const SOURCE_TAG: Record<string, string> = {
  mysql: 'success', json: 'warning', default: 'danger',
  init: 'info', unreachable: 'danger',
};
// 拿不到来源时显示「—」而不是「未知」：未知会让人以为出错了，实际只是还没读到
const sourceText = computed(() => SOURCE_TEXT[runtime.value.source] || '—');
const sourceTag = computed(() => SOURCE_TAG[runtime.value.source] || 'info');
const coolingCount = computed(() =>
  (runtime.value.models || []).filter((m: any) => m.cooldownSec > 0).length);

/** 状态按 provider + modelId 匹配，与列表里那一条记录对齐 */
function statusOf(row: any) {
  return (runtime.value.models || []).find(
    (m: any) => m.provider === row.provider && m.modelId === row.modelId);
}

// 仅作拉取失败时的兜底候选；免费池按地区轮换，实际以「拉取可用模型」为准
const ZEN_MODELS = [
  'mimo-v2.6-flash-free', 'mimo-v2.5-free', 'big-pickle',
  'ling-3.1-flash-free', 'longcat-2.5-preview-free',
  'nemotron-3.5-lightning-free', 'space-bunny-free', 'jev-1.13-free',
];
const zenModels = ref(ZEN_MODELS);

const emptyForm = () => ({
  id: null as number | null, name: '', provider: 'zen', baseUrl: '',
  apiKey: '', modelId: 'mimo-v2.6-flash-free', enabled: 1, priority: null, timeout: 30, remark: '',
});
const form = ref<any>(emptyForm());

const baseUrlPlaceholder = computed(() =>
  form.value.provider === 'ollama'
    ? 'http://host.docker.internal:11434'
    : 'https://api.deepseek.com');

const modelIdPlaceholder = computed(() =>
  form.value.provider === 'ollama'
    ? '如 qwen2.5:7b-instruct'
    : '如 deepseek-v4-pro / qwen-plus（务必与端点自身模型一致）');

function providerLabel(p: string) {
  return { zen: 'Zen 免费', openai: 'OpenAI 兼容', ollama: '本地 Ollama' }[p] || p;
}
function providerTag(p: string): any {
  return { zen: 'success', openai: '', ollama: 'warning' }[p] || 'info';
}

function onProviderChange(p: string) {
  if (p === 'zen') {
    form.value.baseUrl = '';
    form.value.apiKey = '';
    if (!form.value.modelId) form.value.modelId = 'mimo-v2.6-flash-free';
  } else if (p === 'ollama') {
    form.value.apiKey = '';
    if (!form.value.baseUrl) form.value.baseUrl = 'http://host.docker.internal:11434';
  }
}

async function load() {
  loading.value = true;
  try {
    models.value = (await apiAiModels()) as any[];
    try {
      const st: any = await apiAiModelStatus();
      // 只有「确实连不上 AI 服务」才标成不可达。
      // 曾经只要 ok === false 就标不可达，但后端在服务正常时也可能返回 ok:false
      // （例如状态接口本身报错但服务仍在跑），会出现「上面说不可达、下面测试却通过」的矛盾。
      runtime.value = (st?.reachable === false || st?.source === 'unreachable')
        ? { source: 'unreachable', models: [], reachable: false, message: st?.message }
        : { ...st, reachable: true };
    } catch {
      // 状态拿不到不影响管理配置本身
      runtime.value = { source: 'unreachable', models: [], reachable: false };
    }
  } catch (e: any) {
    ElMessage.error(e.message || '加载失败');
  } finally {
    loading.value = false;
  }
}

function openAdd() {
  form.value = emptyForm();
  dialog.value = true;
}

function openEdit(row: any) {
  form.value = {
    id: row.id, name: row.name, provider: row.provider,
    baseUrl: row.baseUrl || '', apiKey: '', modelId: row.modelId,
    enabled: row.enabled, priority: row.priority,
    timeout: row.timeout || 30, remark: row.remark || '',
  };
  dialog.value = true;
}

async function submit() {
  if (!form.value.name?.trim()) return ElMessage.warning('请填写模型名称');
  if (!form.value.modelId?.trim()) return ElMessage.warning('请填写模型标识');
  if (form.value.provider === 'openai' && !form.value.baseUrl?.trim()) {
    return ElMessage.warning('OpenAI 兼容模型需填写 API 地址');
  }
  saving.value = true;
  try {
    await apiSaveAiModel({ ...form.value, apiKey: form.value.apiKey || undefined });
    ElMessage.success('保存成功');
    dialog.value = false;
    await load();
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败');
  } finally {
    saving.value = false;
  }
}

async function toggle(row: any, val: boolean) {
  try {
    await apiToggleAiModel(row.id, val ? 1 : 0);
    row.enabled = val ? 1 : 0;
    ElMessage.success(val ? '已启用' : '已禁用');
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败');
  }
}

async function move(row: any, direction: string) {
  try {
    await apiMoveAiModel(row.id, direction);
    await load();
  } catch (e: any) {
    ElMessage.error(e.message || '排序失败');
  }
}

async function remove(row: any) {
  try {
    await apiDeleteAiModel(row.id);
    ElMessage.success('已删除');
    await load();
  } catch (e: any) {
    ElMessage.error(e.message || '删除失败');
  }
}

async function testRow(row: any) {
  row.__testing = true;
  try {
    const r: any = await apiTestAiModelById(row.id);
    const ok = r?.ok ?? r?.data?.ok;
    const msg = r?.message ?? r?.data?.message ?? '';
    const ms = r?.latencyMs ?? r?.data?.latencyMs;
    if (ok) ElMessage.success(`${msg}（${ms} ms）`);
    else ElMessage.error(msg || '连通失败');
  } catch (e: any) {
    ElMessage.error(e.message || '测试失败');
  } finally {
    row.__testing = false;
  }
}

async function testForm() {
  if (!form.value.modelId?.trim()) return ElMessage.warning('请先填写模型标识');
  testing.value = true;
  try {
    const r: any = await apiTestAiModel({ ...form.value });
    const ok = r?.ok ?? r?.data?.ok;
    const msg = r?.message ?? r?.data?.message ?? '';
    if (ok) ElMessage.success(msg || '连通正常');
    else ElMessage.error(msg || '连通失败');
  } catch (e: any) {
    ElMessage.error(e.message || '测试失败');
  } finally {
    testing.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.catalog-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
}
.catalog-item:hover {
  background: #f5f7fa;
}
</style>
