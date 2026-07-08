package com.sky.dto;

import com.sky.entity.DishFlavor;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@ApiModel(description = "菜品统计DTO")
public class DishReportDTO implements Serializable {

    @ApiModelProperty("菜品数量")
    private Integer sellCount;
    //0 停售 1 起售
    @ApiModelProperty("0 停售 1 起售")
    private Integer status;

}
