package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.dto.CategoryDTO;
import com.sky.entity.Category;
import com.sky.mapper.CategoryMapper;
import com.sky.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    private CategoryMapper categoryMapper;
    /**
     * 新增分类
     * @param category
     */
    @Override
    public void save(CategoryDTO category) {
        Category c = Category.builder()
                .type(category.getType())
                .name(category.getName())
                .sort(category.getSort())
                .status(0)
                .build();

        c.setCreateTime(LocalDateTime.now());
        c.setUpdateTime(LocalDateTime.now());

        c.setCreateUser(BaseContext.getCurrentId());
        c.setUpdateUser(BaseContext.getCurrentId());

        categoryMapper.insert(c);
    }
}
