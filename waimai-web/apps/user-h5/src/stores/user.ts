import { defineStore } from 'pinia';
import { get, type UserInfo } from '@waimai/shared';

export const useUserStore = defineStore('user', {
  state: () => ({
    info: null as UserInfo | null,
  }),
  actions: {
    async fetch() {
      try {
        this.info = await get<UserInfo>('/auth/me');
      } catch {}
    },
  },
});
