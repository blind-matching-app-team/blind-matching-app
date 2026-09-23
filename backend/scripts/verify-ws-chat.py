#!/usr/bin/env python3
"""BMA-73 WebSocket 실시간 채팅 인프라 종단 검증 (표준 라이브러리만 사용).

앱이 떠 있는 상태에서 실행한다. 회원가입/로그인/매칭은 REST 로 만들고, 실제 WebSocket(STOMP) 으로
두 클라이언트가 같은 방에 붙어 실시간으로 주고받는지, JWT 인증·방 권한·하트비트·DB 저장·재접속 복구·
차단 상대 메시지(S11-08) 를 확인한다.

    BASE=http://localhost:8080 python3 backend/scripts/verify-ws-chat.py
    LOG=/tmp/verify-ws.tsv 를 주면 단계별 결과를 TSV 로 남긴다(보고서용).

websocket 라이브러리가 없는 환경(WSL 기본 python3)에서도 돌도록 RFC 6455 프레이밍을 직접 구현했다.
SockJS 엔드포인트의 원시 WebSocket 경로(/ws/websocket)에 붙는다 — 브라우저 SockJS 클라이언트도 같은 경로를 쓴다.
"""
import base64
import json
import os
import socket
import struct
import sys
import time
import urllib.error
import urllib.request
from urllib.parse import urlparse

BASE = os.environ.get("BASE", "http://localhost:8080")
LOG = os.environ.get("LOG")
TS = int(time.time())
PW = "Passw0rd!23"
PASS = 0
FAIL = 0
STEP = ""

if LOG:
    open(LOG, "w", encoding="utf-8").close()


# ---------------------------------------------------------------- REST 도우미
def req(method, path, token=None, body=None):
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    r = urllib.request.Request(BASE + path, data=data, method=method)
    r.add_header("Content-Type", "application/json")
    if token:
        r.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(r, timeout=10) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8") or "{}")
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode("utf-8") or "{}")
        except Exception:
            return e.code, {}


def signup_login(n):
    email = f"bma73-{n}-{TS}@example.com"
    _, s = req("POST", "/api/v1/auth/signup", body={"email": email, "password": PW})
    _, l = req("POST", "/api/v1/auth/login", body={"email": email, "password": PW})
    return s["data"]["userId"], l["data"]["accessToken"]


def profile(token, nick, gender, region, year):
    return req("PUT", "/api/v1/users/me/profile", token,
               {"nickname": nick, "birthDate": f"{year}-05-05", "genderCode": gender, "regionCode": region})


def step(name):
    global STEP
    STEP = name
    print(f"[{name}]")


def check(name, ok, detail=""):
    global PASS, FAIL
    if ok:
        PASS += 1
        print(f"  ✔ {name}")
    else:
        FAIL += 1
        print(f"  ✘ {name}  {detail}")
    if LOG:
        with open(LOG, "a", encoding="utf-8") as f:
            d = str(detail).replace("\t", " ").replace("\n", " ")[:700]
            f.write(f"{STEP}\tWS\t\t\t{'true' if ok else 'false'}\t{name}\t{d}\n")


