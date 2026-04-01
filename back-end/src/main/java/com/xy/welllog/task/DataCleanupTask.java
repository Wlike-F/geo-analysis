package com.xy.welllog.task;
import com.xy.welllog.mapper.LogDataRecordMapper;
import com.xy.welllog.mapper.LogFileInfoMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class DataCleanupTask {

    @Autowired
    private LogDataRecordMapper dataRecordMapper;

    @Autowired
    private LogFileInfoMapper fileInfoMapper;

    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupExpiredData() {
        log.info("开始执行测井历史数据定时清理任务...");
        LocalDateTime expireTime = LocalDateTime.now().minusDays(2);
        List<Long> expiredFileIds = fileInfoMapper.selectExpiredFileIds(expireTime);
        
        if(expiredFileIds != null && !expiredFileIds.isEmpty()) {
            dataRecordMapper.deleteByFileIdsInBatches(expiredFileIds);
            fileInfoMapper.deleteBatchIds(expiredFileIds);
            log.info("已清理 {} 个过期文件的海量明细数据", expiredFileIds.size());
        }
        log.info("测井历史数据定时清理任务执行完毕");
    }
}