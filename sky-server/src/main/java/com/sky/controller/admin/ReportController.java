package com.sky.controller.admin;

import com.sky.result.Result;
import com.sky.service.ReportService;
import com.sky.vo.TurnoverReportVO;
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
}
