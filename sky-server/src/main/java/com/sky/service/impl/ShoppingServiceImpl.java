package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.ShoppingCart;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.ShoppingCartService;
import com.sky.vo.DishVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ShoppingServiceImpl implements ShoppingCartService {

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private SetmealMapper setmealMapper;
    /**
     * 添加购物车
     * @param shoppingCartDTO
     */
    @Override
    public void addShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        // 1. 构造购物车Entry
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);
        shoppingCart.setUserId(BaseContext.getCurrentId());
        // 2. 查询当前添加的条目是否在购物车中
        List<ShoppingCart>  shoppingCartList = shoppingCartMapper.list(shoppingCart);
        // 3. 如果存在, 添加数量
        if (shoppingCartList != null && shoppingCartList.size() == 1) {
            shoppingCart = shoppingCartList.get(0);
            shoppingCart.setNumber(shoppingCart.getNumber() + 1);
            shoppingCartMapper.updateShopCartById(shoppingCart);
            return;
        }
        // 4. 如果不存在, 判断菜品还是套餐
        Long dishId = shoppingCart.getDishId();
        if (dishId != null) {
            // 菜品
            DishVO dish = dishMapper.getByIdWithFlavor(dishId);
            shoppingCart.setImage(dish.getImage());
            shoppingCart.setName(dish.getName());
            shoppingCart.setAmount(dish.getPrice());
        } else {
            // 套餐
            Setmeal byIdWithFlavor = setmealMapper.getByIdWithFlavor(shoppingCart.getSetmealId());
            shoppingCart.setImage(byIdWithFlavor.getImage());
            shoppingCart.setName(byIdWithFlavor.getName());
            shoppingCart.setAmount(byIdWithFlavor.getPrice());
        }
        // 5. 如果不存在, 添加这一条购物车数据
        shoppingCart.setNumber(1);
        shoppingCart.setCreateTime(LocalDateTime.now());
        // 6. 插入
        shoppingCartMapper.insert(shoppingCart);
    }

    /**
     * 查询购物车列表
     * @return
     */
    @Override
    public List<ShoppingCart> list() {
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(ShoppingCart
                .builder()
                .userId(BaseContext.getCurrentId())
                .build());
        return shoppingCartList;
    }

    /**
     * 清空购物车数据
     */
    @Override
    public void clean() {
        Long userID = BaseContext.getCurrentId();
        shoppingCartMapper.clear(userID);
    }

    /**
     * 购物车数量删减
     * @param shoppingCartDTO
     */
    @Override
    public void sub(ShoppingCartDTO shoppingCartDTO) {
        // 1. 组装购物车entry
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);
        shoppingCart.setUserId(BaseContext.getCurrentId());
        // 2. 查询出对应的条目
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);
        if (shoppingCartList != null && shoppingCartList.size() == 1) {
            shoppingCart = shoppingCartList.get(0);
            // 3. 判断数量
            Integer number = shoppingCart.getNumber();
            if (number == 1) {
                // 4. 数量删减完之后是0, 则直接删除
                shoppingCartMapper.delete(shoppingCart);
            } else {

                // 5. 否则将数量改为 -1即可
                shoppingCart.setNumber(shoppingCart.getNumber() - 1);
                shoppingCartMapper.updateShopCartById(shoppingCart);
            }
        }
    }
}
