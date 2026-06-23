package com.sky.mapper;

import com.sky.entity.AddressBook;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AddressBookMapper {
    /**
     * 插入数据
     * @param addressBook
     */
    @Insert("insert into address_book" +
            "        (user_id, consignee, phone, sex, province_code, province_name, city_code, city_name, district_code," +
            "         district_name, detail, label, is_default)" +
            "        values (#{userId}, #{consignee}, #{phone}, #{sex}, #{provinceCode}, #{provinceName}, #{cityCode}, #{cityName}," +
            "                #{districtCode}, #{districtName}, #{detail}, #{label}, #{isDefault})")
    void insert(AddressBook addressBook);

    /**
     * 查询当前用户的所有地址
     * @return
     */
    @Select("select * from address_book where user_id = #{userId}")
    List<AddressBook> list(Long userId);

    /**
     * 查询当前用户的默认地址
     * @return
     */
    @Select("select * from address_book where is_default = 1 and user_id = #{currentId}")
    AddressBook findDefault(Long currentId);

    /**
     * 根据用户id将所有的默认地址取消
     * @param currentId
     */
    @Update("update address_book set is_default = 0 where user_id = #{currentId}")
    void updateIsDefaultByUserId(Long currentId);

    /**
     * 设置默认地址
     * @param id
     */
    @Update("update address_book set is_default = 1 where id = #{id}")
    void setDefault(Long id);

    /**
     * 删除地址
     *
     * @param id
     * @param currentId
     */
    @Delete("delete from address_book where id = #{id} and user_id = #{currentId}")
    void delete(Long id, Long currentId);

    /**
     * 根据id查询地址
     * @param id
     * @return
     */
    @Select("select * from address_book where id = #{id}")
    AddressBook getById(Long id);

    /**
     * 修改地址
     * @param addressBook
     */
    void update(AddressBook addressBook);
}
