package com.sky.controller.user;

import com.sky.entity.Dish;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController("userDishController")
@RequestMapping("/user/dish")
@Api(tags = "C端-菜品接口")
@Slf4j
public class DishController {

    @Autowired
    private DishService dishService;

    @RequestMapping("/list")
    @ApiOperation("查询菜品列表")
    public Result<List<DishVO>> list(@ApiParam("分类id") @RequestParam Long categoryId) {
        log.info("查询菜品列表，分类id为：{}", categoryId);
        List<DishVO> list = dishService.list(categoryId);
        return Result.success(list);
    }
}
