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
  private reconnectTimer: number | null = null;
  private reconnectAttempts = 0;
  private maxReconnect = 5;
  private url: string;
  /**
   * 是否由 close() 主动关闭。
   *
   * 没有这个标志时 close() 会自杀式重连：close() 把 this.ws 置空并调用 socket.close()，
   * socket 随后异步触发 onclose，onclose 看到 reconnectAttempts(0) < maxReconnect(5)
   * 就 setTimeout(() => this.connect()) —— 而此时 this.ws 已是 null，connect() 会
   * 新建一条连接，onopen 又 startHeartbeat()。结果：组件卸载后仍留下一条活连接和一个
   * 30 秒心跳定时器，反复进出页面就会累积多个孤儿连接（每个都持有已卸载组件的闭包）。
   */
  private closedByUser = false;

  constructor(url?: string) {
    const base = (import.meta.env.VITE_WS_BASE as string) || '';
    const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const host = base || `${proto}//${window.location.host}`;
    this.url = `${host}/ws?token=${getToken()}`;
  }

  connect() {
    if (this.closedByUser) return;   // close() 之后不再复活
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
      if (this.closedByUser) return;
      if (this.reconnectAttempts < this.maxReconnect) {
        this.reconnectAttempts++;
        this.clearReconnect();
        this.reconnectTimer = window.setTimeout(() => {
          this.reconnectTimer = null;
          this.connect();
        }, 1000 * this.reconnectAttempts);
      }
    };
    this.ws.onerror = () => this.ws?.close();
  }

  /** 重新启用自动重连（同一个实例复用时需要） */
  reopen() {
    this.closedByUser = false;
    this.reconnectAttempts = 0;
    this.connect();
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

  private clearReconnect() {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }

  /** 彻底关闭：停心跳、取消重连、断开连接（不会自动复活） */
  close() {
    this.closedByUser = true;
    this.stopHeartbeat();
    this.clearReconnect();
    const sock = this.ws;
    this.ws = null;
    // 先摘掉 onclose，避免 close() 触发的那次回调再排一次重连
    if (sock) {
      sock.onclose = null;
      sock.onerror = null;
      sock.onmessage = null;
      sock.onopen = null;
      try {
        sock.close();
      } catch {}
    }
  }
}
