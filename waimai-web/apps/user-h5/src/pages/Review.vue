<template>
  <div class="review-page">
    <van-nav-bar title="评价" left-arrow @click-left="$router.back()" />

    <div class="review-body">
      <!-- 星级评分 -->
      <section class="card">
        <h2 class="card-title">
          <van-icon name="star" size="16" class="title-icon" />
          综合评分
        </h2>
        <div class="rating-row">
          <van-rate v-model="rating" :size="30" />
          <span class="rating-value">{{ rating }}.0</span>
        </div>
        <p class="rating-hint">点亮星星，给这次用餐体验打个分</p>
      </section>

      <!-- 评价内容 -->
      <section class="card">
        <h2 class="card-title">
          <van-icon name="edit" size="16" class="title-icon" />
          评价内容
        </h2>
        <van-field
          v-model="content"
          type="textarea"
          rows="4"
          placeholder="说说这次用餐体验吧"
          maxlength="200"
          show-word-limit
          class="content-field"
        />
      </section>

      <!-- 提交 -->
      <section class="card submit-card">
        <van-button round block type="primary" color="var(--wm-primary)" class="submit-btn" @click="submit">
          提交评价
        </van-button>
        <p class="submit-tip">提交后不可修改，请确认内容无误</p>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { showToast } from 'vant';
import { apiSubmitReview } from '@/api';

const route = useRoute();
const router = useRouter();
const orderId = Number(route.params.id);
const rating = ref(5);
const content = ref('');

async function submit() {
  try {
    await apiSubmitReview(orderId, { rating: rating.value, content: content.value });
    showToast('评价成功');
    router.replace('/orders');
  } catch (e: any) {
    showToast(e.message);
  }
}
</script>

<style scoped>
.review-page {
  min-height: 100vh;
  background: var(--wm-bg-page);
  padding-bottom: calc(var(--wm-space-6) + env(safe-area-inset-bottom));
}

.review-body {
  padding-top: var(--wm-space-3);
}

/* ---------------- 卡片壳 ---------------- */
.card {
  margin: var(--wm-space-3);
  padding: var(--wm-space-4);
  background: var(--wm-bg-card);
  border-radius: var(--wm-radius-lg);
  box-shadow: var(--wm-shadow-1);
}

.card-title {
  display: flex;
  align-items: center;
  gap: var(--wm-space-2);
  margin-bottom: var(--wm-space-3);
  font-size: var(--wm-font-lg);
  font-weight: 600;
  line-height: var(--wm-leading-tight);
  color: var(--wm-text-1);
}

.title-icon {
  color: var(--wm-primary);
}

/* ---------------- 评分 ---------------- */
.rating-row {
  display: flex;
  align-items: center;
  gap: var(--wm-space-3);
  min-height: var(--wm-tap-min);
}

.rating-value {
  font-size: var(--wm-font-xl);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--wm-primary);
}

.rating-hint {
  margin-top: var(--wm-space-2);
  font-size: var(--wm-font-sm);
  line-height: var(--wm-leading-normal);
  color: var(--wm-text-3);
}

/* ---------------- 评价内容 ---------------- */
.content-field {
  padding: 0;
  background: transparent;
}

.content-field :deep(.van-field__body) {
  padding: var(--wm-space-3);
  border: 1px solid var(--wm-border);
  border-radius: var(--wm-radius-md);
  background: var(--wm-bg-page);
}

.content-field :deep(.van-field__control) {
  min-height: calc(var(--wm-tap-min) * 2);
  padding-bottom: var(--wm-space-5);
  font-size: var(--wm-font-md);
  line-height: var(--wm-leading-normal);
  color: var(--wm-text-1);
}

/* 字数统计：Vant 默认会把它隐藏，这里显式显示成输入框右下角的辅助文字 */
.content-field :deep(.van-field__word-limit) {
  right: var(--wm-space-3);
  bottom: var(--wm-space-2);
  display: block;
  font-size: var(--wm-font-sm);
  color: var(--wm-text-4);
}

/* ---------------- 提交 ---------------- */
.submit-card {
  padding-bottom: var(--wm-space-3);
}

.submit-btn {
  min-height: var(--wm-tap-min);
  font-size: var(--wm-font-lg);
}

.submit-tip {
  margin-top: var(--wm-space-3);
  text-align: center;
  font-size: var(--wm-font-xs);
  color: var(--wm-text-4);
}
</style>
