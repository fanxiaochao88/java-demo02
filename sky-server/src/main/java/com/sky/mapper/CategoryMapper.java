package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.dto.CategoryPageQueryDTO;
import com.sky.entity.Category;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

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

    /**
     * 修改分类
     * @param c
     */
    void update(Category c);

    /**
     * 删除分类
     * @param id
     */
    @Delete("delete from category where id = #{id}")
    void delete(Integer id);

    /**
     * 查询分类
     * @param type
     * @return
     */
    @Select("select * from category where type = #{type} order by sort asc")
    List<Category> list(Integer type);
}
