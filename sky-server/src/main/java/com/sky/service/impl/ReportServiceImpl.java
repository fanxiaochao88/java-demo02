package com.sky.service.impl;

import com.qcloud.cos.utils.StringUtils;
import com.sky.dto.GoodsSalesDTO;
import com.sky.dto.OrderReportDTO;
import com.sky.dto.TurnoverReportDTO;
import com.sky.dto.UserReportDTO;
import com.sky.entity.Orders;
import com.sky.mapper.ReportMapper;
import com.sky.service.ReportService;
import com.sky.service.WorkspaceService;
import com.sky.vo.*;
import org.apache.ibatis.annotations.Param;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReportServiceImpl implements ReportService {
    @Autowired
    private ReportMapper reportMapper;
    @Autowired
    private WorkspaceService workspaceService;
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

        // 3. 按照日期分类查询营业额
        List<TurnoverReportDTO> list = reportMapper.sumByMap(startTime, endTime, Orders.COMPLETED);

        // 4. 将查询出来的DTO转换成MAP, 方便按照日期查询金额, 方便补齐
        Map<LocalDate, BigDecimal> turnoverMap = list.stream()
                .collect(Collectors.toMap(TurnoverReportDTO::getLocalDate, TurnoverReportDTO::getTurnover));

        // 5. 组装营业额List, 其中有些日期没有营业额, 补0, 顺序和日期顺序一致
        List<String> turnOverStringList = dateList.stream().map(date -> {
            BigDecimal turnover = turnoverMap.get(date);
            if (turnover == null) {
                turnover = BigDecimal.ZERO;
            }
            return turnover.toString();
        }).collect(Collectors.toList());

        // 6. 封装VO并返回
        TurnoverReportVO res = TurnoverReportVO.builder()
                .dateList(dateList.stream().map(LocalDate::toString).collect(Collectors.joining( ",")))
                .turnoverList(turnOverStringList.stream().collect(Collectors.joining(",")))
                .build();
        return res;
    }

    /**
     * 给定日期范围进行用户统计查询,用于Echarts展示
     * @param begin
     * @param end
     * @return
     */
    @Override
    public UserReportVO userStatistics(LocalDate begin, LocalDate end) {
        // 1. 组装LocalDate List
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        while (!begin.equals(end)) {
            begin = begin.plusDays(1);
            dateList.add(begin);
        }
        // 2. 组装起始和结束日期
        LocalDateTime startTime = LocalDateTime.of(dateList.get(0), LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(dateList.get(dateList.size() - 1), LocalTime.MAX);
        // 3. 根据起始和结束日期 进行用户查询(分组), 查询出来的是List<实体类>
        List<UserReportDTO> list = reportMapper.userStatistics(startTime, endTime);
        // 4. 将List<实体类> 转换成Map
        Map<LocalDate, Long> userMap = list.stream()
                .collect(Collectors.toMap(UserReportDTO::getLocalDate, UserReportDTO::getUserCount));
        // 5. 遍历日期, 将Map结构转换成String列表, 并且补全空白日期为0
        List<String> userList = dateList.stream().map(date -> {
            Long count = userMap.get(date);
            if (count == null) {
                count = 0L;
                userMap.put(date, count);
            }
            return count.toString();
        }).collect(Collectors.toList());

        // 6. 组装所选日期的每一天的总用户量
        // 6.1 查询起始日期前一天的用户总量
        Long currentUserTotal = reportMapper.getTotalUserByTime(startTime);
        List<String> userListForTotalList = new ArrayList<>();
        // 6.2 后续日期的总用户量等于前一天用户量加上当天新增的用户量
        for (int i = 0; i < dateList.size(); i++) {
            currentUserTotal = currentUserTotal + userMap.get(dateList.get(i));
            userListForTotalList.add(currentUserTotal.toString());
        }
        // 6 组装VO并返回
        return UserReportVO.builder()
                .dateList(dateList.stream().map(LocalDate::toString).collect(Collectors.joining(",")))
                .newUserList(String.join(",", userList))
                .totalUserList(String.join(",", userListForTotalList))
                .build();
    }

    /**
     * 给定日期范围进行订单统计查询,用于Echarts展示
     * @param begin
     * @param end
     * @return
     */
    @Override
    public OrderReportVO ordersStatistics(LocalDate begin, LocalDate end) {
        // 1. 组装LocalDate List
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        while (!begin.equals(end)) {
            begin = begin.plusDays(1);
            dateList.add(begin);
        }
        // 2. 组装起始和结束日期
        LocalDateTime startTime = LocalDateTime.of(dateList.get(0), LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(dateList.get(dateList.size() - 1), LocalTime.MAX);
        // 3. 查询(使用自定义DTO)按照日期分组查询
        List<OrderReportDTO> list = reportMapper.ordersStatistics(startTime, endTime);
        // 4. 将查询结果转换成Map
        Map<LocalDate, OrderReportDTO> orderMap = list.stream().collect(Collectors.toMap(OrderReportDTO::getLocalDate, v -> v));
        // 5. 按照日期遍历, 获取对应日期的订单数据, 不足的补0, 将Map也补0
        List<OrderReportDTO> orderList = dateList.stream().map(date -> {
            OrderReportDTO orderReportDTO = orderMap.get(date);
            if (orderReportDTO == null) {
                orderReportDTO = new OrderReportDTO();
                orderReportDTO.setLocalDate(date);
                orderReportDTO.setTotalOrderCount(0);
                orderReportDTO.setValidOrderCount(0);
                orderMap.put(date, orderReportDTO);
            }
            return orderReportDTO;
        }).collect(Collectors.toList());
        // 6. 组装VO返回
        OrderReportVO res = OrderReportVO.builder()
                .dateList(dateList.stream().map(LocalDate::toString).collect(Collectors.joining(",")))
                .orderCountList(orderList.stream().map(OrderReportDTO::getTotalOrderCount).map(String::valueOf).collect(Collectors.joining(",")))
                .validOrderCountList(orderList.stream().map(OrderReportDTO::getValidOrderCount).map(String::valueOf).collect(Collectors.joining(",")))
                .totalOrderCount(orderList.stream().mapToInt(OrderReportDTO::getTotalOrderCount).sum())
                .validOrderCount(orderList.stream().mapToInt(OrderReportDTO::getValidOrderCount).sum())
                .build();
        res.setOrderCompletionRate(res.getValidOrderCount() * 1.0 / res.getTotalOrderCount());
        return res;
    }

    /**
     * 获取销量排名top10
     * @param begin
     * @param end
     * @return
     */
    @Override
    public SalesTop10ReportVO top10(LocalDate begin, LocalDate end) {
        //1. 组装起始和结束时间
        LocalDateTime startTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);
        //2. 分组连接查询 DTO List
        List<GoodsSalesDTO> list = reportMapper.findGoodsTop(startTime, endTime, Orders.COMPLETED, 10);
        //3. 组装VO并返回
        SalesTop10ReportVO res = SalesTop10ReportVO.builder()
                .nameList(list.stream().map(GoodsSalesDTO::getName).collect(Collectors.joining(",")))
                .numberList(list.stream().map(GoodsSalesDTO::getNumber).map(String::valueOf).collect(Collectors.joining(",")))
                .build();
        return res;
    }

    @Override
    public void exportBusinessData(HttpServletResponse response) {
        LocalDate begin = LocalDate.now().minusDays(30);
        LocalDate end = LocalDate.now().minusDays(1);
        //查询概览运营数据，提供给Excel模板文件
        BusinessDataVO businessData = workspaceService.getBusinessData(LocalDateTime.of(begin,LocalTime.MIN), LocalDateTime.of(end, LocalTime.MAX));
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("template/运营数据报表模板.xlsx");
        try {
            //基于提供好的模板文件创建一个新的Excel表格对象
            XSSFWorkbook excel = new XSSFWorkbook(inputStream);
            //获得Excel文件中的一个Sheet页
            XSSFSheet sheet = excel.getSheet("Sheet1");

            sheet.getRow(1).getCell(1).setCellValue(begin + "至" + end);
            //获得第4行
            XSSFRow row = sheet.getRow(3);
            //获取单元格
            row.getCell(2).setCellValue(businessData.getTurnover());
            row.getCell(4).setCellValue(businessData.getOrderCompletionRate());
            row.getCell(6).setCellValue(businessData.getNewUsers());
            row = sheet.getRow(4);
            row.getCell(2).setCellValue(businessData.getValidOrderCount());
            row.getCell(4).setCellValue(businessData.getUnitPrice());
            for (int i = 0; i < 30; i++) {
                LocalDate date = begin.plusDays(i);
                //准备明细数据
                businessData = workspaceService.getBusinessData(LocalDateTime.of(date,LocalTime.MIN), LocalDateTime.of(date, LocalTime.MAX));
                row = sheet.getRow(7 + i);
                row.getCell(1).setCellValue(date.toString());
                row.getCell(2).setCellValue(businessData.getTurnover());
                row.getCell(3).setCellValue(businessData.getValidOrderCount());
                row.getCell(4).setCellValue(businessData.getOrderCompletionRate());
                row.getCell(5).setCellValue(businessData.getUnitPrice());
                row.getCell(6).setCellValue(businessData.getNewUsers());
            }
            //通过输出流将文件下载到客户端浏览器中
            ServletOutputStream out = response.getOutputStream();
            excel.write(out);
            //关闭资源
            out.flush();
            out.close();
            excel.close();

        }catch (IOException e){
            e.printStackTrace();
        }
    }
}
