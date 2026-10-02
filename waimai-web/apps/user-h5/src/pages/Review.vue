<template>
  <div class="review-page">
    <van-nav-bar title="评价" left-arrow @click-left="$router.back()" />
    <div class="review-body">
      <div class="rating-label">评分</div>
      <van-rate v-model="rating" :size="28" />
      <div class="content-label">评价内容</div>
      <van-field v-model="content" type="textarea" rows="4" placeholder="说说这次用餐体验吧" maxlength="200" show-word-limit />
      <van-button round block type="primary" color="#ff6034" style="margin-top: 24px" @click="submit">提交评价</van-button>
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
.review-body {
  padding: 24px 16px;
}
.rating-label,
.content-label {
  font-size: 15px;
  font-weight: 600;
  margin: 16px 0 8px;
}
</style>
