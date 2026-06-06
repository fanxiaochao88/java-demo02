package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.exception.DeletionNotAllowedException;
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
        if (records == null || records.size() == 0) {
            return new PageResult(total, records);
        }

        // 2 收集所有的菜品id
        List<Long> dishIds = records.stream().map(DishVO::getId).collect(Collectors.toList());
        if (dishIds == null || dishIds.size() == 0) {
            return new PageResult(total, records);
        }
        // 3 批量查询菜品对应的口味数据
        List<DishFlavor> dishFlavors = dishFlavorMapper.getByDishIds(dishIds);
        if (dishFlavors == null || dishFlavors.size() == 0) {
            return new PageResult(total, records);
        }

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

    /**
     * 批量删除
     * @param ids
     */
    @Override
    @Transactional
    public void deleteBatch(List<Long> ids) {
        // 判断是否能够删除, 是否存在起售
        Integer count = dishMapper.countByDishIds(ids);
        if (count > 0) {
            throw new DeletionNotAllowedException("存在起售中的菜品, 不允许删除");
        }

        // 删除菜品
        dishMapper.deleteBatch(ids);

        // 删除菜品关联的口味
        dishFlavorMapper.deleteByDishIds(ids);
    }

    /**
     * 批量起售停售
     * @param status
     * @param id
     */
    @Override
    public void startOrStop(Integer status, Long id) {
        Dish dish = Dish.builder()
                .id(id)
                .status(status)
                .build();

        dishMapper.update(dish);
    }

    /**
     * 根据id查询菜品和对应的口味
     * @param id
     * @return
     */
    @Override
    @Transactional
    public DishVO getByIdWithFlavor(Long id) {
        // 1. 查询菜品数据
        DishVO dishVO = dishMapper.getByIdWithFlavor(id);
        // 2. 查询口味数据
        List<DishFlavor> dishFlavors = dishFlavorMapper.getByDishId(id);
        // 3. 组装数据并返回
        dishVO.setFlavors(dishFlavors);

        return dishVO;
    }

    /**
     * 修改菜品
     * @param dishDTO
     */
    @Override
    public void update(DishDTO dishDTO) {
        // 1. 修改菜品表
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);
        dishMapper.update(dish);
        // 2. 删除菜品口味表
        dishFlavorMapper.deleteByDishId(dishDTO.getId());
        // 3. 添加菜品口味表
        List<DishFlavor> flavors = dishDTO.getFlavors();
        if (flavors != null && flavors.size() > 0) {
            flavors.forEach(flavor -> flavor.setDishId(dishDTO.getId()));
            dishFlavorMapper.insertBatch(flavors);
        }
    }

    /**
     * 条件查询
     * @param categoryId
     * @return
     */
    @Override
    public List<DishVO> list(Long categoryId) {
        return dishMapper.list(categoryId);
    }
}
