package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.SetmealService;
import com.sky.vo.SetmealVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class SetmealServiceImpl implements SetmealService {

    @Autowired
    private SetmealMapper setmealMapper;
    @Autowired
    private SetmealDishMapper setmealDishMapper;

    /**
     * 新增套餐
     * @param setmealDTO
     */
    @Override
    public void save(SetmealDTO setmealDTO) {
        // 1. 将套餐数据转化为entity对象
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);
        // 2. 新增套餐
        setmealMapper.insert(setmeal);
        // 3. 获取套餐id
        Long setmealId = setmeal.getId();
        // 4. 获取套餐中的菜品数据
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        if (setmealDishes != null && setmealDishes.size() > 0) {
            setmealDishes.forEach(setmealDish -> setmealDish.setSetmealId(setmealId));
            setmealMapper.insertBatchSetmealDish(setmealDishes);
        }
    }

    /**
     * 套餐分页查询
     * @param setmealPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO) {
        PageHelper.startPage(setmealPageQueryDTO.getPage(), setmealPageQueryDTO.getPageSize());
        Page<SetmealVO> page = setmealMapper.pageQuery(setmealPageQueryDTO);

        List<SetmealVO> result = page.getResult();
        long total = page.getTotal();

        return new PageResult(total, result);
    }

    /**
     * 批量删除套餐
     * @param ids
     */
    @Override
    public void delete(List<Long> ids) {
        // 0. 判断是否有起售的套餐
        int count = setmealMapper.countBySetmealId(ids);
        if (count > 0) {
            throw new DeletionNotAllowedException("存在起售中的套餐");
        }
        // 1. 删除套餐数据
        setmealMapper.deleteBatch(ids);

        // 2. 删除套餐和菜品的关联数据
        setmealDishMapper.deleteBatch(ids);
    }

    /**
     * 套餐起售、停售
     * @param status
     * @param id
     */
    @Override
    public void startOrStop(Integer status, Long id) {
       Setmeal s = Setmeal.builder()
                .id(id)
                .status(status)
                .build();

       setmealMapper.update(s);
    }

    @Override
    public SetmealVO getByIdWithFlavor(Long id) {
        // 1. 查询套餐
        Setmeal setmeal = setmealMapper.getByIdWithFlavor(id);
        // 2. 查询套餐中的菜品数据
        List<SetmealDish> setmealDishes = setmealDishMapper.getBySetmealId(id);
        // 3. 组装数据并返回
        SetmealVO setmealVO = new SetmealVO();
        BeanUtils.copyProperties(setmeal, setmealVO);
        setmealVO.setSetmealDishes(setmealDishes);
        return setmealVO;
    }

    /**
     * 修改套餐
     * @param setmealDTO
     */
    @Override
    public void update(SetmealDTO setmealDTO) {
       // 1. 将数据转化为entity对象
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);
       // 2. 修改套餐
        setmealMapper.update(setmeal);
       // 3. 删除套餐中的菜品数据
        setmealDishMapper.deleteBatch(List.of(setmealDTO.getId()));
       // 4. 添加新的菜品数据
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        if (setmealDishes != null && setmealDishes.size() > 0) {
            setmealDishes.forEach(setmealDish -> setmealDish.setSetmealId(setmealDTO.getId()));
            setmealMapper.insertBatchSetmealDish(setmealDishes);
        }
    }
}
