package com.sky.controller.user;

import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;
import com.sky.result.Result;
import com.sky.service.ShoppingCartService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequestMapping("/user/shoppingCart")
@RestController
@Slf4j
@Api(tags = "C端-购物车接口")
public class ShoppingCutController {

    @Autowired
    private ShoppingCartService shoppingCartService;

    /**
     * 添加购物车
     * @param shoppingCartDTO
     * @return
     */
    @PostMapping("/add")
    @ApiOperation("添加购物车")
    public Result add(@RequestBody ShoppingCartDTO shoppingCartDTO) {
        log.info("添加购物车：{}", shoppingCartDTO);
        shoppingCartService.addShoppingCart(shoppingCartDTO);
        return Result.success();
    }

    /**
     * 查询购物车列表
     */
    @GetMapping("/list")
    @ApiOperation("查询购物车列表")
    public Result<List<ShoppingCart>> list() {
        log.info("查询购物车列表");
        return Result.success(shoppingCartService.list());
    }

    /**
     * 清空购物车数据
     */
    @DeleteMapping("/clean")
    @ApiOperation("清空购物车数据")
    public Result clean() {
        log.info("清空购物车数据");
        shoppingCartService.clean();
        return Result.success();
    }

    /**
     * 购物车商品删除
     */
    @PostMapping("/sub")
    @ApiOperation("购物车商品删除")
    public Result sub(@RequestBody ShoppingCartDTO shoppingCartDTO) {
        log.info("购物车商品删除：{}", shoppingCartDTO);
        shoppingCartService.sub(shoppingCartDTO);
        return Result.success();
    }
}
