package com.sky.WebSocket;

import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.util.HashMap;
import java.util.Map;

@Component
@ServerEndpoint("/ws/{sid}")
public class WebSocketServer {
    // 存放会话对象
    private static Map<String, Session> sessionMap = new HashMap<>();

    /**
     * 建立连接
     * @param session
     * @param sid
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("sid") String sid) {
        System.out.println("与客户端" + sid + "建立连接");
        sessionMap.put(sid, session);
    }

    /**
     * 收到客户端消息
     */
    @OnMessage
    public void onMessage(String message, @PathParam("sid") String sid) {
        System.out.println("服务端收到客户端" + sid + "的消息：" + message);
    }

    /**
     * 断开连接
     */
    @OnClose
    public void onClose(@PathParam("sid") String sid) {
        System.out.println("与客户端" + sid + "断开连接");
        sessionMap.remove(sid);
    }

    /**
     * 群发
     */
    public void sendToAllClient(String message) {
        sessionMap.forEach((sid, session) -> {
            try {
                session.getBasicRemote().sendText(message);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * 群发指定用户
     */
    public void sendToClient(String message, String sid) {
        try {
            sessionMap.get(sid).getBasicRemote().sendText(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