# ---------------------------------------------------------------- 원시 WebSocket + STOMP
class Stomp:
    """STOMP over raw WebSocket (RFC 6455, 클라이언트 마스킹, 텍스트 프레임)."""

    def __init__(self, base=BASE, path="/ws/websocket"):
        u = urlparse(base)
        self.host, self.port = u.hostname, u.port or 80
        self.sock = socket.create_connection((self.host, self.port), timeout=10)
        key = base64.b64encode(os.urandom(16)).decode()
        handshake = (f"GET {path} HTTP/1.1\r\nHost: {self.host}:{self.port}\r\nUpgrade: websocket\r\n"
                     f"Connection: Upgrade\r\nSec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n\r\n")
        self.sock.sendall(handshake.encode())
        resp = b""
        while b"\r\n\r\n" not in resp:
            chunk = self.sock.recv(4096)
            if not chunk:
                raise RuntimeError("handshake closed")
            resp += chunk
        self.http_status = int(resp.split(b" ")[1])
        if self.http_status != 101:
            raise RuntimeError(f"handshake failed: {resp[:200]!r}")
        self.buf = b""
        self.frames = []          # 수신한 STOMP 프레임 (command, headers, body)
        self.heartbeats = 0       # 서버가 보낸 하트비트('\n') 수
        self.closed = False
        self.sub_seq = 0

    # -- WebSocket 프레이밍
    def _send_text(self, text):
        payload = text.encode("utf-8")
        header = bytearray([0x81])
        n = len(payload)
        if n < 126:
            header.append(0x80 | n)
        elif n < 65536:
            header.append(0x80 | 126)
            header += struct.pack(">H", n)
        else:
            header.append(0x80 | 127)
            header += struct.pack(">Q", n)
        mask = os.urandom(4)
        header += mask
        masked = bytes(b ^ mask[i % 4] for i, b in enumerate(payload))
        self.sock.sendall(bytes(header) + masked)

    def _read_frame(self, timeout):
        """하나의 WebSocket 프레임을 읽어 (opcode, payload) 를 돌려준다. 타임아웃이면 None."""
        self.sock.settimeout(timeout)
        try:
            while True:
                if len(self.buf) >= 2:
                    b0, b1 = self.buf[0], self.buf[1]
                    opcode = b0 & 0x0F
                    n = b1 & 0x7F
                    idx = 2
                    if n == 126:
                        if len(self.buf) < 4:
                            self.buf += self.sock.recv(4096); continue
                        n = struct.unpack(">H", self.buf[2:4])[0]; idx = 4
                    elif n == 127:
                        if len(self.buf) < 10:
                            self.buf += self.sock.recv(4096); continue
                        n = struct.unpack(">Q", self.buf[2:10])[0]; idx = 10
                    if b1 & 0x80:
                        idx += 4  # 서버는 마스킹하지 않지만 방어적으로
                    if len(self.buf) >= idx + n:
                        payload = self.buf[idx:idx + n]
                        self.buf = self.buf[idx + n:]
                        return opcode, payload
                chunk = self.sock.recv(65536)
                if not chunk:
                    self.closed = True
                    return None
                self.buf += chunk
        except socket.timeout:
            return None
        except (ConnectionResetError, OSError):
            self.closed = True
            return None

    # -- STOMP
    def _frame(self, command, headers, body=""):
        lines = [command] + [f"{k}:{v}" for k, v in headers.items()]
        return "\n".join(lines) + "\n\n" + body + "\x00"

    def send_frame(self, command, headers, body=""):
        self._send_text(self._frame(command, headers, body))

    def pump(self, timeout=1.0, until=None):
        """timeout 동안 프레임을 읽어 self.frames 에 쌓는다. until(frame) 이 참이 되면 그 프레임을 돌려준다."""
        deadline = time.time() + timeout
        while time.time() < deadline:
            remaining = max(0.05, deadline - time.time())
            fr = self._read_frame(remaining)
            if fr is None:
                if self.closed:
                    return None
                continue
            opcode, payload = fr
            if opcode == 0x8:      # close
                self.closed = True
                return None
            if opcode == 0x9:      # ping → pong
                continue
            text = payload.decode("utf-8", "replace")
            for raw in text.split("\x00"):
                if raw == "" or raw == "\n":
                    if raw == "\n" or text == "\n":
                        self.heartbeats += 1
                    continue
                raw = raw.lstrip("\n")
                if not raw:
                    self.heartbeats += 1
                    continue
                head, _, body = raw.partition("\n\n")
                lines = head.split("\n")
                headers = {}
                for line in lines[1:]:
                    k, _, v = line.partition(":")
                    headers.setdefault(k, v)
                frame = (lines[0], headers, body)
                self.frames.append(frame)
                if until and until(frame):
                    return frame
        return None

    def connect(self, token, heartbeat="10000,10000"):
        headers = {"accept-version": "1.2,1.1,1.0", "heart-beat": heartbeat, "host": self.host}
        if token is not None:
            headers["Authorization"] = "Bearer " + token
        self.send_frame("CONNECT", headers)
        return self.pump(5, lambda f: f[0] in ("CONNECTED", "ERROR"))

    def subscribe(self, destination):
        self.sub_seq += 1
        sid = f"sub-{self.sub_seq}"
        self.send_frame("SUBSCRIBE", {"id": sid, "destination": destination})
        time.sleep(0.3)
        return sid

    def send_chat(self, room, content):
        body = json.dumps({"messageType": "TEXT", "content": content}, ensure_ascii=False)
        self.send_frame("SEND", {"destination": f"/app/chat/{room}/send", "content-type": "application/json",
                                 "content-length": str(len(body.encode("utf-8")))}, body)

    def wait_message(self, timeout=5, pred=None):
        def until(f):
            if f[0] != "MESSAGE":
                return False
            try:
                data = json.loads(f[2])
            except Exception:
                return False
            return pred is None or pred(f[1], data)
        fr = self.pump(timeout, until)
        return (fr[1], json.loads(fr[2])) if fr else (None, None)

    def close(self):
        try:
            self.send_frame("DISCONNECT", {"receipt": "bye"})
            self.pump(0.5)
            self.sock.close()
        except Exception:
            pass
        self.closed = True


