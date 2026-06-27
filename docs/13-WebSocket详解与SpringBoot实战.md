# 13 - WebSocket 详解与 Spring Boot 实战

> 作为前端你可能已经用过 `new WebSocket('ws://...')`，但后端这块是怎么运作的？本文从零讲清楚。

---

## 目录

1. [先导问题：你的困惑解答](#1-先导问题你的困惑解答)
2. [WebSocket 是什么](#2-websocket-是什么)
3. [Tomcat 如何处理 WebSocket](#3-tomcat-如何处理-websocket)
4. [Spring Boot 中 WebSocket 的三个角色](#4-spring-boot-中-websocket-的三个角色)
   - [4.1 深入理解"多例"：每个连接一个新实例](#41-深入理解多例每个连接一个新实例)
5. [代码逐行解读](#5-代码逐行解读)
6. [完整实战：来单提醒功能](#6-完整实说来单提醒功能)
7. [前端如何对接](#7-前端如何对接)
8. [常见坑与最佳实践](#8-常见坑与最佳实践)

---

## 1. 先导问题：你的困惑解答

### Q1: WebSocketServer 是一个"工具类"吗？

**不是。**它更像是一个**特殊的 Controller**。

```
HTTP 模式:  请求 → DispatcherServlet → 匹配 @RequestMapping → Controller方法
WebSocket模式: 握手 → Tomcat WebSocket引擎 → 匹配 @ServerEndpoint → WebSocketServer方法
```

区别在于：HTTP Controller 是**单例**的（一个实例处理所有请求），而 WebSocketServer 是**多例**的（每个连接创建一个新实例），这也是为什么 `sessionMap` 必须是 `static`。

### Q2: 也是 Tomcat 监听到吗？

**是的，但走的路径不同。**

```
                     ┌──────────── Tomcat ──────────────┐
                     │                                   │
   HTTP 请求  ──────>│  HTTP Connector (端口8080)         │
                     │    → DispatcherServlet            │
                     │    → Interceptor Chain            │
                     │    → @Controller                  │
                     │                                   │
   WebSocket 握手 ──>│  HTTP Connector (同一个8080端口!)   │
   (也是HTTP请求)    │    → 检测到 Upgrade: websocket 头   │
                     │    → 不走 DispatcherServlet        │
                     │    → WebSocket 协议升级处理器       │
                     │    → 匹配 @ServerEndpoint          │
                     │    → 创建新的 WebSocketServer 实例  │
                     │    → 协议升级为 WebSocket           │
                     │                                   │
                     └───────────────────────────────────┘
```

关键：WebSocket 和 HTTP **共用同一个端口**（都是 8080），Tomcat 在 HTTP 连接器层面就分流了。

### Q3: 监听到就创建一个映射会话？

**分两步：**

```
第一步：协议升级（一次性的 HTTP 握手）
  客户端 → 服务器: GET /ws/user123 HTTP/1.1
                    Upgrade: websocket
                    Connection: Upgrade
  服务器 → 客户端: HTTP/1.1 101 Switching Protocols
                    Upgrade: websocket

第二步：会话建立（Tomcat 内部）
  1. 根据路径 /ws/{sid} 找到 @ServerEndpoint("/ws/{sid}")
  2. 实例化一个 WebSocketServer 对象
  3. 把 TCP 连接包装成 Session 对象
  4. 调用 @OnOpen 方法，把 session 和 sid 传进去
  5. 你把 session 存入 sessionMap，以后就能通过它发消息
```

---

## 2. WebSocket 是什么

### 一句话

**WebSocket 是在单个 TCP 连接上进行全双工通信的协议。**

### 对比 HTTP

| 特性 | HTTP | WebSocket |
|------|------|-----------|
| 通信模式 | 请求-响应（半双工） | 全双工 |
| 谁先说话 | 必须客户端先请求 | 双方随时发 |
| 连接生命周期 | 一次请求/响应就关闭 | 持久连接 |
| 头部开销 | 每次请求带完整 HTTP 头 | 帧头仅 2-14 字节 |
| 协议标识 | `http://` 或 `https://` | `ws://` 或 `wss://` |
| 典型场景 | 查数据、提交表单 | 实时推送、聊天、通知 |

### 一个直观的比喻

```
HTTP  = 写信
  你写一封信寄出去 → 对方回一封信 → 结束
  每次都要装信封、贴邮票、写地址

WebSocket = 打电话
  拨号接通 → 双方随时说话 → 挂断才结束
  接通后就不需要每次拨号了
```

---

## 3. Tomcat 如何处理 WebSocket

### 3.1 架构全景

```
┌──────────────────────────────────────────────────────────┐
│                        Tomcat                             │
│                                                           │
│  ┌─────────────────────┐    ┌──────────────────────────┐ │
│  │   HTTP Connector    │    │   WebSocket Container     │ │
│  │   (端口8080)        │    │                           │ │
│  │                     │    │  WebSocketEngine          │ │
│  │   ┌───────────────┐ │    │    │                      │ │
│  │   │ 协议检测      │ │    │    ├─ /ws/{sid} ──> WebSocketServer │
│  │   │               │ │    │    ├─ /chat     ──> ChatEndpoint   │
│  │   │ HTTP请求? ────┼─┼────┼────┤    ├─ /notify   ──> NotifyEndpoint│
│  │   │   → Servlet容器 │    │    │                      │ │
│  │   │               │ │    │    └─ 每个Endpoint创建   │ │
│  │   │ WebSocket升级? │ │    │       多实例(每连接一个)  │ │
│  │   │   → WebSocket  │ │    │                           │ │
│  │   │     容器       │ │    │                           │ │
│  │   └───────────────┘ │    │                           │ │
│  └─────────────────────┘    └──────────────────────────┘ │
└──────────────────────────────────────────────────────────┘
```

### 3.2 判断逻辑（在 Tomcat 源码层面）

```
收到一个 HTTP 请求:
  if (请求头包含 "Upgrade: websocket") {
      查找 WebSocket Container 中匹配的 Endpoint;
      执行协议升级;
      后续这个 TCP 连接上的数据都走 WebSocket 帧协议;
  } else {
      走正常的 Servlet → Filter Chain → DispatcherServlet → Controller;
  }
```

### 3.3 关键角色：ServerEndpointExporter

```java
@Configuration
public class WebSocketConfiguration {

    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
}
```

**这个 Bean 做了什么？**

Spring Boot 启动时：
1. `ServerEndpointExporter` 扫描 classpath 中所有带 `@ServerEndpoint` 注解的类
2. 把每个 `@ServerEndpoint` 注册到 Tomcat 的 WebSocket Container 中
3. 相当于告诉 Tomcat："如果收到 `/ws/{sid}` 的 WebSocket 握手请求，用 `WebSocketServer` 类来处理"

**如果没有这个 Bean，`@ServerEndpoint` 不会生效！**

> 注意：如果你用的是 Spring Boot 内嵌的 Tomcat，需要这个 Bean。但如果你用的是外部独立部署的 Tomcat（打 war 包），则不需要，因为外部 Tomcat 会自动扫描。

---

## 4. Spring Boot 中 WebSocket 的三个角色

```
角色1: 端点类 (@ServerEndpoint)
  ├─ 类似 HTTP 中的 Controller
  ├─ 处理连接生命周期: @OnOpen / @OnClose / @OnError
  ├─ 处理消息: @OnMessage
  └─ 特点: 多例！每个连接一个新实例

角色2: 配置类 (@Configuration)
  ├─ 提供 ServerEndpointExporter Bean
  └─ 把 @ServerEndpoint 注册到 Tomcat WebSocket 容器

角色3: 业务类（你项目中的 Service）
  ├─ 注入 WebSocketServer（但 WebSocketServer 是多例无法直接注入！）
  └─ 通过 static 方法或消息队列来触发推送
```

---

### 4.1 深入理解"多例"：每个连接一个新实例

这是理解 WebSocket 服务端最核心的概念。用前端的话说：

#### 一次 `new WebSocket()` = 一个连接 = 服务端一个新实例

```
前端                                     后端 (Tomcat)

// 页面A中
const ws1 = new WebSocket("/ws/123")
  ─── 第一次握手，走TCP连接#1 ───>  创建 WebSocketServer 实例 #1
                                    @OnOpen(session_A, "123")
                                    sessionMap.put("123", session_A)

// 页面B中（或者同一个页面的另一个模块）
const ws2 = new WebSocket("/ws/456")
  ─── 第二次握手，走TCP连接#2 ───>  创建 WebSocketServer 实例 #2 (全新的!)
                                    @OnOpen(session_B, "456")
                                    sessionMap.put("456", session_B)
```

**规则很简单：前端调几次 `new WebSocket()`，后端就创建几个实例。**

- 同一个浏览器两个页面分别连接 → 2 个实例
- 同一个页面写了两段 `new WebSocket()` → 2 个实例
- 两个不同浏览器分别连接 → 2 个实例
- 只要 TCP 连接不同，就是不同的实例

#### 为什么必须是多例？

因为 Tomcat 的设计哲学是：**一个连接绑定一个 Endpoint 实例**。

每个连接有不同的 Session、不同的 sid、不同的生命周期。Tomcat 无法用一个实例去管理多个连接的状态。

> 这和 HTTP Controller 完全不同。Controller 是 Spring 管理的单例 Bean，所有 HTTP 请求都由同一个 Controller 实例处理（Controller 不持有请求状态，状态在方法参数里）。

#### 为什么 sessionMap 必须是 static

**这才是整个设计的关键！** 看看不用 static 会怎样：

```
不加 static (错误):

实例 #1                       实例 #2
  sessionMap = {}                sessionMap = {}
  @OnOpen → put("123", A)       @OnOpen → put("456", B)
  sessionMap = {                 sessionMap = {
    "123": A                       "456": B
  }                             }

  如果从实例 #1 群发 → 只能发给 123！456 在另一个 Map 里，根本看不到！
  实例 #2 群发 → 只能发给 456！

  两个实例互相不知道对方的存在，sessionMap 等于废了。
```

```
加 static (正确):

static sessionMap (所有实例共享同一份)

实例 #1                       实例 #2
  @OnOpen                       @OnOpen
  → staticMap.put("123", A)    → staticMap.put("456", B)

static sessionMap = {
  "123": A,      ← 实例 #1 写入
  "456": B       ← 实例 #2 写入
}

无论从哪个实例调用 sendToAllClient()：
  → 遍历 static sessionMap
  → 给 "123" 和 "456" 都发消息
  → 两人都能收到！
```

#### 同一个 sid 被连接两次怎么办

```javascript
// 同一个页面
const ws1 = new WebSocket("ws://localhost:8080/ws/user001");  // 连接1
const ws2 = new WebSocket("ws://localhost:8080/ws/user001");  // 连接2 (sid相同!)
```

```
后端:

连接1 → 实例 #1: sessionMap.put("user001", session_1)
连接2 → 实例 #2: sessionMap.put("user001", session_2)
// sessionMap 里 key 是 "user001"，value 被覆盖成 session_2！
// 连接1 的会话丢失了，再也无法向连接1推送消息！
```

**解决方案**（二选一）：

```java
// 方案A: 一个sid支持多个session（适合多端同时在线）
private static Map<String, List<Session>> sessionMap = new ConcurrentHashMap<>();

@OnOpen
public void onOpen(Session session, @PathParam("sid") String sid) {
    sessionMap.computeIfAbsent(sid, k -> Collections.synchronizedList(new ArrayList<>()))
              .add(session);
}

// 方案B: 新连接挤掉旧连接（适合单设备登录）
@OnOpen
public void onOpen(Session session, @PathParam("sid") String sid) {
    Session oldSession = sessionMap.get(sid);
    if (oldSession != null && oldSession.isOpen()) {
        try { oldSession.close(); } catch (Exception e) { }
    }
    sessionMap.put(sid, session);
}
```

#### 多例带来的另一个问题：无法直接 @Autowired

Service 层想调用 WebSocketServer 推送消息，但它是多例的：

```java
// ❌ 这会注入 Spring 容器中唯一的模板实例，不是真正处理连接的实例
@Autowired
private WebSocketServer webSocketServer;
```

解决方案详见 [第 8 节 → 坑1](#坑1websocketserver-无法被-autowired-注入)。

---

## 5. 代码逐行解读

### 5.1 WebSocketServer（端点类）

```java
package com.sky.websocket;

import org.springframework.stereotype.Component;
import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Component                // ① 交给Spring管理（主要是为了让Spring能扫描到它）
@ServerEndpoint("/ws/{sid}")  // ② 声明这是一个WebSocket端点，路径支持路径参数
public class WebSocketServer {

    // ③ 必须有 static！因为WebSocketServer是多例的（每个连接创建一个实例）
    //    如果不用static，每个连接看到的都是自己的空Map
    private static Map<String, Session> sessionMap = new HashMap();
    //                      ↑
    //           Session = 一个WebSocket连接
    //           你可以通过它向客户端发送消息

    /**
     * ④ 连接建立时触发
     * @param session Tomcat传进来的连接会话（每个连接唯一）
     * @param sid     路径参数 /ws/{sid} 中的 sid 值
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("sid") String sid) {
        System.out.println("客户端：" + sid + "建立连接");
        sessionMap.put(sid, session);   // ⑤ 保存会话，以后可以主动推消息
    }

    /**
     * ⑥ 收到客户端消息时触发
     * @param message 客户端发来的文本
     * @param sid     路径参数
     */
    @OnMessage
    public void onMessage(String message, @PathParam("sid") String sid) {
        System.out.println("收到来自客户端：" + sid + "的信息:" + message);
        // ⑦ 这里可以处理客户端发来的指令，比如心跳、ack确认等
    }

    /**
     * ⑧ 连接关闭时触发（客户端主动关闭、网络断开、超时等）
     */
    @OnClose
    public void onClose(@PathParam("sid") String sid) {
        System.out.println("连接断开:" + sid);
        sessionMap.remove(sid);   // ⑨ 及时清理，防止内存泄漏
    }

    /**
     * ⑩ 群发消息 － 业务层调用这个方法向所有在线客户端推送
     */
    public void sendToAllClient(String message) {
        Collection<Session> sessions = sessionMap.values();
        for (Session session : sessions) {
            try {
                // ⑪ 向客户端发送文本消息（服务端主动推送的核心API）
                session.getBasicRemote().sendText(message);
            } catch (Exception e) {
                e.printStackTrace();  // 比如客户端已断开但还没触发onClose
            }
        }
    }
}
```

### 5.2 单发 vs 群发（扩展代码）

```java
// 根据 sid 给指定用户发消息（单发）
public void sendToOneClient(String sid, String message) {
    Session session = sessionMap.get(sid);
    if (session != null && session.isOpen()) {
        try {
            session.getBasicRemote().sendText(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

### 5.3 WebSocketConfiguration（配置类）

```java
package com.sky.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

@Configuration
public class WebSocketConfiguration {

    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        // 这个Bean的作用是：
        // 1. 扫描所有@ServerEndpoint注解的类
        // 2. 注册到Tomcat的WebSocket容器
        // 3. 只有这样，WebSocket握手请求才会被正确路由
        return new ServerEndpointExporter();
    }
}
```

---

## 6. 完整实战：来单提醒功能

这是外卖项目中非常典型的 WebSocket 应用场景：用户下单后，商家后台实时收到新订单提醒。

### 6.1 业务流程

```
用户下单(order表 insert) 
  → OrderService.paySuccess(orderId) 
  → 需要通知商家端："有新订单！"
  → 调用 WebSocketServer.sendToAllClient("NEW_ORDER:订单号123")
  → 所有连接到 /ws/{sid} 的商家后台页面收到消息
  → 前端弹窗/播放提示音
```

### 6.2 完整后端代码

#### 文件1: `WebSocketServer.java`

```java
package com.sky.websocket;

import org.springframework.stereotype.Component;
import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ServerEndpoint("/ws/{sid}")
public class WebSocketServer {

    // 用 ConcurrentHashMap 代替 HashMap (线程安全！)
    private static Map<String, Session> sessionMap = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("sid") String sid) {
        System.out.println("WebSocket连接建立: " + sid);
        sessionMap.put(sid, session);
    }

    @OnMessage
    public void onMessage(String message, @PathParam("sid") String sid) {
        System.out.println("收到消息 from " + sid + ": " + message);
    }

    @OnClose
    public void onClose(@PathParam("sid") String sid) {
        System.out.println("WebSocket连接断开: " + sid);
        sessionMap.remove(sid);
    }

    @OnError
    public void onError(Session session, Throwable error) {
        System.err.println("WebSocket发生错误: " + error.getMessage());
        error.printStackTrace();
    }

    // ============ 以下是业务方法 ============

    /**
     * 群发给所有在线客户端
     */
    public void sendToAllClient(String message) {
        for (Map.Entry<String, Session> entry : sessionMap.entrySet()) {
            try {
                Session session = entry.getValue();
                if (session.isOpen()) {
                    session.getBasicRemote().sendText(message);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 发给指定用户
     */
    public void sendToClient(String sid, String message) {
        Session session = sessionMap.get(sid);
        if (session != null && session.isOpen()) {
            try {
                session.getBasicRemote().sendText(message);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
```

#### 文件2: `WebSocketConfiguration.java`

```java
package com.sky.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

@Configuration
public class WebSocketConfiguration {

    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
}
```

#### 文件3: 在 Service 中调用推送

```java
// 在 OrderServiceImpl 中

@Autowired
private WebSocketServer webSocketServer;  // 要小心！见下方 "常见坑"

// 支付成功后推送消息
public void paySuccess(String orderNumber) {
    // ... 更新数据库状态 ...

    // 向所有商家端推送新订单提醒
    String message = "{\"type\":\"NEW_ORDER\", \"orderNumber\":\"" + orderNumber + "\"}";
    webSocketServer.sendToAllClient(message);
}
```

### 6.3 关键时序图

```
 用户手机端                 后端                          商家PC端
     │                       │                              │
     │                       │      ws握手连接              │
     │                       │ <══════════════════════════ │
     │                       │     /ws/shop_admin_001      │
     │                       │                              │
     │  下单 POST /order     │                              │
     │ ─────────────────────>│                              │
     │                       │  订单入库                    │
     │                       │                              │
     │                       │  sendToAllClient(            │
     │                       │    "NEW_ORDER:xxxxx")       │
     │                       │ ────────────────────────────>│
     │                       │                              │  弹窗"新订单！"
     │  返回"下单成功"       │                              │
     │ <─────────────────────│                              │
```

---

## 7. 前端如何对接

### 7.1 建立连接

```javascript
// 商家后台页面
let socket = null;
let reconnectTimer = null;

function connectWebSocket() {
    const sid = 'shop_admin_' + getCurrentUserId();  // 业务标识
    socket = new WebSocket('ws://localhost:8080/ws/' + sid);

    socket.onopen = function() {
        console.log('WebSocket 连接已建立');
        clearTimeout(reconnectTimer);
    };

    socket.onmessage = function(event) {
        const data = JSON.parse(event.data);
        if (data.type === 'NEW_ORDER') {
            // 弹出新订单提醒
            showNotification('新订单：' + data.orderNumber);
            playNotificationSound();
        }
    };

    socket.onclose = function() {
        console.log('WebSocket 连接已断开，3秒后重连');
        reconnectTimer = setTimeout(connectWebSocket, 3000);
    };

    socket.onerror = function(error) {
        console.error('WebSocket 错误:', error);
        socket.close();
    };
}

// 页面加载时连接
connectWebSocket();

// 页面关闭时主动断开（避免服务端报错）
window.addEventListener('beforeunload', function() {
    if (socket) {
        socket.close();
    }
});
```

### 7.2 心跳保活

```javascript
// 每30秒发一次心跳
const heartbeatInterval = setInterval(function() {
    if (socket && socket.readyState === WebSocket.OPEN) {
        socket.send('PING');
    }
}, 30000);
```

---

## 8. 常见坑与最佳实践

### 坑1：@Autowired 注入 WebSocketServer 到底行不行？

**这个问题新手经常困惑。答案是：能注入，但注入的是 Spring 单例，不是处理连接的实例。**

#### 先说结论

```java
// 教程里这样写：
@Autowired
private WebSocketServer webSocketServer;  // ← 注入的是Spring容器中的单例

// 然后调用：
webSocketServer.sendToAllClient("消息");  // ← 居然能正常工作！
```

**能工作的原因：** `sendToAllClient()` 内部访问的是 `static sessionMap`。`static` 字段被所有实例共享——Spring 单例、Tomcat 创建的连接实例，看到的都是同一份 `static sessionMap`。

```
┌──────────────────────────────────────────┐
│          static sessionMap               │
│  {"123": session_A, "456": session_B}   │
│           ↑              ↑               │
│           │              │               │
│  Tomcat实例#1写入   Tomcat实例#2写入     │
│                                          │
│  Spring单例(@Autowired的) 读取 → 能拿到所有Session ✅ │
└──────────────────────────────────────────┘
```

#### 什么时候会出问题？

一旦你在类里加了**非 static 的实例字段**，@Autowired 的实例就拿不到正确值：

```java
@Component
@ServerEndpoint("/ws/{sid}")
public class WebSocketServer {
    private static Map<String, Session> sessionMap = new ConcurrentHashMap<>();
    
    private String currentSid;  // ❌ 非static实例字段

    @OnOpen
    public void onOpen(Session session, @PathParam("sid") String sid) {
        this.currentSid = sid;  // Tomcat实例上有值，Spring单例上永远是null
        sessionMap.put(sid, session);
    }

    public void doSomething() {
        System.out.println(this.currentSid);  
        // @Autowired注入的Spring单例调用 → 永远打印null
    }
}
```

#### 所以到底怎么做？

```java
// 方案A：直接用 @Autowired（教程同款，适合简单场景）
// 前提：所有需要跨实例访问的数据都是 static 的
@Component
@ServerEndpoint("/ws/{sid}")
public class WebSocketServer {
    private static Map<String, Session> sessionMap = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("sid") String sid) {
        sessionMap.put(sid, session);
    }

    public void sendToAllClient(String message) {
        sessionMap.values().forEach(s -> { /* ... */ });
    }
}

// 任何地方直接 @Autowired 注入：
// @Autowired private WebSocketServer ws;
// ws.sendToAllClient("hello");  ✅ 可以

// ============================================================

// 方案B：完全用 static 方法（推荐，最清晰）
@Component
@ServerEndpoint("/ws/{sid}")
public class WebSocketServer {
    private static Map<String, Session> sessionMap = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("sid") String sid) {
        sessionMap.put(sid, session);
    }

    // static 方法，不需要注入任何实例
    public static void pushToAll(String message) {
        for (Session s : sessionMap.values()) {
            if (s.isOpen()) {
                try { s.getBasicRemote().sendText(message); }
                catch (Exception e) { e.printStackTrace(); }
            }
        }
    }
}

// 调用方（不需要 @Autowired！）：
// WebSocketServer.pushToAll("新订单！");

// ============================================================

// 方案C：用独立的 SessionManager 分离职责（生产环境推荐）
@Component
public class WebSocketSessionManager {
    private static final Map<String, Session> sessionMap = new ConcurrentHashMap<>();

    public static void add(String sid, Session s) { sessionMap.put(sid, s); }
    public static void remove(String sid) { sessionMap.remove(sid); }
    
    public static void sendToAll(String message) {
        sessionMap.forEach((sid, s) -> {
            if (s.isOpen()) try { s.getBasicRemote().sendText(message); }
            catch (Exception e) { }
        });
    }
}

// WebSocketServer 只负责注解声明
@OnOpen
public void onOpen(Session session, @PathParam("sid") String sid) {
    WebSocketSessionManager.add(sid, session);
}
```

### 坑2：线程安全

```java
// ❌ 错误
private static Map<String, Session> sessionMap = new HashMap();
// HashMap不是线程安全的，多线程同时put/remove/遍历会出问题

// ✅ 正确
private static Map<String, Session> sessionMap = new ConcurrentHashMap<>();
// ConcurrentHashMap支持并发读写
```

### 坑3：遍历时删除

```java
// ❌ 错误
public void sendToAllClient(String message) {
    for (Session session : sessionMap.values()) {
        if (!session.isOpen()) {
            // 不要在遍历HashMap时直接remove！
            sessionMap.remove(...);  // ConcurrentModificationException
        }
    }
}

// ✅ 正确：只发消息，清理交给onClose
public void sendToAllClient(String message) {
    for (Map.Entry<String, Session> entry : sessionMap.entrySet()) {
        try {
            Session session = entry.getValue();
            if (session.isOpen()) {
                session.getBasicRemote().sendText(message);
            }
        } catch (Exception e) {
            // 发送失败说明连接已死，但要等onClose自然清理
            e.printStackTrace();
        }
    }
}
```

### 坑4：session 数量不断增加

```java
// 如果没有正确清理，sessionMap会越来越大
// 每次连接都会 add，但如果 @OnClose 没有触发（比如网络异常断开），
// 这个session就永远留在Map里 → 内存泄漏

// ✅ 定时清理死连接
@Scheduled(fixedRate = 60000)  // 每分钟执行一次
public void cleanDeadSessions() {
    sessionMap.entrySet().removeIf(entry -> !entry.getValue().isOpen());
    System.out.println("当前在线连接数: " + sessionMap.size());
}
```

### 坑5：sendText 的线程安全

```java
// Session.sendText() 不是线程安全的！
// 如果多个线程同时向同一个Session发消息，可能造成消息乱序

// ✅ 使用同步发送
public void sendToClient(String sid, String message) {
    Session session = sessionMap.get(sid);
    if (session != null && session.isOpen()) {
        synchronized (session) {  // 对session加锁
            session.getBasicRemote().sendText(message);
        }
    }
}
```

### 最佳实践清单

| 项目 | 建议 |
|------|------|
| 存储容器 | `ConcurrentHashMap` 而不是 `HashMap` |
| sessionMap | 必须 `static` |
| 清理机制 | 定时清理 `!isOpen()` 的死 session |
| 发送方式 | 对 session 加 `synchronized` |
| 消息格式 | 使用 JSON，统一 `{"type":"...", "data":{...}}` 结构 |
| 生产环境 | 考虑用 Spring WebSocket + STOMP 替代 JSR-356 原生 API |
| 大规模场景 | 考虑用 Netty 或专门的推送服务 |

---

## 总结

```
┌─────────────────────────────────────────────────────────┐
│                    核心要记住的 5 点                       │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  1. WebSocket 和 HTTP 共用端口，Tomcat 在连接器层分流     │
│                                                          │
│  2. @ServerEndpoint 是特殊的"Controller"，但是多例的      │
│                                                          │
│  3. sessionMap 必须用 static，因为每个连接是不同实例      │
│                                                          │
│  4. ServerEndpointExporter 是必需的注册器                 │
│                                                          │
│  5. 用 ConcurrentHashMap + 定时清理 + 同步发送           │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

> 下一章预告：深入 Spring WebSocket + STOMP 协议，实现更优雅的点对点消息推送。
