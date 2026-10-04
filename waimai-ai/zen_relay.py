#!/usr/bin/env python3
"""OpenCode Zen 免费模型 -> 本地 OpenAI 兼容端点。

[已归档 / 不再是项目依赖]
zen 门禁已内置进 app/llm/client.py 的 zen provider（在模型池里就是一个普通模型），
项目正常运行时不需要另起这个进程。本文件保留仅作独立调试工具：
想脱离项目单独验证 zen 门禁，或把 zen 暴露成标准 OpenAI 端点时可以用。

用法:
    python zen_relay.py [端口]            默认 8787
    python zen_relay.py 8787 --sticky    整个进程复用同一个 session

上游门禁(2026-10-03 实测): 缺少任一条件都会返回 403 FreeTierError
    1. User-Agent 必须是 opencode/1.18+     -> 见 UA
    2. x-opencode-session 必须是 ses_ + 26 位十六进制 -> 见 _new_session()
    3. body 中 stream 必须为 true            -> 上行强制改写
    4. body 必须携带 bash/glob/grep/read 工具 -> 缺失时自动注入
上行只接受流式,若客户端 stream=false,这里把 SSE 聚合成普通 JSON 返回。
"""
import json
import os
import sys
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib import request as urlrequest

UPSTREAM = "https://opencode.ai/zen/v1/chat/completions"
MODELS_URL = "https://opencode.ai/zen/v1/models"
UA = "opencode/1.18.13"  # 必须 >= 1.18,低于此版本上游拒绝

# 门禁要求 body 至少含这四个工具才能进入免费层
GATE_TOOLS = [
    {"type": "function", "function": {"name": n,
                                      "description": n,
                                      "parameters": {"type": "object",
                                                     "properties": {"p": {"type": "string"}},
                                                     "required": ["p"]}}}
    for n in ("bash", "glob", "grep", "read")
]

# --sticky 时整个进程复用一个 session,否则每次请求换新(更像真实客户端)
STICKY = "--sticky" in sys.argv
_SESSION = "ses_" + uuid.uuid4().hex[:26] if STICKY else None


def _new_session() -> str:
    """ses_ 前缀 + 26 位十六进制,格式不符会被上游拒绝。"""
    if _SESSION:
        return _SESSION
    return "ses_" + uuid.uuid4().hex[:26]


def _inject_tools(payload: dict) -> dict:
    """合并客户端自带的工具与门禁工具,不覆盖用户函数。"""
    existing = payload.get("tools") or []
    names = {t.get("function", {}).get("name") for t in existing}
    merged = list(existing) + [t for t in GATE_TOOLS
                               if t["function"]["name"] not in names]
    payload["tools"] = merged
    return payload


def _call_upstream(payload: dict):
    """始终以 stream=true 请求上游,返回可逐行读取的响应对象。"""
    payload = dict(payload)
    payload["stream"] = True           # 上行必须流式,false 直接 403
    _inject_tools(payload)             # 上行必须带门禁工具

    req = urlrequest.Request(
        UPSTREAM,
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "User-Agent": UA,
            "x-opencode-session": _new_session(),
            "Authorization": "Bearer public",  # 可省略,带上亦可
        },
        method="POST",
    )
    return urlrequest.urlopen(req, timeout=180)


def _aggregate(resp) -> dict:
    """把上游 SSE 聚合成 OpenAI 非流式响应体。"""
    content, reasoning, finish = [], [], "stop"
    model, cid = None, None
    for raw in resp:
        line = raw.decode("utf-8", "replace").strip()
        if not line.startswith("data:"):
            continue
        data = line[5:].strip()
        if data == "[DONE]":
            break
        try:
            chunk = json.loads(data)
        except json.JSONDecodeError:
            continue
        model = model or chunk.get("model")
        cid = cid or chunk.get("id")
        for ch in chunk.get("choices") or []:
            delta = ch.get("delta") or {}
            if delta.get("content"):
                content.append(delta["content"])
            if delta.get("reasoning_content") or delta.get("reasoning"):
                reasoning.append(delta.get("reasoning_content")
                                 or delta.get("reasoning"))
            if ch.get("finish_reason"):
                finish = ch["finish_reason"]
    msg = {"role": "assistant", "content": "".join(content)}
    if reasoning:
        msg["reasoning_content"] = "".join(reasoning)
    return {
        "id": cid or "zen-relay",
        "object": "chat.completion",
        "created": 0,
        "model": model or "unknown",
        "choices": [{"index": 0, "message": msg, "finish_reason": finish}],
        "usage": {"prompt_tokens": 0, "completion_tokens": 0, "total_tokens": 0},
    }


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):  # 静音默认日志
        pass

    def _send(self, code: int, body: bytes, ctype: str):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _json_err(self, code: int, msg: str):
        self._send(code, json.dumps({"error": {"message": msg}}).encode(),
                   "application/json")

    def do_GET(self):
        if self.path.startswith("/v1/models"):
            try:
                # 模型列表端点同样校验门禁头,缺 UA/session 会 403
                req = urlrequest.Request(MODELS_URL, headers={
                    "User-Agent": UA,
                    "x-opencode-session": _new_session(),
                    "Authorization": "Bearer public",
                })
                with urlrequest.urlopen(req, timeout=30) as r:
                    data = r.read()
                self._send(200, data, "application/json")
            except Exception as e:
                self._json_err(502, f"upstream models error: {e}")
        elif self.path == "/health":
            self._send(200, b'{"status":"ok"}', "application/json")
        else:
            self._json_err(404, "not found")

    def do_POST(self):
        if not self.path.startswith(("/v1/chat/completions", "/chat/completions")):
            return self._json_err(404, "not found")
        try:
            length = int(self.headers.get("Content-Length") or 0)
            payload = json.loads(self.rfile.read(length) or b"{}")
        except Exception as e:
            return self._json_err(400, f"bad request: {e}")

        want_stream = bool(payload.get("stream"))
        try:
            resp = _call_upstream(payload)
        except Exception as e:
            return self._json_err(502, f"upstream error: {e}")

        # 客户端要流式:原样透传 SSE
        if want_stream:
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.send_header("Cache-Control", "no-cache")
            self.send_header("Connection", "keep-alive")
            self.end_headers()
            try:
                for raw in resp:
                    self.wfile.write(raw)
                    self.wfile.flush()
                self.wfile.write(b"data: [DONE]\n\n")
                self.wfile.flush()
            except (BrokenPipeError, ConnectionResetError):
                pass
            return

        # 客户端要非流式:聚合后返回 JSON
        try:
            body = json.dumps(_aggregate(resp)).encode("utf-8")
        except Exception as e:
            return self._json_err(502, f"aggregate error: {e}")
        self._send(200, body, "application/json")


def main():
    port = 8787
    for arg in sys.argv[1:]:
        if arg.isdigit():
            port = int(arg)
    srv = ThreadingHTTPServer(("127.0.0.1", port), Handler)
    print(f"zen-relay listening on http://127.0.0.1:{port}/v1  "
          f"(upstream={UPSTREAM}, sticky={STICKY})")
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        print("\nbye")


if __name__ == "__main__":
    main()
