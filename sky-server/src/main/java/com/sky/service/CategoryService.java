package com.sky.service;

import com.sky.dto.CategoryDTO;
import com.sky.dto.CategoryPageQueryDTO;
import com.sky.result.PageResult;

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
}
