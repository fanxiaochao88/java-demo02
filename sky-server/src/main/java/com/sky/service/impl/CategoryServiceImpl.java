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
import org.springframework.beans.BeanUtils;
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

    /**
     * 启用禁用分类
     * @param status
     * @param id
     */
    @Override
    public void startOrStop(Integer status, Long id) {
        Category c = Category.builder()
                .id(id)
                .status(status)
                .updateTime(LocalDateTime.now())
                .updateUser(BaseContext.getCurrentId())
                .build();

        categoryMapper.update(c);
    }

    /**
     * 修改分类
     * @param category
     */
    @Override
    public void update(CategoryDTO category) {
        Category c = new Category();
        BeanUtils.copyProperties(category, c);

        c.setUpdateTime(LocalDateTime.now());
        c.setUpdateUser(BaseContext.getCurrentId());

        categoryMapper.update(c);
    }

    /**
     * 删除分类
     * @param id
     */
    @Override
    public void delete(Integer id) {
        categoryMapper.delete(id);
    }

    /**
     * 查询分类
     * @param type
     * @return
     */
    @Override
    public List<Category> list(Integer type) {
        List<Category> list = categoryMapper.list(type);
        return list;
    }
}
