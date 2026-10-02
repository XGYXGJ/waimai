import { get, put, del, post } from '@waimai/shared';

export const apiLogin = (phone: string, password: string) =>
  post<{ accessToken: string }>('/auth/login', { phone, password });

export const apiDashboard = () => get('/admin/dashboard');

export const apiMerchants = (auditStatus?: number, page = 1, size = 10) => get('/admin/merchants', { auditStatus, page, size });
export const apiAuditMerchant = (id: number, pass: boolean, remark?: string) => put(`/admin/merchants/${id}/audit`, { pass, remark });

export const apiUsers = (role?: string, page = 1, size = 10) => get('/admin/users', { role, page, size });
export const apiUserStatus = (id: number, status: number) => put(`/admin/users/${id}/status`, null, { params: { status } });

export const apiRiders = (auditStatus?: number, page = 1, size = 10) => get('/admin/riders', { auditStatus, page, size });
export const apiAuditRider = (id: number, pass: boolean) => put(`/admin/riders/${id}/audit`, { pass });

export const apiOrders = (keyword?: string, status?: string, page = 1, size = 10) => get('/admin/orders', { keyword, status, page, size });
export const apiRefund = (id: number) => put(`/admin/orders/${id}/refund`);

export const apiReviews = (merchantId?: number, page = 1, size = 10) => get('/admin/reviews', { merchantId, page, size });
export const apiDeleteReview = (id: number) => del(`/admin/reviews/${id}`);

export const apiAuditCampaign = (id: number, pass: boolean) => put(`/admin/bid-campaigns/${id}/audit`, { pass });

export const apiConfigs = () => get('/admin/config');
export const apiUpdateConfig = (key: string, configValue: string) => put(`/admin/config/${key}`, { configValue });
