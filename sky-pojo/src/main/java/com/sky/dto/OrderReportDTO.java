package com.sky.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

@Data
public class OrderReportDTO implements Serializable {

    // 日期
    private LocalDate localDate;

    // 订单总数
    private Integer totalOrderCount;

    // 成功订单数
    private Integer validOrderCount;

}
