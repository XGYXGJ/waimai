import { get, post, put, del, type Merchant, type Dish, type Order, type RecommendItem, type Address, type CartItem, type Coupon } from '@waimai/shared';

// 认证
// remember=true 时后端签发 30 天有效期的 refresh token（「30 天免登录」）
export const apiLogin = (phone: string, password: string, remember = false) =>
  post<{ accessToken: string; refreshToken: string }>('/auth/login', { phone, password, remember });
export const apiRegister = (data: any) => post('/auth/register', data);
export const apiMe = () => get('/auth/me');

// 首页/商家
export const apiHome = (lng?: number, lat?: number) => get('/merchant/home', { lng, lat });
export const apiMerchantList = (params: any) => get('/merchant/list', params);
export const apiMerchantDetail = (id: number) => get(`/merchant/${id}`);
export const apiSearchDishes = (keyword: string) => get('/merchant/search-dishes', { keyword });

// 推荐
export const apiDailyRecommend = (lng?: number, lat?: number) => get('/recommend/daily', { lng, lat });

// 购物车
export const apiCartList = () => get<{ records: CartItem[]; merchantId?: number; deliveryFee?: number; packageFee?: number }>('/cart');
export const apiCartAdd = (dishId: number, quantity = 1) => post('/cart/add', null, { params: { dishId, quantity } });
export const apiCartUpdate = (dishId: number, quantity: number) => post('/cart/update', null, { params: { dishId, quantity } });
export const apiCartClear = () => del('/cart/clear');

// 订单
// 配送费按「商家 → 收货地址」的距离动态计算，所以传 addressId 才能拿到真实配送费
export const apiOrderPreview = (merchantId: number, addressId?: number) => get('/order/preview', { merchantId, addressId });
export const apiOrderCreate = (data: any) => post<Order>('/order', data);
export const apiOrderPay = (id: number) => post(`/order/${id}/pay`);
export const apiOrderCancel = (id: number) => post(`/order/${id}/cancel`);
export const apiMyOrders = (status?: string, page = 1, size = 10) => get('/order/my', { status, page, size });
export const apiOrderDetail = (id: number) => get(`/order/${id}`);

// 地址
export const apiAddresses = () => get<Address[]>('/user/addresses');
export const apiAddressSave = (data: any) => post('/user/address', data);
export const apiAddressDelete = (id: number) => del(`/user/address/${id}`);
export const apiAddressDefault = (id: number) => post(`/user/address/${id}/default`);

// 收藏
export const apiFavorites = () => get('/user/favorites');
export const apiToggleFavorite = (merchantId: number) => post(`/user/favorite/${merchantId}`);

// 优惠券
export const apiCouponHall = () => get('/coupon/hall');
export const apiReceiveCoupon = (id: number) => post(`/coupon/${id}/receive`);
export const apiMyCoupons = (status?: number) => get('/coupon/my', { status });
export const apiUsableCoupons = (merchantId: number, dishAmount: number) => get('/coupon/usable', { merchantId, dishAmount });

// 评价
export const apiSubmitReview = (orderId: number, data: any) => post(`/review/${orderId}`, data);
export const apiMerchantReviews = (merchantId: number) => get(`/review/merchant/${merchantId}`);

// AI 客服
export const apiChat = (content: string) => post<{ reply: string; intent: string; degraded: boolean }>('/ai/chat', { content });

// 图片上传（会话凭证、工单图片）
export const apiUpload = async (file: File): Promise<string> => {
  const fd = new FormData();
  fd.append('file', file);
  const res: any = await post('/common/upload', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return res?.url || '';
};

// 订单会话（用户 ↔ 商家）
export const apiImSessions = () => get('/im/sessions');
export const apiImOpen = (orderId: number) => post('/im/session/open', { orderId });
export const apiImMessages = (sessionId: number, sinceId?: number) =>
  get(`/im/session/${sessionId}/messages`, sinceId ? { sinceId } : {});
export const apiImSend = (sessionId: number, data: any) => post(`/im/session/${sessionId}/send`, data);
export const apiImTicketCreate = (sessionId: number, data: any) =>
  post(`/im/session/${sessionId}/ticket`, data);
export const apiImTickets = () => get('/im/tickets');

// 通知
export const apiNotifications = (page = 1, size = 20) => get('/notification', { page, size });
export const apiReadAll = () => post('/notification/read-all');

// 搜索
export const apiSaveSearch = (keyword: string) => post('/user/search', null, { params: { keyword } });
export const apiSearchHistory = () => get('/user/search-history');
