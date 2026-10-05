import { get, put, del, post } from '@waimai/shared';

export const apiLogin = (phone: string, password: string, remember = false) =>
  post<{ accessToken: string; refreshToken: string }>('/auth/login', { phone, password, remember });

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

// ---- 收入结算 ----
export const apiIncomeOverview = () => get('/admin/income/overview');
export const apiIncomeMerchants = (page = 1, size = 10) => get('/admin/income/merchants', { page, size });

// ---- AI 模型池 ----
export const apiAiModels = () => get('/admin/ai-models');
export const apiSaveAiModel = (data: any) => post('/admin/ai-models', data);
export const apiDeleteAiModel = (id: number) => del(`/admin/ai-models/${id}`);
export const apiToggleAiModel = (id: number, enabled: number) =>
  put(`/admin/ai-models/${id}/enabled`, null, { params: { enabled } });
export const apiMoveAiModel = (id: number, direction: string) =>
  post(`/admin/ai-models/${id}/move`, null, { params: { direction } });
export const apiTestAiModel = (data: any) => post('/admin/ai-models/test', data);
export const apiTestAiModelById = (id: number) => post(`/admin/ai-models/${id}/test`);
/** 拉取该服务商当前可用的模型名列表 */
export const apiAiModelCatalog = (data: any) => post('/admin/ai-models/catalog', data);
/** 模型池运行状态：当前生效模型、各模型最近结果与冷却剩余 */
export const apiAiModelStatus = () => get('/admin/ai-models/status');
