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
import org.springframework.data.redis.core.RedisTemplate;
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
    @Autowired
    private RedisTemplate redisTemplate;

    @RequestMapping("/list")
    @ApiOperation("查询菜品列表")
    public Result<List<DishVO>> list(@ApiParam("分类id") @RequestParam Long categoryId) {
        log.info("查询菜品列表，分类id为：{}", categoryId);

        // 1. 构造redis key
        String key = "dish_" + categoryId;

        // 2. 查询redis
        List<DishVO> cache = (List<DishVO>) redisTemplate.opsForValue().get(key);
        if (cache != null && cache.size() > 0) {
            return Result.success(cache);
        }
        // 3. 查询数据库
        List<DishVO> list = dishService.list(categoryId);

        // 4. 缓存数据
        redisTemplate.opsForValue().set(key, list);

        return Result.success(list);
    }
}