# ---------------------------------------------------------------- 시나리오
def main():
    step("1. A·B 가입/프로필/상호 좋아요 → 매칭·채팅방, C 는 비참여자")
    user_a, tok_a = signup_login("a"); user_b, tok_b = signup_login("b"); user_c, tok_c = signup_login("c")
    n = TS % 10000
    profile(tok_a, f"소켓A{n}", "M", "SEOUL_GANGNAM", 1995)
    profile(tok_b, f"소켓B{n}", "F", "SEOUL_JUNG", 1997)
    profile(tok_c, f"소켓C{n}", "M", "SEOUL_GANGNAM", 1990)
    req("POST", "/api/v1/matching/actions", tok_b, {"targetUserId": user_a, "actionType": "LIKE"})
    code, j = req("POST", "/api/v1/matching/actions", tok_a, {"targetUserId": user_b, "actionType": "LIKE"})
    room = j.get("data", {}).get("chatRoomId")
    check("매칭 성사, chatRoomId 발급", code == 200 and room is not None, j)
    if room is None:
        return finish()

    step("2. 토큰 없이 CONNECT → ERROR / 잘못된 토큰 → ERROR (JWT 인증)")
    s = Stomp(); fr = s.connect(None); s.close()
    ok1 = fr is not None and fr[0] == "ERROR"
    s = Stomp(); fr2 = s.connect("not.a.jwt"); s.close()
    check("ERROR 프레임 ×2 (세션 미개방)", ok1 and fr2 is not None and fr2[0] == "ERROR",
          f"{fr and fr[0]} {fr and fr[1].get('message')} / {fr2 and fr2[0]} {fr2 and fr2[1].get('message')}")

    step("3. A·B CONNECT (액세스 토큰) → CONNECTED, heart-beat 10000,10000 협상")
    a = Stomp(); fa = a.connect(tok_a)
    b = Stomp(); fb = b.connect(tok_b)
    check("CONNECTED ×2, heart-beat=10000,10000", fa and fa[0] == "CONNECTED" and fb and fb[0] == "CONNECTED"
          and fa[1].get("heart-beat") == "10000,10000", f"{fa and fa[1]} / {fb and fb[1]}")

    step("4. A·B 가 /topic/chat/{room} 구독 → A 가 STOMP 로 전송 → B 즉시 수신, A 에코, 발신자=세션 주체")
    a.subscribe(f"/topic/chat/{room}"); b.subscribe(f"/topic/chat/{room}")
    a.send_chat(room, "안녕하세요 소켓으로 보냅니다")
    hb, mb = b.wait_message(5); ha, ma = a.wait_message(5)
    msg1 = mb and mb.get("messageId")
    check("B 수신 content 일치·senderUserId=A·messageId 발급, A 도 같은 messageId 수신",
          mb is not None and mb.get("content") == "안녕하세요 소켓으로 보냅니다" and mb.get("senderUserId") == user_a
          and msg1 is not None and ma is not None and ma.get("messageId") == msg1, f"B={mb} A={ma}")

    step("5. B 가 REST 로 전송 → A 가 소켓으로 수신 (REST/STOMP 같은 토픽)")
    code, j = req("POST", f"/api/v1/chat/rooms/{room}/messages", tok_b, {"messageType": "TEXT", "content": "REST 로 답장"})
    ha, ma = a.wait_message(5)
    check("REST 200, A 소켓 수신 content='REST 로 답장' senderUserId=B",
          code == 200 and ma is not None and ma.get("content") == "REST 로 답장" and ma.get("senderUserId") == user_b, f"{code} {ma}")
    b.wait_message(2)  # B 자신의 에코 소비

    step("6. DB 저장 확인: REST 이력에 소켓으로 보낸 메시지가 있다 (최신순, 2건)")
    code, j = req("GET", f"/api/v1/chat/rooms/{room}/messages?page=0&size=30", tok_a)
    content = j.get("data", {}).get("content", [])
    ids = [m.get("messageId") for m in content]
    check("200, totalElements 2, 소켓 메시지 ID 포함, 최신순", code == 200 and j["data"].get("totalElements") == 2
          and msg1 in ids and ids == sorted(ids, reverse=True), f"{code} ids={ids}")

    step("7. 비참여자 C 가 방 구독 → ERROR (방 단위 인가) / 남의 방으로 SEND → ERROR")
    c = Stomp(); fc = c.connect(tok_c)
    c.subscribe(f"/topic/chat/{room}")
    e1 = c.pump(3, lambda f: f[0] == "ERROR")
    c2 = Stomp(); c2.connect(tok_c)
    c2.send_chat(room, "끼어들기")
    e2 = c2.pump(3, lambda f: f[0] == "ERROR")
    c.close(); c2.close()
    check("CONNECTED 후 구독 ERROR, SEND ERROR", fc and fc[0] == "CONNECTED" and e1 is not None and e2 is not None,
          f"{e1 and e1[1].get('message')} / {e2 and e2[1].get('message')}")
    code, j = req("GET", f"/api/v1/chat/rooms/{room}/messages", tok_b)
    check("C 의 SEND 는 저장되지 않음 (이력 여전히 2건)", code == 200 and j["data"].get("totalElements") == 2, j.get("data", {}).get("totalElements"))

    step("8. 재접속 복구: B 연결 끊김 → A 가 2건 전송 → B 재접속·재구독 → REST 이력으로 따라잡고 새 메시지는 실시간 수신")
    last_seen = max(ids)
    b.close()
    a.send_chat(room, "끊긴 사이 1"); a.wait_message(3)
    a.send_chat(room, "끊긴 사이 2"); a.wait_message(3)
    b2 = Stomp(); fb2 = b2.connect(tok_b); b2.subscribe(f"/topic/chat/{room}")
    code, j = req("GET", f"/api/v1/chat/rooms/{room}/messages?page=0&size=30", tok_b)
    missed = [m for m in j.get("data", {}).get("content", []) if m.get("messageId", 0) > last_seen]
    a.send_chat(room, "재접속 후 실시간")
    hb, mb = b2.wait_message(5, lambda h, d: d.get("content") == "재접속 후 실시간")
    check("재CONNECTED, 놓친 2건은 이력에서 확인('끊긴 사이 1/2'), 재접속 후 메시지 실시간 수신",
          fb2 and fb2[0] == "CONNECTED" and sorted(m["content"] for m in missed) == ["끊긴 사이 1", "끊긴 사이 2"]
          and mb is not None, f"missed={[m.get('content') for m in missed]} live={mb}")
    a.wait_message(2)

    step("9. 하트비트: 서버가 10초 주기로 빈 프레임을 보낸다 (12초 대기)")
    before = a.heartbeats
    a.pump(12)
    check("12초 내 서버 하트비트 ≥1", a.heartbeats > before, f"heartbeats={a.heartbeats - before}")

    _, notif_before = req("GET", "/api/v1/notifications?page=0&size=50", tok_a)
    msg_notif_before = sum(1 for x in (notif_before.get("data", {}).get("content") or []) if x.get("eventCode") == "MESSAGE_RECEIVED")
    step("10. S11-08 차단: A 가 B 차단 → A 는 방에서 나감(A 목록 0) → B 가 STOMP 전송 → B 는 /user/queue/chat 에코만, A 토픽 수신 없음, B 이력엔 남음")
    code, j = req("POST", f"/api/v1/users/{user_b}/block", tok_a, {"reason": "test"})
    left = j.get("data", {}).get("chatRoomLeft")
    _, rooms_a = req("GET", "/api/v1/chat/rooms", tok_a)
    b2.subscribe("/user/queue/chat")
    b2.send_chat(room, "차단당한 뒤 메시지")
    hq, mq = b2.wait_message(5, lambda h, d: d.get("content") == "차단당한 뒤 메시지")
    ha, ma = a.wait_message(2)
    _, hist_b = req("GET", f"/api/v1/chat/rooms/{room}/messages?page=0&size=5", tok_b)
    top_b = hist_b.get("data", {}).get("content", [{}])[0].get("content")
    check("차단 200 chatRoomLeft=true, A 목록 0, B 에코 수신(destination=/user/queue/chat), A 수신 없음, B 이력 최신='차단당한 뒤 메시지'",
          code == 200 and left is True and rooms_a.get("data") == [] and mq is not None
          and hq.get("destination") == "/user/queue/chat" and ma is None and top_b == "차단당한 뒤 메시지",
          f"left={left} roomsA={len(rooms_a.get('data', []))} echo={hq} A={ma} topB={top_b}")
    code, j = req("GET", "/api/v1/chat/rooms/unread-count", tok_a)
    _, notif = req("GET", "/api/v1/notifications?page=0&size=50", tok_a)
    msg_notif_after = sum(1 for x in (notif.get("data", {}).get("content") or []) if x.get("eventCode") == "MESSAGE_RECEIVED")
    check("A 안읽음 0, 차단 후 A 에게 MESSAGE_RECEIVED 알림이 늘지 않음",
          code == 200 and j["data"].get("unreadCount") == 0 and msg_notif_after == msg_notif_before,
          f"unread={j.get('data')} MESSAGE_RECEIVED before={msg_notif_before} after={msg_notif_after}")

    for s_ in (a, b2):
        s_.close()
    return finish()


def finish():
    print()
    print(f"RESULT: pass={PASS} fail={FAIL}")
    return 0 if FAIL == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
