package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.WebSocket.WebSocketServer;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.AddressBook;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.ShoppingCart;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.AddressBookMapper;
import com.sky.mapper.OrderDetailsMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private AddressBookMapper addressBookMapper;

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderDetailsMapper orderDetailsMapper;

    /**
     * 导入websocket模块
     */
    @Autowired
    private WebSocketServer webSocketServer;

    /**
     * 用户下单
     * @param ordersSubmitDTO
     * @return
     */
    @Override
    public OrderSubmitVO submit(OrdersSubmitDTO ordersSubmitDTO) {
        // 1. 处理异常情况, 暂时处理购物车为空, 收货地址为空, 后期补充配送范围超出限制
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        // 2. 获取并且判断购物车是否为空
        Long userId = BaseContext.getCurrentId();
        ShoppingCart ShoppingCart = com.sky.entity.ShoppingCart.builder()
                .userId(userId)
                .build();
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(ShoppingCart);
        if (shoppingCartList == null || shoppingCartList.size() == 0) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }
        // 3. 构造订单数据
        Orders order = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, order);
        order.setPhone(addressBook.getPhone());
        order.setAddress(addressBook.getDetail());
        order.setConsignee(addressBook.getConsignee());
        order.setNumber(String.valueOf(System.currentTimeMillis()));
        order.setUserId(userId);
        order.setStatus(Orders.PENDING_PAYMENT);
        order.setPayStatus(Orders.UN_PAID);
        order.setOrderTime(LocalDateTime.now());

        // 4. 插入订单数据, 需要主键返回
        orderMapper.insert(order);
        // 5. 插入订单明细数据
        List<OrderDetail> orderDetailList = new ArrayList<>();
        for ( ShoppingCart shoppingCart: shoppingCartList) {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(shoppingCart, orderDetail);
            orderDetail.setOrderId(order.getId());
            orderDetailList.add(orderDetail);
        }
        orderDetailsMapper.insertBatch(orderDetailList);
        // 6. 清空购物车
        shoppingCartMapper.clear(userId);
        // 7. 构造VO对象并且返回
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(order.getId())
                .orderNumber(order.getNumber())
                .orderAmount(order.getAmount())
                .orderTime(order.getOrderTime())
                .build();
        return orderSubmitVO;
    }

    /**
     * 用户支付, 本来需要调用微信api获取预支付, 传递前段进行支付, 收到微信官方回调之后, 切换订单状态. 但是我这里默认成功, 直接切换订单状态
     * @param ordersPaymentDTO
     * @return
     */
    @Override
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) {
        String orderNumber = ordersPaymentDTO.getOrderNumber();
        // 默认支付成功
        Orders orders = Orders.builder()
                        .status(Orders.TO_BE_CONFIRMED)
                        .payStatus(Orders.PAID)
                        .checkoutTime(LocalDateTime.now())
                        .number(orderNumber)
                        .build();
        orderMapper.updateByNumber(orders);
        HashMap<Object, Object> map = new HashMap<>();
        map.put("type", 1); // 1 是新订单提醒    2  用户催单
        map.put("orderId", "111");
        map.put("content", orders.getNumber());
        webSocketServer.sendToAllClient(JSON.toJSONString(map));
        return OrderPaymentVO.builder().build();
    }

    /**
     * 订单列表
     * @param ordersPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(OrdersPageQueryDTO ordersPageQueryDTO) {
        // 1. 进行分页查询
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        ordersPageQueryDTO.setStatus(ordersPageQueryDTO.getStatus());
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);
        List<OrderVO> orderVOS = new ArrayList<>();
        // 2. 根据分页结果遍历, 获取订单详情列表
        if (page != null && !page.isEmpty()) {
            for ( Orders orders : page) {
                List<OrderDetail> orderDetailList = orderDetailsMapper.list(orders.getId());
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                orderVO.setOrderDetailList(orderDetailList);
                orderVOS.add(orderVO);
            }
        }
        // 3. 组装VO列表并返回
        return new PageResult(page.getTotal(), orderVOS);
    }

    /**
     * 订单详情
     * @param id
     * @return
     */
    @Override
    public OrderVO orderDetail(Long id) {
        // 查询订单信息
        Orders orders = orderMapper.getById(id);
        // 查询订单菜品详细信息
        List<OrderDetail> orderDetailList = orderDetailsMapper.list(id);
        // 组装VO
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        orderVO.setOrderDetailList(orderDetailList);
        return orderVO;
    }

    @Override
    public void cancel(Long id) {
        // 1. 查询出来订单, 判断异常
        Orders orders = orderMapper.getById(id);
        Orders newOrders = new Orders();
        // 2. 判断订单状态. >2不能取消
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (orders.getStatus() > 2) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        // 3. 如果是2, 进行退款, 并且将付款状态变成退款
        if (orders.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            // 假设微信退款成功
            newOrders.setPayStatus(Orders.REFUND);
        }
        // 4. 更新订单数据项
        newOrders.setId(id);
        newOrders.setNumber(orders.getNumber());
        newOrders.setStatus(Orders.CANCELLED);
        newOrders.setCancelReason("用户取消");
        newOrders.setCancelTime(LocalDateTime.now());
        // 5. 更新数据库最新订单
        orderMapper.updateByNumber(newOrders);
    }

    /**
     * 再来一单
     * @param id
     */
    @Override
    public void repetition(Long id) {
        // 1. 查询当前订单详情列表
        List<OrderDetail> orderDetailList = orderDetailsMapper.list(id);
        // 2. 构造购物车列表
        List<ShoppingCart> shoppingCartList = orderDetailList.stream().map(x -> {
            ShoppingCart shoppingCart = new ShoppingCart();
            BeanUtils.copyProperties(x, shoppingCart, "id");
            shoppingCart.setUserId(BaseContext.getCurrentId());
            shoppingCart.setCreateTime(LocalDateTime.now());
            return shoppingCart;
        }).collect(Collectors.toList());
        // 3. 插入购物车

        shoppingCartMapper.insertBatch(shoppingCartList);
    }

    /**
     * admin端条件搜索订单列表
     * @param ordersPageQuery
     * @return
     */
    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQuery) {
        // 1. 进行分页查询
        PageHelper.startPage(ordersPageQuery.getPage(), ordersPageQuery.getPageSize());
        Page<Orders> page = orderMapper.pageQuery(ordersPageQuery);
        // 2. 对订单过滤, 部分订单需要展示菜单详情
        List<OrderVO> orderVOS = getOrderVoList(page);
        return new PageResult(page.getTotal(), orderVOS);
    }

    /**
     * 统计订单数据
     * @return
     */
    @Override
    public OrderStatisticsVO statistics() {
        List<Orders> ordersList = orderMapper.list();
        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO(0, 0, 0);
        if (ordersList != null && !ordersList.isEmpty()) {
            for (Orders orders : ordersList) {
                if (orders.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
                    orderStatisticsVO.setToBeConfirmed(orderStatisticsVO.getToBeConfirmed() + 1);
                } else if (orders.getStatus().equals(Orders.CONFIRMED)) {
                    orderStatisticsVO.setConfirmed(orderStatisticsVO.getConfirmed() + 1);
                } else if (orders.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)){
                    orderStatisticsVO.setDeliveryInProgress(orderStatisticsVO.getDeliveryInProgress() + 1);
                }
            }
        }
        return orderStatisticsVO;
    }

    /**
     * 商家接单
     * @param ordersConfirmDTO
     */
    @Override
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        // 查询出来订单
        Orders orders = orderMapper.getById(ordersConfirmDTO.getId());
        // 判断订单状态是否是待接单
        if (orders.getStatus() != Orders.TO_BE_CONFIRMED) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        // 组装Orders
        Orders newOrders = new Orders();
        newOrders.setId(orders.getId());
        newOrders.setStatus(Orders.CONFIRMED);
        // 更新数据库订单
        orderMapper.update(newOrders);
    }

    /**
     * 商家拒单
     * @param rejectionDTO
     */
    @Override
    public void rejection(OrdersRejectionDTO rejectionDTO) {
        // 1. 查询订单
        Orders orders = orderMapper.getById(rejectionDTO.getId());
        // 2. 判断是否是待接单状态
        if (orders.getStatus() != Orders.TO_BE_CONFIRMED) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders newOrders = new Orders();
        newOrders.setId(orders.getId());
        // 3. 如果是待接单状态, 拒单, 更新订单状态字段
        newOrders.setStatus(Orders.CANCELLED);
        // 4. 退款, 更新付款状态, 调用微信接口进行退款
        newOrders.setPayStatus(Orders.REFUND);
        // 5. 更新拒单原因
        newOrders.setRejectionReason(rejectionDTO.getRejectionReason());
        // 6. 更新新的数据
        orderMapper.update(newOrders);
    }

    /**
     * 管理端取消订单
     * @param ordersCancelDTO
     */
    @Override
    public void adminCancel(OrdersCancelDTO ordersCancelDTO) {
        Orders newOrders = new Orders();
        // 1. 查询出来订单
        Orders orders = orderMapper.getById(ordersCancelDTO.getId());
        // 2. 判断异常(包含订单是否存在, 订单的status必须是待付款, 待派送, 派送中)
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (!orders.getStatus().equals(Orders.PENDING_PAYMENT) &&
                !orders.getStatus().equals(Orders.CONFIRMED) &&
                !orders.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        // 3. 查看付款状态, 如果已付款, 退款
        if (orders.getPayStatus().equals(Orders.PAID)) {
            newOrders.setPayStatus(Orders.REFUND);
        }
        // 4. 组装心得Orders(id, status, payStatus, cancelReason, cancelTime)
        newOrders.setId(orders.getId());
        newOrders.setStatus(Orders.CANCELLED);
        newOrders.setCancelReason(ordersCancelDTO.getCancelReason());
        newOrders.setCancelTime(LocalDateTime.now());
        // 5. 更新数据库
        orderMapper.update(newOrders);
    }

    /**
     * 商家派送订单
     * @param id
     */
    @Override
    public void delivery(Long id) {
        // 1. 查询订单
        Orders orders = orderMapper.getById(id);
        // 2. 判断异常(订单不存在, 状态不是待派送)
        if (orders == null || !orders.getStatus().equals(Orders.CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        // 3. 组装orders, 设置状态为派送中
        Orders newOrders = new Orders();
        newOrders.setId(orders.getId());
        newOrders.setStatus(Orders.DELIVERY_IN_PROGRESS);
        // 4. 更新数据库
        orderMapper.update(newOrders);
    }

    @Override
    public void complete(Long id) {
        // 1. 查询订单
        Orders orders = orderMapper.getById(id);
        // 2. 判断异常(订单不存在, 状态不是派送中)
        if (orders == null || !orders.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        // 3. 组装orders, 状态为完成
        Orders newOrders = new Orders();
        newOrders.setId(orders.getId());
        newOrders.setStatus(Orders.COMPLETED);
        newOrders.setDeliveryTime(LocalDateTime.now());
        // 4. 更新数据库
        orderMapper.update(newOrders);
    }

    /**
     * 催单
     * @param id
     */
    @Override
    public void reminder(Long id) {
        Orders orders = orderMapper.getById(id);
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        // 只有已接单和派送中可以催单
        if (!orders.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        HashMap<Object, Object> map = new HashMap<>();
        map.put("orderId", orders.getId());
        map.put("type", 2);
        map.put("content", orders.getNumber());
        webSocketServer.sendToAllClient(JSON.toJSONString(map));
    }

    /**
     * 根据订单列表, 组装VO列表, 每一项可能查询菜品详情信息
     * @param page
     * @return
     */
    private List<OrderVO> getOrderVoList(Page<Orders> page) {
        List<Orders> ordersList = page.getResult();
        List<OrderVO> orderVOS = new ArrayList<>();
        if (ordersList != null && ordersList.size() > 0) {
            for (Orders orders : ordersList) {
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                // 获取订单详情
                String orderDetailString = getOrderDetailString(orders);
                orderVO.setOrderDishes(orderDetailString);
                orderVOS.add(orderVO);
            }
        }
        return orderVOS;
    }

    /**
     * 根据订单查询订单详情, 然后将详情List转为字符串List, 再转化为字符串
     * @param orders
     * @return
     */
    private String getOrderDetailString(Orders orders) {
        List<OrderDetail> orderDetailList = orderDetailsMapper.list(orders.getId());
        List<String> orderDetailStringList = orderDetailList.stream().map(x -> {
            StringBuilder sb = new StringBuilder();
            sb.append(x.getName());
            sb.append(" x ");
            sb.append(x.getNumber());
            sb.append(" = ");
            sb.append(x.getAmount());
            return sb.toString();
        }).collect(Collectors.toList());

        return String.join(",", orderDetailStringList);
    }
}
