package com.sky.service;

import com.sky.dto.CategoryDTO;
import com.sky.dto.CategoryPageQueryDTO;
import com.sky.entity.Category;
import com.sky.result.PageResult;

import java.util.List;

public interface CategoryService {
    /**
     * 新增分类
     * @param category
     */
    void save(CategoryDTO category);

    /**
     * 分页查询
     * @param pageDto
     * @return
     */
    PageResult pageQuery(CategoryPageQueryDTO pageDto);

    /**
     * 启用禁用分类
     * @param status
     * @param id
     */
    void startOrStop(Integer status, Long id);

    /**
     * 修改分类
     * @param category
     */
    void update(CategoryDTO category);

    /**
     * 删除分类
     * @param id
     */
    void delete(Integer id);

    /**
     * 查询
     * @param type
     * @return
     */
    List<Category> list(Integer type);
}
