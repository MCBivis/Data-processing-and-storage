package org.example.service;

import org.example.dto.ScheduleEntry;
import org.example.repository.ScheduleRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;

    public ScheduleService(ScheduleRepository scheduleRepository) {
        this.scheduleRepository = scheduleRepository;
    }

    public List<ScheduleEntry> inboundSchedule(String airportCode) {
        String code = airportCode.trim().toUpperCase();
        return scheduleRepository.findInboundSchedule(code);
    }

    public List<ScheduleEntry> outboundSchedule(String airportCode) {
        String code = airportCode.trim().toUpperCase();
        return scheduleRepository.findOutboundSchedule(code);
    }
}
