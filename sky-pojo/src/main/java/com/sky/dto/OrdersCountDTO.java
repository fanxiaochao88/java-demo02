package com.sky.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

@Data
public class OrdersCountDTO implements Serializable {

    // 订单状态
    private Integer status;
    // 数量
    private Integer count;
}
