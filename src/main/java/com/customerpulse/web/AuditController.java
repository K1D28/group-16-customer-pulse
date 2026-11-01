package com.customerpulse.web;

import com.customerpulse.domain.AuditAction;
import com.customerpulse.domain.AuditLog;
import com.customerpulse.domain.AuditRecordType;
import com.customerpulse.repository.AuditLogRepository;
import com.customerpulse.repository.CustomerRepository;
import com.customerpulse.service.AuditService;
import com.customerpulse.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** UI-12 / FR-13: Manager-only, read-only audit log (secured in SecurityConfig). There is no edit or delete. */
@Controller
@RequestMapping("/audit")
public class AuditController {

    public static final int PAGE_SIZE = 10;

    /** One changed field: for CREATE/DELETE only {@code value} is set; for UPDATE {@code oldValue} and {@code newValue}. */
    public record Change(String field, Object value, Object oldValue, Object newValue, boolean update) {}

    public record Row(AuditLog entry, List<Change> changes) {}

    private final AuditLogRepository log;
    private final AuditService audit;
    private final UserService users;
    private final CustomerRepository customers;

    public AuditController(AuditLogRepository log, AuditService audit, UserService users, CustomerRepository customers) {
        this.log = log;
        this.audit = audit;
        this.users = users;
        this.customers = customers;
    }

    @GetMapping
    public String list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(required = false) Long userId,
                       @RequestParam(required = false) AuditRecordType recordType,
                       @RequestParam(required = false) AuditAction action,
                       @RequestParam(required = false) Long customerId,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        Page<AuditLog> result = log.search(from == null ? null : from.atStartOfDay(),
                to == null ? null : to.plusDays(1).atStartOfDay(),
                userId, recordType, action, customerId, PageRequest.of(Math.max(page, 0), PAGE_SIZE));
        List<Row> rows = new ArrayList<>();
        for (AuditLog e : result.getContent()) rows.add(new Row(e, changes(e)));

        model.addAttribute("page", result);
        model.addAttribute("rows", rows);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("userId", userId);
        model.addAttribute("recordType", recordType);
        model.addAttribute("action", action);
        model.addAttribute("customerId", customerId);
        model.addAttribute("userOptions", users.all());
        model.addAttribute("customerOptions", customers.findAll(org.springframework.data.domain.Sort.by("companyName")));
        model.addAttribute("recordTypes", AuditRecordType.values());
        model.addAttribute("actions", AuditAction.values());
        return "audit/list";
    }

    private List<Change> changes(AuditLog e) {
        List<Change> list = new ArrayList<>();
        for (Map.Entry<String, Object> c : audit.read(e).entrySet()) {
            if (e.getAction() == AuditAction.UPDATE && c.getValue() instanceof List<?> pair && pair.size() == 2) {
                list.add(new Change(c.getKey(), null, pair.get(0), pair.get(1), true));
            } else {
                list.add(new Change(c.getKey(), c.getValue(), null, null, false));
            }
        }
        return list;
    }
}
