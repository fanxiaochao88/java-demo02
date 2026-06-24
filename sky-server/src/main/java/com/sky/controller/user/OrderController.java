package com.sky.controller.user;

import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController("userOrderController")
@RequestMapping("/user/order")
@Slf4j
@Api(tags = "C端-订单接口")
public class OrderController {

    @Autowired
    private OrderService orderService;

    /**
     * 用户下单
     */
    @RequestMapping("/submit")
    @ApiOperation("用户下单")
    public Result<OrderSubmitVO> submit(@RequestBody OrdersSubmitDTO ordersSubmitDTO) {
        log.info("用户下单：{}", ordersSubmitDTO);
        return Result.success(orderService.submit(ordersSubmitDTO));
    }

    /**
     * 用户支付, 暂时mock, 不使用微信支付
     */
    @RequestMapping("/payment")
    @ApiOperation("用户支付")
    public Result<OrderPaymentVO> payment(@RequestBody OrdersPaymentDTO ordersPaymentDTO) {
        log.info("用户支付：{}", ordersPaymentDTO);
        return Result.success(orderService.payment(ordersPaymentDTO));
    }

    /**
     * 分页查询订单列表
     */
    @RequestMapping("/historyOrders")
    @ApiOperation("分页查询订单列表")
    public Result<PageResult> historyOrders(OrdersPageQueryDTO ordersPageQueryDTO) {
        log.info("分页查询订单列表：{}", ordersPageQueryDTO);
        return Result.success(orderService.pageQuery(ordersPageQueryDTO));
    }

    /**
     * 订单详情
     */
    @GetMapping("/orderDetail/{id}")
    @ApiOperation("订单详情")
    public Result<OrderVO> orderDetail(@ApiParam("订单id") @PathVariable Long id) {
        log.info("订单详情：{}", id);
        return Result.success(orderService.orderDetail(id));
    }

    /**
     * 取消订单
     * 规则: 待付款1, 待接单2, 用户可以随时主动取消订单, 如果是2, 需要退款, 重置付款状态字段 如果超过2, 不能取消, 需要和商家进行电话沟通
     */
    @PutMapping("/cancel/{id}")
    @ApiOperation("取消订单")
    public Result cancel(@ApiParam("订单id") @PathVariable Long id) {
        log.info("取消订单：{}", id);
        orderService.cancel(id);
        return Result.success();
    }
}
