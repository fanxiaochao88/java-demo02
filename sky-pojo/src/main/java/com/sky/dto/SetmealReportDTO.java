package com.sky.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import java.io.Serializable;

@Data
@ApiModel(description = "套餐统计DTO")
public class SetmealReportDTO implements Serializable {

    @ApiModelProperty("套餐数量")
    private Integer sellCount;
    //0 停售 1 起售
    @ApiModelProperty("0 停售 1 起售")
    private Integer status;

}
