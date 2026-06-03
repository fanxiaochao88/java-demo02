package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.context.BaseContext;
import com.sky.dto.CategoryDTO;
import com.sky.dto.CategoryPageQueryDTO;
import com.sky.entity.Category;
import com.sky.mapper.CategoryMapper;
import com.sky.result.PageResult;
import com.sky.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

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

    /**
     * 分类分页查询
     * @param pageDto
     * @return
     */
    @Override
    public PageResult pageQuery(CategoryPageQueryDTO pageDto) {
        PageHelper.startPage(pageDto.getPage(), pageDto.getPageSize());
        Page<Category> page = categoryMapper.pageQuery(pageDto);
        long total = page.getTotal();
        List<Category> result = page.getResult();
        return new PageResult(total, result);
    }
}
