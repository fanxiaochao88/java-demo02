package com.sky.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

@Data
@ApiModel(description = "分页查询参数")
public class CategoryPageQueryDTO implements Serializable {

    @ApiModelProperty("页码")
    private int page;


    @ApiModelProperty("每页条数")
    private int pageSize;

    @ApiModelProperty("分类名称")
    private String name;

    @ApiModelProperty("分类类型")
    private Integer type;

}
