package com.sky.controller.admin;

import com.sky.result.Result;
import com.sky.service.WorkspaceService;
import com.sky.vo.BusinessDataVO;
import com.sky.vo.OrderOverViewVO;
import com.sky.vo.OrderStatisticsVO;
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
        return Result.success(workspaceService.getBusinessData());
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
}
