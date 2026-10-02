// 共享类型定义（与后端 DTO 对应）
export interface R<T = any> {
  code: number;
  msg: string;
  data: T;
}

export interface UserInfo {
  id: number;
  phone: string;
  nickname: string;
  avatar?: string;
  role: 'USER' | 'MERCHANT' | 'RIDER' | 'ADMIN';
}

export interface Merchant {
  id: number;
  shopName: string;
  categoryId?: number;
  categoryName?: string;
  logo?: string;
  cover?: string;
  notice?: string;
  phone?: string;
  address?: string;
  lng?: number;
  lat?: number;
  businessHours?: string;
  minOrderAmount?: number;
  deliveryFee?: number;
  rating?: number;
  monthlySales?: number;
  openStatus?: number;
  distanceKm?: number;
  isAd?: boolean;
  adCampaignId?: number;
  bid?: number;
}

export interface Dish {
  id: number;
  merchantId: number;
  categoryId?: number;
  name: string;
  description?: string;
  image?: string;
  price: number;
  originalPrice?: number;
  unit?: string;
  stock?: number;
  monthlySales?: number;
  rating?: number;
  tags?: string;
  isRecommend?: number;
  status?: number;
}

export interface RecommendItem {
  dishId: number;
  dishName: string;
  image?: string;
  price: number;
  merchantId: number;
  merchantName?: string;
  source?: string;
  score?: number;
  reason?: string;
}

export interface Order {
  id: number;
  orderNo: string;
  merchantId: number;
  status: string;
  addressSnapshot?: string;
  dishAmount?: number;
  deliveryFee?: number;
  packageFee?: number;
  discountAmount?: number;
  payAmount?: number;
  riderId?: number;
  riderName?: string;
  riderPhone?: string;
  remark?: string;
  cancelReason?: string;
  createdAt?: string;
}

export interface Address {
  id: number;
  contact: string;
  phone: string;
  gender?: number;
  province?: string;
  city?: string;
  district?: string;
  detail?: string;
  lng?: number;
  lat?: number;
  tag?: string;
  isDefault?: number;
}

export interface CartItem {
  dishId: number;
  dishName: string;
  image?: string;
  price: number;
  quantity: number;
  stock?: number;
}

export interface Coupon {
  id: number;
  merchantId: number;
  name: string;
  type: number;
  thresholdAmount?: number;
  discountAmount?: number;
  discountRate?: number;
  totalCount?: number;
  receivedCount?: number;
  perUserLimit?: number;
  startTime?: string;
  endTime?: string;
  status?: number;
}
