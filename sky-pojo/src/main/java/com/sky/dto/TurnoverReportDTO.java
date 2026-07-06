package com.sky.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class TurnoverReportDTO implements Serializable {

    // 日期
    private LocalDate localDate;

    // 营业额
    private BigDecimal turnover;

}
