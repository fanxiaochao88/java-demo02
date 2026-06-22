package com.sky.controller.user;

import com.sky.entity.SetmealDish;
import com.sky.result.Result;
import com.sky.service.SetmealService;
import com.sky.vo.SetmealVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController("userSetmealController")
@RequestMapping("/user/setmeal")
@Api(tags = "C端-套餐接口")
@Slf4j
public class SetmealController {

    @Autowired
    private SetmealService setmealService;

    @RequestMapping("/list")
    @ApiOperation("按照套餐分类查询套餐")
    @Cacheable(value = "setmealCache", key = "#categoryId")
    public Result<List<SetmealVO>> list(@ApiParam("分类id") @RequestParam Long categoryId) {
        log.info("查询分类id：{}的套餐", categoryId);
        return Result.success(setmealService.list(categoryId));
    }


    /**
     * 根据套餐id查询菜品
     */
    @GetMapping("/dish/{id}")
    @ApiOperation("根据套餐id查询菜品")
    public Result<List<SetmealDish>> getDishIds(@ApiParam("套餐id") @PathVariable Long id) {
        log.info("根据套餐id查询菜品：{}", id);
        return Result.success(setmealService.getDishId(id));
    }
}
