package com.sky.mapper;

import com.sky.dto.GoodsSalesDTO;
import com.sky.dto.OrderReportDTO;
import com.sky.dto.TurnoverReportDTO;
import com.sky.dto.UserReportDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;

@Mapper
public interface ReportMapper {
    /**
     * 根据时间范围查询营业额数据
     * @param startTime
     * @param endTime
     * @param status
     * @return
     */
    List<TurnoverReportDTO> sumByMap(@Param("startTime") LocalDateTime startTime,
                                     @Param("endTime") LocalDateTime endTime,
                                     @Param("status") Integer status);

    /**
     * 根据时间范围查询用户数据(每日新增用户数)
     * @param startTime
     * @param endTime
     * @return
     */
    List<UserReportDTO> userStatistics(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    /**
     * 查询指定时间段的总用户量
     * @param begin
     * @return
     */
    @Select("select count(*) from user where create_time < #{begin}")
    Long getTotalUserByTime(@Param("begin") LocalDateTime begin);

    /**
     * 根据时间范围查询订单数据
     * @param startTime
     * @param endTime
     * @return
     */
    List<OrderReportDTO> ordersStatistics(@Param("begin") LocalDateTime startTime, @Param("end") LocalDateTime endTime);

    /**
     * 查询销量排名top10
     * @param startTime
     * @param endTime
     * @param completed
     * @param i
     * @return
     */
    List<GoodsSalesDTO> findGoodsTop(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("status") Integer completed,
            @Param("N") int N);
}
