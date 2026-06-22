package com.sky.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import java.io.Serializable;

@Data
@ApiModel("购物车数据")
public class ShoppingCartDTO implements Serializable {

    @ApiModelProperty("菜品id")
    private Long dishId;
    @ApiModelProperty("套餐id")
    private Long setmealId;
    @ApiModelProperty("口味")
    private String dishFlavor;

}
