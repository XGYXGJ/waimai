import { get, post, put, http, type Order, type Dish } from '@waimai/shared';

export const apiLogin = (phone: string, password: string, remember = false) =>
  post<{ accessToken: string; refreshToken: string }>('/auth/login', { phone, password, remember });

export const apiRegister = (data: { phone: string; password: string; nickname?: string; role: string }) =>
  post('/auth/register', data);

/** 图片上传（FormData），返回 { url } */
export const apiUpload = (file: File): Promise<{ url: string }> => {
  const fd = new FormData();
  fd.append('file', file);
  return http.post('/common/upload', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
  }) as unknown as Promise<{ url: string }>;
};

/** 商户经营分类选项 */
export const apiCategoryOptions = () => get('/merchant/category-options');

export const apiShopInfo = () => get('/merchant/shop/info');
export const apiUpdateShop = (data: any) => put('/merchant/shop', data);
export const apiDashboard = () => get('/merchant/dashboard');

export const apiCategories = () => get('/merchant/categories');
export const apiSaveCategory = (data: any) => post('/merchant/category', null, { params: data });
export const apiDeleteCategory = (id: number) => import('@waimai/shared').then((m) => m.del(`/merchant/category/${id}`));

export const apiDishes = (categoryId?: number) => get('/merchant/dishes', { categoryId });
export const apiSaveDish = (data: any) => post('/merchant/dish', data);
export const apiDishStatus = (id: number, status: number) => post(`/merchant/dish/${id}/status`, null, { params: { status } });

export const apiMerchantOrders = (status?: string, page = 1, size = 10) => get('/order/merchant', { status, page, size });
export const apiAcceptOrder = (id: number) => post(`/order/${id}/accept`);
export const apiRejectOrder = (id: number, reason?: string) => post(`/order/${id}/reject`, null, { params: { reason } });
export const apiReadyOrder = (id: number) => post(`/order/${id}/ready`);

export const apiMerchantCoupons = () => get('/merchant/coupons');
export const apiCreateCoupon = (data: any) => post('/merchant/coupon', data);
export const apiOffCoupon = (id: number) => post(`/merchant/coupon/${id}/off`);

export const apiCampaigns = () => get('/merchant/campaigns');
export const apiCreateCampaign = (data: any) => post('/merchant/campaign', data);
export const apiCampaignStatus = (id: number, status: number) => post(`/merchant/campaign/${id}/status`, null, { params: { status } });

export const apiForecast = () => get('/merchant/forecast');

export const apiReviews = () => get('/review/my');
export const apiMerchantReviews = (merchantId: number) => get(`/review/merchant/${merchantId}`);
export const apiReplyReview = (reviewId: number, reply: string) => post(`/review/${reviewId}/reply`, null, { params: { reply } });

/* ---------- 顾客会话 / 售后工单 ---------- */
export const apiImSessions = () => get('/merchant/im/sessions');
export const apiImMessages = (sessionId: number, sinceId?: number) =>
  get(`/merchant/im/session/${sessionId}/messages`, sinceId ? { sinceId } : {});
export const apiImSend = (sessionId: number, data: any) => post(`/merchant/im/session/${sessionId}/send`, data);
export const apiImTickets = (status?: string) => get('/merchant/im/tickets', status ? { status } : {});
export const apiImHandleTicket = (id: number, action: string, reply: string) =>
  post(`/merchant/im/ticket/${id}/handle`, { action, reply });
