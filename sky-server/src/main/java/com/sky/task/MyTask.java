package com.sky.task;

import com.sky.WebSocket.WebSocketServer;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@Slf4j
public class MyTask {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private WebSocketServer webSocketServer;

    /**
     * 测试websocket
     */
//    @Scheduled(cron = "0/5 * * * * ?")
//    public void testWebSocket() {
//        // 发送消息和当前时间
//        webSocketServer.sendToAllClient("测试websocket" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
//    }

    /**
     * 处理超时未支付订单, 每30秒执行一次
     */
    @Scheduled(cron = "0 * * * * ?")
    public void processTimeOutNotPayOrder() {
        log.info("处理超时未支付订单");
        // 1. 查询状态为待支付订单, 并且下单时间超过当前时间-15分钟
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime time = now.minusMinutes(15);
        List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeLT(Orders.PENDING_PAYMENT, time);
        // 2. 判断结果List, 遍历, 修改订单状态, 更新到数据库
        if (ordersList != null && !ordersList.isEmpty()) {
            for (Orders orders : ordersList) {
                orders.setStatus(Orders.CANCELLED);
                orders.setCancelReason("用户支付超时");
                orders.setCancelTime(LocalDateTime.now());
                orderMapper.update(orders);
            }
        }
    }

    /**
     * 处理派送中状态的订单
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void processDeliveryOrder() {
        log.info("处理派送中状态的订单");
        // 1. 定义时间, 订单超过一个小时
        LocalDateTime time = LocalDateTime.now().minusHours(1);
        // 2. 查询对应的订单
        List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeLT(Orders.DELIVERY_IN_PROGRESS, time);
        // 3. 异常判断
        if (ordersList != null && !ordersList.isEmpty()) {
            for (Orders orders : ordersList) {
                orders.setStatus(Orders.COMPLETED);
                orders.setDeliveryTime(LocalDateTime.now());
                orderMapper.update(orders);
            }
        }
    }
}
