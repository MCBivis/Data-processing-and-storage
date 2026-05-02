package org.example.web;

import org.example.dto.ScheduleEntry;
import org.example.service.ScheduleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/airports")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping("/{code}/schedule/inbound")
    public List<ScheduleEntry> inbound(@PathVariable String code) {
        return scheduleService.inboundSchedule(code);
    }

    @GetMapping("/{code}/schedule/outbound")
    public List<ScheduleEntry> outbound(@PathVariable String code) {
        return scheduleService.outboundSchedule(code);
    }
}
