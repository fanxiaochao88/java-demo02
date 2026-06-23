package com.sky.entity;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 地址簿
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "地址簿")
public class AddressBook implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("主键")
    private Long id;

    //用户id
    @ApiModelProperty("用户id")
    private Long userId;

    //收货人
    @ApiModelProperty("收货人")
    private String consignee;

    //手机号
    @ApiModelProperty("手机号")
    private String phone;

    //性别 0 女 1 男
    @ApiModelProperty("性别 0 女 1 男")
    private String sex;

    //省级区划编号
    @ApiModelProperty("省级区划编号")
    private String provinceCode;

    //省级名称
    @ApiModelProperty("省级名称")
    private String provinceName;

    //市级区划编号
    @ApiModelProperty("市级区划编号")
    private String cityCode;

    //市级名称
    @ApiModelProperty("市级名称")
    private String cityName;

    //区级区划编号
    @ApiModelProperty("区级区划编号")
    private String districtCode;

    //区级名称
    @ApiModelProperty("区级名称")
    private String districtName;

    //详细地址
    @ApiModelProperty("详细地址")
    private String detail;

    //标签
    @ApiModelProperty("标签")
    private String label;

    //是否默认 0否 1是
    @ApiModelProperty("是否默认 0否 1是")
    private Integer isDefault;
}
