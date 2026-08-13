package com.pcdoctor.service;

import com.pcdoctor.exception.BizException;
import com.pcdoctor.common.ErrorCode;
import com.pcdoctor.dto.MessageRequest;
import com.pcdoctor.dto.MessageView;
import com.pcdoctor.entity.Appointment;
import com.pcdoctor.entity.AppointmentMessage;
import com.pcdoctor.mapper.AppointmentMapper;
import com.pcdoctor.mapper.MessageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 预约留言（#5.3）：按 user_id 关联（#34），恢复老前端被注释的评论功能（#20）。
 */
@Slf4j
@Service
public class MessageService {

    private final MessageMapper messageMapper;
    private final AppointmentMapper appointmentMapper;

    public MessageService(MessageMapper messageMapper, AppointmentMapper appointmentMapper) {
        this.messageMapper = messageMapper;
        this.appointmentMapper = appointmentMapper;
    }

    /** 留言列表（公开） */
    public List<MessageView> list(Long appointmentId) {
        requireAppointment(appointmentId);
        return messageMapper.selectViewsByAppointment(appointmentId);
    }

    /** 发留言（登录） */
    @Transactional
    public MessageView post(Long appointmentId, MessageRequest request, String userId) {
        requireAppointment(appointmentId);
        AppointmentMessage m = new AppointmentMessage();
        m.setAppointmentId(appointmentId);
        m.setUserId(userId);
        m.setMessage(request.message());
        m.setPicture(request.picture());
        messageMapper.insert(m);
        log.info("新留言: appointmentId={}, userId={}", appointmentId, userId);
        // 复用列表查询返回，保证视图一致（含昵称）
        return messageMapper.selectViewsByAppointment(appointmentId).stream()
                .filter(v -> v.id().equals(m.getId()))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.SYSTEM_ERROR));
    }

    private void requireAppointment(Long appointmentId) {
        Appointment a = appointmentMapper.selectById(appointmentId);
        if (a == null) {
            throw new BizException(ErrorCode.APPOINTMENT_NOT_FOUND);
        }
    }
}