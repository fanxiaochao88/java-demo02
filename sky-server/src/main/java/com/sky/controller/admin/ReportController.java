package com.sky.controller.admin;

import com.sky.result.Result;
import com.sky.service.ReportService;
import com.sky.vo.OrderReportVO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/admin/report")
@Slf4j
@Api(tags = "报表统计相关接口")
public class ReportController {

    @Autowired
    private ReportService reportService;
    /**
     * 营业额统计
     */
    @GetMapping("/turnoverStatistics")
    @ApiOperation("营业额统计")
    public Result<TurnoverReportVO> turnoverStatistics(
            @ApiParam(value = "开始时间")
            @DateTimeFormat(pattern = "yyyy-MM-dd")
            LocalDate begin,
            @ApiParam(value = "结束时间")
            @DateTimeFormat(pattern = "yyyy-MM-dd")
            LocalDate end) {
        log.info("营业额数据统计：{}到{}", begin, end);
        TurnoverReportVO turnoverReportVO = reportService.turnoverStatistics(begin, end);
        return Result.success(turnoverReportVO);
    }

    /**
     * 用户数据统计
     */
    @GetMapping("/userStatistics")
    @ApiOperation("用户数据统计")
    public Result<UserReportVO> userStatistics(
            @ApiParam(value = "开始时间")
            @DateTimeFormat(pattern = "yyyy-MM-dd")
            LocalDate begin,
            @ApiParam(value = "结束时间")
            @DateTimeFormat(pattern = "yyyy-MM-dd")
            LocalDate end
    ) {
        log.info("用户数据统计：{}到{}", begin, end);
        return Result.success(reportService.userStatistics(begin, end));
    }

    /**
     * 订单分析
     */
    @GetMapping("/ordersStatistics")
    @ApiOperation("订单分析")
    public Result<OrderReportVO> ordersStatistics(
            @ApiParam(value = "开始时间")
            @DateTimeFormat(pattern = "yyyy-MM-dd")
            LocalDate begin,
            @ApiParam(value = "结束时间")
            @DateTimeFormat(pattern = "yyyy-MM-dd")
            LocalDate end
    ) {
        log.info("订单数据统计：{}到{}", begin, end);
        OrderReportVO orderReportVO = reportService.ordersStatistics(begin, end);
        return Result.success(orderReportVO);
    }

    /**
     * 菜品销量排名
     */
    @GetMapping("/top10")
    @ApiOperation("菜品销量排名")
    public Result<SalesTop10ReportVO> top10(
            @ApiParam(value = "开始时间")
            @DateTimeFormat(pattern = "yyyy-MM-dd")
            LocalDate begin,
            @ApiParam(value = "结束时间")
            @DateTimeFormat(pattern = "yyyy-MM-dd")
            LocalDate end
    ) {
        log.info("销量排名：{}到{}", begin, end);
        return Result.success(reportService.top10(begin, end));
    }
}
