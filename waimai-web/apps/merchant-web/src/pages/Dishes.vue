<template>
  <div>
    <el-card>
      <div style="display: flex; justify-content: space-between; margin-bottom: 16px">
        <div>
          <el-select v-model="categoryId" placeholder="全部分类" clearable style="width: 200px" @change="load">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </div>
        <div>
          <el-button type="primary" @click="openCategory">新增分类</el-button>
          <el-button type="primary" @click="openDish">新增菜品</el-button>
        </div>
      </div>

      <el-table :data="dishes">
        <el-table-column prop="name" label="菜品名" />
        <el-table-column label="价格" width="140">
          <template #default="{ row }">
            <span>{{ row.price }}</span>
            <span v-if="row.originalPrice > row.price" style="color: #999; text-decoration: line-through; margin-left: 6px; font-size: 12px">{{ row.originalPrice }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="stock" label="库存" width="80" />
        <el-table-column prop="monthlySales" label="月售" width="80" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '上架' : '下架' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button size="small" @click="openDish(row)">编辑</el-button>
            <el-button size="small" :type="row.status === 1 ? 'danger' : 'success'" @click="toggleStatus(row)">
              {{ row.status === 1 ? '下架' : '上架' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="showDish" :title="editing?.id ? '编辑菜品' : '新增菜品'" width="500px">
      <el-form label-width="80px">
        <el-form-item label="分类">
          <el-select v-model="form.categoryId" style="width: 100%">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" /></el-form-item>
        <el-form-item label="现价"><el-input-number v-model="form.price" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="原价">
          <el-input-number v-model="form.originalPrice" :min="0" :precision="2" placeholder="高于现价则显示折扣" />
          <div class="form-tip">原价高于现价时，用户端会显示划线价与折扣</div>
        </el-form-item>
        <el-form-item label="库存"><el-input-number v-model="form.stock" :min="0" /></el-form-item>
        <el-form-item label="标签"><el-input v-model="form.tags" placeholder="用逗号分隔，如：辣,量大" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDish = false">取消</el-button>
        <el-button type="primary" @click="saveDish">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showCategory" title="新增分类" width="400px">
      <el-form label-width="80px">
        <el-form-item label="名称"><el-input v-model="categoryForm.name" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="categoryForm.sort" :min="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCategory = false">取消</el-button>
        <el-button type="primary" @click="saveCategory">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { apiCategories, apiSaveCategory, apiDishes, apiSaveDish, apiDishStatus } from '@/api';

const categories = ref<any[]>([]);
const dishes = ref<any[]>([]);
const categoryId = ref<number | undefined>();
const showDish = ref(false);
const showCategory = ref(false);
const editing = ref<any>(null);
const form = ref<any>({});
const categoryForm = ref<any>({ name: '', sort: 0 });

function openDish(d?: any) {
  editing.value = d || null;
  form.value = d ? { ...d } : { name: '', description: '', price: 0, originalPrice: null, stock: 999, tags: '', categoryId: categoryId.value };
  showDish.value = true;
}

function openCategory() {
  categoryForm.value = { name: '', sort: 0 };
  showCategory.value = true;
}

async function saveDish() {
  try {
    await apiSaveDish({
      id: editing.value?.id,
      categoryId: form.value.categoryId,
      name: form.value.name,
      description: form.value.description,
      price: form.value.price,
      originalPrice: form.value.originalPrice,
      stock: form.value.stock,
      tags: form.value.tags,
    });
    ElMessage.success('保存成功');
    showDish.value = false;
    load();
  } catch (e: any) {
    ElMessage.error(e.message);
  }
}

async function saveCategory() {
  try {
    await apiSaveCategory(categoryForm.value);
    ElMessage.success('保存成功');
    showCategory.value = false;
    loadCategories();
  } catch (e: any) {
    ElMessage.error(e.message);
  }
}

async function toggleStatus(row: any) {
  try {
    await apiDishStatus(row.id, row.status === 1 ? 0 : 1);
    load();
  } catch (e: any) {
    ElMessage.error(e.message);
  }
}

async function loadCategories() {
  try {
    categories.value = (await apiCategories()) as any[];
  } catch {}
}

async function load() {
  try {
    const data: any = await apiDishes(categoryId.value);
    dishes.value = data.records || [];
  } catch {}
}

onMounted(() => {
  loadCategories();
  load();
});
</script>

<style scoped>
.form-tip {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}
</style>
