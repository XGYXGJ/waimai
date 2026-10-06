<template>
  <router-view />
  <!-- z-index 显式抬高：页面内容变长时，最后一张卡片不能压在 tabbar 上把按钮挡住 -->
  <van-tabbar route v-if="showTabbar" :style="{ zIndex: 100 }">
    <van-tabbar-item replace to="/" icon="orders-o">抢单大厅</van-tabbar-item>
    <van-tabbar-item replace to="/delivering" icon="logistics">进行中</van-tabbar-item>
    <van-tabbar-item replace to="/history" icon="clock-o">历史</van-tabbar-item>
    <van-tabbar-item replace to="/me" icon="contact-o">我的</van-tabbar-item>
  </van-tabbar>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
const route = useRoute();
/** 只有这几个是 tab 页；地图/沟通/个人中心是二级页，隐藏 tabbar 由页面自己给出入口 */
const showTabbar = computed(() => ['/', '/delivering', '/history', '/me'].includes(route.path));
</script>
