import { get, post } from '@waimai/shared';

export const apiLogin = (phone: string, password: string, remember = false) =>
  post<{ accessToken: string; refreshToken: string }>('/auth/login', { phone, password, remember });

export const apiRegister = (data: { phone: string; password: string; nickname?: string; role: string }) =>
  post('/auth/register', data);

export const apiRiderInfo = () => get('/rider/info');
export const apiSetOnline = (value: boolean) => post('/rider/online', null, { params: { value } });
export const apiHall = () => get('/rider/hall');
export const apiDeliveries = () => get('/rider/deliveries');
/** 历史订单：只含已送达，与 /rider/deliveries 分开 */
export const apiHistory = () => get('/rider/history');
export const apiGrab = (id: number) => post(`/rider/order/${id}/grab`);
export const apiPickup = (id: number) => post(`/rider/order/${id}/pickup`);
export const apiDeliver = (id: number) => post(`/rider/order/${id}/deliver`);
export const apiReportLocation = (lng: number, lat: number, orderId?: number) =>
  post('/rider/location', null, { params: { lng, lat, orderId } });
/** 订单详情：骑手端用它取顾客地址坐标（detail 接口已放行 RIDER 角色） */
export const apiOrderDetail = (id: number) => get(`/order/${id}`);

/* ---------- 订单三方会话（骑手侧） ---------- */

export const apiImSessions = () => get('/rider/im/sessions');
export const apiImOpen = (orderId: number) => post('/rider/im/session/open', { orderId });
export const apiImMessages = (sessionId: number, sinceId?: number) =>
  get(`/rider/im/session/${sessionId}/messages`, sinceId ? { sinceId } : {});
/** 骑手只能发文本与图片（工单由商家处置） */
export const apiImSend = (sessionId: number, data: any) =>
  post(`/rider/im/session/${sessionId}/send`, data);
export const apiImTickets = () => get('/rider/im/tickets');

export const apiUpload = async (file: File): Promise<string> => {
  const fd = new FormData();
  fd.append('file', file);
  const res: any = await post('/common/upload', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return res?.url || '';
};
