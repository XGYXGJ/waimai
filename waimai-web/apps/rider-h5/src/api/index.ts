import { get, post } from '@waimai/shared';

export const apiLogin = (phone: string, password: string, remember = false) =>
  post<{ accessToken: string; refreshToken: string }>('/auth/login', { phone, password, remember });

export const apiRegister = (data: { phone: string; password: string; nickname?: string; role: string }) =>
  post('/auth/register', data);

export const apiRiderInfo = () => get('/rider/info');
export const apiHall = () => get('/rider/hall');
export const apiDeliveries = () => get('/rider/deliveries');
export const apiGrab = (id: number) => post(`/rider/order/${id}/grab`);
export const apiPickup = (id: number) => post(`/rider/order/${id}/pickup`);
export const apiDeliver = (id: number) => post(`/rider/order/${id}/deliver`);
export const apiReportLocation = (lng: number, lat: number, orderId?: number) =>
  post('/rider/location', null, { params: { lng, lat, orderId } });
