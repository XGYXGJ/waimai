// WebSocket 客户端：自动重连、心跳、类型化消息分发
import { getToken } from './request';

export type WsMessage = {
  type: string;
  [key: string]: any;
};

type Handler = (msg: WsMessage) => void;

export class WsClient {
  private ws: WebSocket | null = null;
  private handlers = new Map<string, Handler[]>();
  private heartbeatTimer: number | null = null;
  private reconnectAttempts = 0;
  private maxReconnect = 5;
  private url: string;

  constructor(url?: string) {
    const base = (import.meta.env.VITE_WS_BASE as string) || '';
    const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const host = base || `${proto}//${window.location.host}`;
    this.url = `${host}/ws?token=${getToken()}`;
  }

  connect() {
    if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) {
      return;
    }
    this.ws = new WebSocket(this.url);
    this.ws.onmessage = (e) => {
      try {
        const msg = JSON.parse(e.data) as WsMessage;
        (this.handlers.get(msg.type) || []).forEach((h) => h(msg));
      } catch {}
    };
    this.ws.onopen = () => {
      this.reconnectAttempts = 0;
      this.startHeartbeat();
    };
    this.ws.onclose = () => {
      this.stopHeartbeat();
      if (this.reconnectAttempts < this.maxReconnect) {
        this.reconnectAttempts++;
        setTimeout(() => this.connect(), 1000 * this.reconnectAttempts);
      }
    };
    this.ws.onerror = () => this.ws?.close();
  }

  on(type: string, handler: Handler) {
    if (!this.handlers.has(type)) this.handlers.set(type, []);
    this.handlers.get(type)!.push(handler);
    return () => this.off(type, handler);
  }

  off(type: string, handler: Handler) {
    const list = this.handlers.get(type);
    if (list) this.handlers.set(type, list.filter((h) => h !== handler));
  }

  send(msg: WsMessage) {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(msg));
    }
  }

  subscribeOrder(orderId: number) {
    this.send({ type: 'SUBSCRIBE_ORDER', orderId });
  }

  unsubscribeOrder(orderId: number) {
    this.send({ type: 'UNSUBSCRIBE_ORDER', orderId });
  }

  reportLocation(lng: number, lat: number, orderId?: number) {
    this.send({ type: 'LOCATION_REPORT', lng, lat, orderId });
  }

  private startHeartbeat() {
    this.stopHeartbeat();
    this.heartbeatTimer = window.setInterval(() => {
      this.send({ type: 'PING' });
    }, 30000);
  }

  private stopHeartbeat() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }

  close() {
    this.stopHeartbeat();
    this.ws?.close();
    this.ws = null;
  }
}
