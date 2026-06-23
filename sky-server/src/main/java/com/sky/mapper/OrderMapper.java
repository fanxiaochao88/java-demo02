package com.sky.mapper;

import com.sky.entity.Orders;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OrderMapper {
    /**
     * 插入订单数据
     * @param order
     */
    void insert(Orders order);

    @Update("update orders set status = #{status}, pay_status = #{payStatus}, checkout_time = #{checkoutTime} where number = #{number}")
    void updateByNumber(Orders orders);
}
