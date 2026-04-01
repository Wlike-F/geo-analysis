package com.xy.welllog.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xy.welllog.entity.LogFileInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface LogFileInfoMapper extends BaseMapper<LogFileInfo> {
    @Select("SELECT id FROM log_file_info WHERE create_time < #{expireTime}")
    List<Long> selectExpiredFileIds(@Param("expireTime") LocalDateTime expireTime);
}