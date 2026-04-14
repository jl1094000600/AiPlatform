package com.aipal.service;

import com.aipal.entity.MonCallRecord;
import com.aipal.mapper.MonCallRecordMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class MonitorService extends ServiceImpl<MonCallRecordMapper, MonCallRecord> {
}
