package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
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
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
            orders.setPayStatus(Orders.REFUND);
        }
        // 4. 更新订单数据项
        Orders newOrders = new Orders();
        newOrders.setId(id);
        newOrders.setNumber(orders.getNumber());
        newOrders.setStatus(Orders.CANCELLED);
        newOrders.setCancelReason("用户取消");
        newOrders.setCancelTime(LocalDateTime.now());
        // 5. 更新数据库最新订单
        orderMapper.updateByNumber(newOrders);
    }
}
