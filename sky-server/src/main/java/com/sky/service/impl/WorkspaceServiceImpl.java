package com.sky.service.impl;

import com.sky.dto.DishReportDTO;
import com.sky.dto.OrdersCountDTO;
import com.sky.dto.SetmealReportDTO;
import com.sky.entity.Orders;
import com.sky.mapper.WorkspaceMapper;
import com.sky.service.WorkspaceService;
import com.sky.vo.BusinessDataVO;
import com.sky.vo.DishOverViewVO;
import com.sky.vo.OrderOverViewVO;
import com.sky.vo.SetmealOverViewVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class WorkspaceServiceImpl implements WorkspaceService {

    @Autowired
    private WorkspaceMapper workspaceMapper;

    @Override
    @Transactional
    public BusinessDataVO getBusinessData(LocalDateTime begin, LocalDateTime end) {
        if (begin == null && end == null) {
            begin = LocalDateTime.now().with(LocalTime.MIN);
            end = LocalDateTime.now().with(LocalTime.MAX);
        }
        // 2. 查询今日的订单List
        List<Orders> ordersList = workspaceMapper.getOrdersByTime(begin, end);
        // 3. 统计营业额, 统计已完成订单, 计算完单率, 计算客单价
        int totalOrderCount = ordersList.size();
        int turnover = 0;
        int validOrderCount = 0;
        for (Orders orders : ordersList) {
            turnover += orders.getAmount().intValue();
            if (orders.getStatus() == Orders.COMPLETED) {
                validOrderCount++;
            }
        }
        // 4. 查询今日用户数
        Long userCount = workspaceMapper.countUser(begin, end);
        // 5. 构造返回结果对象并返回
        BusinessDataVO businessDataVO = BusinessDataVO.builder()
                .newUsers(Math.toIntExact(userCount))
                .unitPrice(turnover == 0 ? 0 : (turnover * 1.0) / validOrderCount)
                .turnover((double) turnover)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(totalOrderCount == 0 ? 0 : validOrderCount * 1.0 / totalOrderCount)
                .build();
        return businessDataVO;
    }

    /**
     * 获取订单统计数据
     * @return
     */
    @Override
    public OrderOverViewVO getOrderStatistics() {
        // 1. 构造当天的起始和结束日期
        LocalDateTime begin = LocalDateTime.now().with(LocalTime.MIN);
        LocalDateTime end = LocalDateTime.now().with(LocalTime.MAX);
        // 2. 按照订单状态分组计数
        List<OrdersCountDTO> ordersCountList = workspaceMapper.countOrdersByStatus(begin, end);
        // 3. 封装成Map结构
        Map<Integer, Integer> ordersCountMap = ordersCountList.stream()
                .collect(Collectors.toMap(OrdersCountDTO::getStatus, OrdersCountDTO::getCount));
        // 3.1 获取订单总数
        int allOrders = ordersCountList.stream().reduce(0, (sum, item) -> sum += item.getCount(), Integer::sum).intValue();
        // 4. 组装VO并且返回
        OrderOverViewVO orderOverViewVO = OrderOverViewVO.builder()
                .allOrders(allOrders)
                .cancelledOrders(ordersCountMap.getOrDefault(Orders.CANCELLED, 0))
                .completedOrders(ordersCountMap.getOrDefault(Orders.COMPLETED, 0))
                .deliveredOrders(ordersCountMap.getOrDefault(Orders.DELIVERY_IN_PROGRESS, 0))
                .waitingOrders(ordersCountMap.getOrDefault(Orders.TO_BE_CONFIRMED, 0))
                .build();
        return orderOverViewVO;
    }

    @Override
    public DishOverViewVO getDishStatistics() {
        //1. 按照菜品状态分类查询,统计数量
        List<DishReportDTO> dishReportList = workspaceMapper.getDishReport();
        //2. 转换为map
        Map<Integer, Integer> dishReportMap = dishReportList.stream().collect(Collectors.toMap(DishReportDTO::getStatus, DishReportDTO::getSellCount));
        //3. 使用流失操作组装VO返回
        DishOverViewVO dishOverViewVO = DishOverViewVO.builder()
                .sold(dishReportMap.getOrDefault(1, 0))
                .discontinued(dishReportMap.getOrDefault(0, 0))
                .build();
        return dishOverViewVO;
    }

    @Override
    public SetmealOverViewVO getSetmealStatistics() {
        List<SetmealReportDTO> setmealReportList = workspaceMapper.getSetmealReport();
        Map<Integer, Integer> setmealReportMap = setmealReportList.stream().collect(Collectors.toMap(SetmealReportDTO::getStatus, SetmealReportDTO::getSellCount));
        SetmealOverViewVO setmealOverViewVO = SetmealOverViewVO.builder()
                .sold(setmealReportMap.getOrDefault(1, 0))
                .discontinued(setmealReportMap.getOrDefault(0, 0))
                .build();
        return setmealOverViewVO;
    }
}
