package com.xy.welllog.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xy.welllog.entity.WellLayer;

public interface WellLayerService extends IService<WellLayer> {

    /**
     * 根据深度查找匹配的层名
     * @param fileId 文件ID
     * @param depth  深度值
     * @return 匹配的层名，未匹配返回 null
     */
    String matchLayerName(Long fileId, double depth);
}
