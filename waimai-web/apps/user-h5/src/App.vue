<template>
  <router-view />
  <van-tabbar route v-if="showTabbar">
    <van-tabbar-item replace to="/" icon="wap-home-o">首页</van-tabbar-item>
    <van-tabbar-item replace to="/orders" icon="orders-o">订单</van-tabbar-item>
    <van-tabbar-item replace to="/chat" icon="chat-o">客服</van-tabbar-item>
    <van-tabbar-item replace to="/profile" icon="user-o">我的</van-tabbar-item>
  </van-tabbar>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';

const route = useRoute();
const showTabbar = computed(() => ['/', '/orders', '/chat', '/profile'].includes(route.path));
</script>
