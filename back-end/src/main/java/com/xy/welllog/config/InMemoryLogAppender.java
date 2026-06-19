package com.xy.welllog.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * 内存环形日志缓冲器，捕获最近 500 条运行日志供前端查看。
 */
@Slf4j
@Component
public class InMemoryLogAppender extends AppenderBase<ILoggingEvent> {

    private static final int MAX_ENTRIES = 500;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LinkedList<LogEntry> buffer = new LinkedList<>();

    @PostConstruct
    public void init() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        this.setContext(context);
        this.setName("inMemoryAppender");
        this.start();
        rootLogger.addAppender(this);
        log.info("InMemoryLogAppender 已注册，环形缓冲容量: {} 条", MAX_ENTRIES);
    }

    @Override
    protected void append(ILoggingEvent event) {
        LogEntry entry = new LogEntry(
                LocalDateTime.now().format(FMT),
                event.getLevel().toString(),
                shortenLoggerName(event.getLoggerName()),
                event.getFormattedMessage()
        );

        synchronized (buffer) {
            buffer.addLast(entry);
            if (buffer.size() > MAX_ENTRIES) {
                buffer.removeFirst();
            }
        }
    }

    /**
     * 获取日志条目
     * @param level 过滤级别（null 或 "ALL" 表示不过滤）
     * @param maxLines 最多返回条数
     */
    public List<LogEntry> getEntries(String level, int maxLines) {
        List<LogEntry> result = new ArrayList<>();
        Level filterLevel = null;
        if (level != null && !level.equalsIgnoreCase("ALL")) {
            try {
                filterLevel = Level.toLevel(level, Level.ALL);
            } catch (Exception e) {
                filterLevel = null;
            }
        }

        synchronized (buffer) {
            int count = 0;
            for (int i = buffer.size() - 1; i >= 0 && count < maxLines; i--) {
                LogEntry entry = buffer.get(i);
                if (filterLevel == null || isLevelAtOrAbove(entry.level, filterLevel)) {
                    result.add(entry);
                    count++;
                }
            }
        }

        // 反转使时间正序
        java.util.Collections.reverse(result);
        return result;
    }

    private boolean isLevelAtOrAbove(String entryLevel, Level filterLevel) {
        try {
            Level entry = Level.toLevel(entryLevel, Level.ALL);
            return entry.isGreaterOrEqual(filterLevel);
        } catch (Exception e) {
            return true;
        }
    }

    private String shortenLoggerName(String name) {
        if (name == null) return "";
        int lastDot = name.lastIndexOf('.');
        return lastDot >= 0 ? name.substring(lastDot + 1) : name;
    }

    /**
     * 日志条目数据结构
     */
    public static class LogEntry {
        public String timestamp;
        public String level;
        public String logger;
        public String message;

        public LogEntry(String timestamp, String level, String logger, String message) {
            this.timestamp = timestamp;
            this.level = level;
            this.logger = logger;
            this.message = message;
        }
    }
}
