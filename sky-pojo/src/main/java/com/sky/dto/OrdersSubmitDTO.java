package com.sky.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@ApiModel("订单提交数据")
public class OrdersSubmitDTO implements Serializable {
    //地址簿id
    @ApiModelProperty("地址簿id")
    private Long addressBookId;
    //付款方式
    @ApiModelProperty("付款方式")
    private int payMethod;
    //备注
    @ApiModelProperty("备注")
    private String remark;
    //预计送达时间
    @ApiModelProperty("预计送达时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime estimatedDeliveryTime;
    //配送状态  1立即送出  0选择具体时间
    @ApiModelProperty("配送状态  1立即送出  0选择具体时间")
    private Integer deliveryStatus;
    //餐具数量
    @ApiModelProperty("餐具数量")
    private Integer tablewareNumber;
    //餐具数量状态  1按餐量提供  0选择具体数量
    @ApiModelProperty("餐具数量状态  1按餐量提供  0选择具体数量")
    private Integer tablewareStatus;
    //打包费
    @ApiModelProperty("打包费")
    private Integer packAmount;
    //总金额
    @ApiModelProperty("总金额")
    private BigDecimal amount;
}
