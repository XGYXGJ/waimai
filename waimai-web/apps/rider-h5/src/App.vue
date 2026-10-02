<template>
  <router-view />
  <van-tabbar route v-if="showTabbar">
    <van-tabbar-item replace to="/" icon="orders-o">抢单大厅</van-tabbar-item>
    <van-tabbar-item replace to="/delivering" icon="logistics">进行中</van-tabbar-item>
    <van-tabbar-item replace to="/history" icon="clock-o">历史</van-tabbar-item>
  </van-tabbar>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
const route = useRoute();
const showTabbar = computed(() => ['/', '/delivering', '/history'].includes(route.path));
</script>
