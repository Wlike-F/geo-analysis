package com.xy.welllog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xy.welllog.entity.WellLayer;
import com.xy.welllog.mapper.WellLayerMapper;
import com.xy.welllog.service.WellLayerService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class WellLayerServiceImpl extends ServiceImpl<WellLayerMapper, WellLayer> implements WellLayerService {

    @Override
    public String matchLayerName(Long fileId, double depth) {
        if (fileId == null) return null;
        BigDecimal d = BigDecimal.valueOf(depth);
        LambdaQueryWrapper<WellLayer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WellLayer::getFileId, fileId)
               .le(WellLayer::getTopDepth, d)
               .ge(WellLayer::getBottomDepth, d)
               .orderByAsc(WellLayer::getTopDepth)
               .last("LIMIT 1");
        WellLayer layer = this.getOne(wrapper);
        return layer != null ? layer.getLayerName() : null;
    }
}
