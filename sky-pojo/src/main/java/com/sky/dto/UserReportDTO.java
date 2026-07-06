package com.sky.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class UserReportDTO implements Serializable {

    // 日期
    private LocalDate localDate;

    // 用户数
    private Long userCount;

}
