package com.sky.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.HashMap;
import java.util.List;

@Mapper
public interface ReportMapper {
    List<HashMap<String, Double>> sumByMap(HashMap<Object, Object> map);
}
