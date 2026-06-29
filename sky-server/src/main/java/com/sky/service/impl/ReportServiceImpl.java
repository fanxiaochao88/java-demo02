package com.sky.service.impl;

import com.qcloud.cos.utils.StringUtils;
import com.sky.entity.Orders;
import com.sky.mapper.ReportMapper;
import com.sky.service.ReportService;
import com.sky.vo.TurnoverReportVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReportServiceImpl implements ReportService {
    @Autowired
    private ReportMapper reportMapper;
    /**
     * 给定日期范围进行营业额查询,用于Echarts展示
     * @param begin
     * @param end
     * @return
     */
    @Override
    public TurnoverReportVO turnoverStatistics(LocalDate begin, LocalDate end) {
        // 1. 组装LocalDate List
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        while (!begin.equals(end)) {
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        // 2. 根据dateList 进行营业额查询
        List<Double> turnoverList = new ArrayList<>();
        LocalDateTime startTime = LocalDateTime.of(dateList.get(0), LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(dateList.get(dateList.size() - 1), LocalTime.MAX);
        HashMap<Object, Object> map = new HashMap<>();
        map.put("begin", startTime);
        map.put("end", endTime);
        map.put("status", Orders.COMPLETED);
        List<HashMap<String, Double>> sqlRes = new ArrayList<>();
        sqlRes = reportMapper.sumByMap(map);

        dateList.stream().map(d -> {
            return d.toString();
        }).collect(Collectors.joining(","));

        return TurnoverReportVO.builder()
                .dateList(dateList.stream().map(d -> d.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))).collect(Collectors.joining(",")))
                .turnoverList(turnoverList.stream().map(d -> String.valueOf(d)).collect(Collectors.joining(",")))
                .build();
    }
}
