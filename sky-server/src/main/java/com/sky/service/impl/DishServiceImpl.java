package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.result.PageResult;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DishServiceImpl implements DishService {

    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private DishFlavorMapper dishFlavorMapper;
    /**
     * 新增菜品
     * @param dishDTO
     */
    @Override
    @Transactional
    public void save(DishDTO dishDTO) {
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);
        // 向菜品表插入一条数据
        dishMapper.insert(dish);
        Long id = dish.getId();
        // 向口味表插入多条数据
        List<DishFlavor> flavors = dishDTO.getFlavors();
        if (flavors != null && flavors.size() > 0) {
            flavors.forEach(flavor -> flavor.setDishId(id));
            dishFlavorMapper.insertBatch(flavors);
        }
    }

    /**
     * 菜品分页查询
     * @param dishPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO) {
        // 1 查询菜品以及左连接查询菜品分类名称
        PageHelper.startPage(dishPageQueryDTO.getPage(), dishPageQueryDTO.getPageSize());
        Page<DishVO> page = dishMapper.pageQuery(dishPageQueryDTO);
        long total = page.getTotal();
        List<DishVO> records = page.getResult();

        // 2 收集所有的菜品id
        List<Long> dishIds = records.stream().map(DishVO::getId).collect(Collectors.toList());

        // 3 批量查询菜品对应的口味数据
        List<DishFlavor> dishFlavors = dishFlavorMapper.getByDishIds(dishIds);

        // 4. 按照dishId分组
        Map<Long, List<DishFlavor>> flavorMap = new HashMap<>();
        for (DishFlavor dishFlavor : dishFlavors) {
            flavorMap.putIfAbsent(dishFlavor.getDishId(), new ArrayList<>());
            flavorMap.get(dishFlavor.getDishId()).add(dishFlavor);
        }

        // 5. 封装DishVO数据
        for (DishVO record : records) {
            record.setFlavors(flavorMap.getOrDefault(record.getId(), new ArrayList<>()));
        }

        return new PageResult(total, records);
    }
}
