package com.sky.controller.admin;

import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersRejectionDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController("adminOrderController")
@RequestMapping("/admin/order")
@Slf4j
@Api(tags = "订单管理")
public class OrderController {

    @Autowired
    private OrderService orderService;

    /**
     * admin端订单列表条件查询
     */
    @GetMapping("/conditionSearch")
    @ApiOperation("订单列表条件查询")
    public Result<PageResult> conditionSearch(OrdersPageQueryDTO ordersPageQuery){
        log.info("订单列表条件查询：{}", ordersPageQuery);
        PageResult pageResult = orderService.conditionSearch(ordersPageQuery);
        return Result.success(pageResult);
    }

    /**
     * 查询各个状态订单的数量
     */
    @GetMapping("/statistics")
    @ApiOperation("查询各个状态订单的数量")
    public Result<OrderStatisticsVO> statistics(){
        log.info("查询各个状态订单的数量");
        OrderStatisticsVO orderStatisticsVO = orderService.statistics();
        return Result.success(orderStatisticsVO);
    }

    /**
     * 查询订单详情
     */
    @GetMapping("/details/{id}")
    @ApiOperation("查询订单详情")
    public Result<OrderVO> details(@ApiParam("订单id") @PathVariable Long id){
        log.info("查询订单详情：{}", id);
        OrderVO orderVO = orderService.orderDetail(id);
        return Result.success(orderVO);
    }

    /**
     * 商家接单
     */
    @PutMapping("/confirm")
    @ApiOperation("商家接单")
    public Result confirm(@RequestBody OrdersConfirmDTO ordersConfirmDTO) {
        log.info("商家接单：{}", ordersConfirmDTO);
        orderService.confirm(ordersConfirmDTO);
        return Result.success();
    }

    /**
     * 商家拒单
     */
    @PutMapping("/rejection")
    @ApiOperation("商家拒单")
    public Result rejection(@RequestBody OrdersRejectionDTO rejectionDTO) {
        log.info("商家拒单：{}", rejectionDTO);
        orderService.rejection(rejectionDTO);
        return Result.success();
    }
}
