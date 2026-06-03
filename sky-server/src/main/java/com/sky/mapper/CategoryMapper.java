package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.dto.CategoryPageQueryDTO;
import com.sky.entity.Category;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CategoryMapper {
    /**
     * 新增分类
     * @param c
     */
    @Insert("insert into category (type, name, sort, create_time, update_time, create_user, update_user, status) " +
            "values (#{type}, #{name}, #{sort}, #{createTime}, #{updateTime}, #{createUser}, #{updateUser}, #{status})")
    void insert(Category c);

    /**
     * 分类分页查询
     * @param pageDto
     * @return
     */
    Page<Category> pageQuery(CategoryPageQueryDTO pageDto);
}
