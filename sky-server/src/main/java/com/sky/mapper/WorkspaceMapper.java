package com.sky.mapper;

import com.sky.dto.OrdersCountDTO;
import com.sky.entity.Orders;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface WorkspaceMapper {
    /**
     * 根据时间范围查询订单
     * @param begin
     * @param end
     * @return
     */
    List<Orders> getOrdersByTime(@Param("begin") LocalDateTime begin, @Param("end") LocalDateTime end);

    /**
     * 根据时间范围查询用户
     * @param begin
     * @param end
     * @return
     */
    Long countUser(LocalDateTime begin, LocalDateTime end);

    /**
     * 根据状态统计订单数量
     * @param begin
     * @param end
     * @return
     */
    List<OrdersCountDTO> countOrdersByStatus(@Param("begin") LocalDateTime begin, @Param("end") LocalDateTime end);
}
