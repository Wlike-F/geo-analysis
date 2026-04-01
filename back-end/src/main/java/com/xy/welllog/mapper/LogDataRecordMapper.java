package com.xy.welllog.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xy.welllog.entity.LogDataRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface LogDataRecordMapper extends BaseMapper<LogDataRecord> {
    @Delete({
        "<script>",
        "DELETE FROM log_data_records WHERE file_id IN ",
        "<foreach collection='fileIds' item='id' open='(' separator=',' close=')'>",
        "#{id}",
        "</foreach>",
        "</script>"
    })
    void deleteByFileIdsInBatches(@Param("fileIds") List<Long> fileIds);
}