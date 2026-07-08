package com.sky.controller.admin;

import com.sky.result.Result;
import com.sky.service.WorkspaceService;
import com.sky.vo.*;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/admin/workspace")
@RestController
@Slf4j
@Api(tags = "工作台相关接口")
public class WorkspaceController {
    @Autowired
    private WorkspaceService workspaceService;
    /**
     * 今日数据总览(包含营业额,有效订单数, 完单率, 平均客单价, 新增用户数)
     */
    @GetMapping("/businessData")
    @ApiOperation("查询今日数据")
    public Result<BusinessDataVO> getBusinessData() {
        log.info("查询今日数据");
        return Result.success(workspaceService.getBusinessData(null,  null));
    }

    /**
     * 今日订单状态统计
     */
    @GetMapping("/overviewOrders")
    @ApiOperation("今日订单状态统计")
    public Result<OrderOverViewVO> getOrderStatistics() {
        log.info("查询今日订单状态统计");
        return Result.success(workspaceService.getOrderStatistics());
    }

    /**
     * 菜品统计
     */
    @GetMapping("/overviewDishes")
    @ApiOperation("菜品统计")
    public Result<DishOverViewVO> getDishStatistics() {
        log.info("查询菜品统计");
        return Result.success(workspaceService.getDishStatistics());
    }

    /**
     * 套餐统计
     */
    @GetMapping("/overviewSetmeals")
    @ApiOperation("套餐统计")
    public Result<SetmealOverViewVO> getSetmealStatistics() {
        log.info("查询套餐统计");
        return Result.success(workspaceService.getSetmealStatistics());
    }
}
