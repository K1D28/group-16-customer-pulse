package com.customerpulse.service;

import com.customerpulse.domain.*;
import com.customerpulse.repository.*;
import com.customerpulse.web.form.CustomerForm;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional
public class CustomerService {

    public static final int PAGE_SIZE = 10;

    /** Words ignored when checking for similar company names ("ABC Co." vs "ABC Company"). */
    private static final Set<String> NAME_NOISE = Set.of(
            "co", "company", "ltd", "limited", "inc", "corp", "corporation", "group",
            "the", "plc", "llc", "pcl", "and");

    private final CustomerRepository customers;
    private final UserRepository users;
    private final ActivityRepository activities;
    private final FollowUpRepository followUps;
    private final StatusHistoryRepository history;
    private final CaseRepository cases;
    private final CaseAssignmentRepository caseAssignments;
    private final AccessGuard guard;

    public CustomerService(CustomerRepository customers, UserRepository users, ActivityRepository activities,
                           FollowUpRepository followUps, StatusHistoryRepository history, CaseRepository cases,
                           CaseAssignmentRepository caseAssignments, AccessGuard guard) {
        this.customers = customers;
        this.users = users;
        this.activities = activities;
        this.followUps = followUps;
        this.history = history;
        this.cases = cases;
        this.caseAssignments = caseAssignments;
        this.guard = guard;
    }

    /** FR-11: paged search within the user's scope, 10 per page. */
    @Transactional(readOnly = true)
    public Page<Customer> search(AppUser me, String q, CustomerStatus status, int page) {
        String text = (q == null || q.isBlank()) ? null : q.trim();
        PageRequest pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by("companyName"));
        return customers.search(guard.scope(me), guard.team(me), status, text, pageable);
    }

    @Transactional(readOnly = true)
    public List<Customer> listInScope(AppUser me) {
        return customers.findInScope(guard.scope(me), guard.team(me));
    }

    /** FR-03 step 2: load a customer only if the user may see it. */
    @Transactional(readOnly = true)
    public Customer getAccessible(Long id, AppUser me) {
        Customer c = customers.findById(id).orElseThrow(() -> new NotFoundException("Customer not found"));
        guard.checkAccess(me, c);
        return c;
    }

    /** FR-02 step 3: customers whose names look like the same company. */
    @Transactional(readOnly = true)
    public List<Customer> findSimilar(String companyName, Long excludeId, AppUser me) {
        String target = normalize(companyName);
        if (target.isEmpty()) return List.of();
        List<Customer> result = new ArrayList<>();
        for (Customer c : customers.findByOwnerTeamId(me.getTeam().getId())) {
            if (c.getId().equals(excludeId)) continue;
            String other = normalize(c.getCompanyName());
            if (other.isEmpty()) continue;
            boolean similar = other.equals(target)
                    || (target.length() >= 3 && other.contains(target))
                    || (other.length() >= 3 && target.contains(other));
            if (similar) result.add(c);
        }
        return result;
    }

    static String normalize(String name) {
        if (name == null) return "";
        String cleaned = name.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        StringBuilder sb = new StringBuilder();
        for (String word : cleaned.split("\\s+")) {
            if (word.isEmpty() || NAME_NOISE.contains(word)) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(word);
        }
        return sb.toString();
    }

    /** FR-02: new customers start as NEGOTIATING with a first history entry. */
    public Customer create(CustomerForm form, AppUser me) {
        Customer c = new Customer();
        apply(c, form);
        c.setOwner(me.isManager() && form.getOwnerId() != null ? loadUser(form.getOwnerId(), me) : me);
        c.setCurrentStatus(CustomerStatus.NEGOTIATING);
        customers.save(c);
        writeHistory(c, null, CustomerStatus.NEGOTIATING, me, "New lead created");
        return c;
    }

    public Customer update(Long id, CustomerForm form, AppUser me) {
        Customer c = getAccessible(id, me);
        apply(c, form);
        // Only Managers can move ownership (FR-09)
        if (me.isManager() && form.getOwnerId() != null && !form.getOwnerId().equals(c.getOwner().getId())) {
            c.setOwner(loadUser(form.getOwnerId(), me));
        }
        return c;
    }

    /** Deletes the customer and everything recorded about it. Managers only. */
    public void delete(Long id, AppUser me) {
        guard.checkManager(me);
        Customer c = getAccessible(id, me);
        Long cid = c.getId();
        followUps.deleteByCustomerId(cid);   // follow-ups first: they may point at activities
        activities.deleteByCustomerId(cid);
        caseAssignments.deleteByCustomerId(cid);
        cases.deleteByCustomerId(cid);
        history.deleteByCustomerId(cid);
        customers.deleteById(cid);
    }

    /**
     * FR-06: change status and write the history entry in one transaction.
     * Returns false (and saves nothing) when the status is unchanged.
     */
    public boolean changeStatus(Long id, CustomerStatus newStatus, String reason, AppUser me) {
        Customer c = getAccessible(id, me);
        if (newStatus == null) throw new BusinessException("Choose a new status.");
        if (reason != null && reason.length() > 255) throw new BusinessException("Reason can be up to 255 characters.");
        if (c.getCurrentStatus() == newStatus) return false;
        CustomerStatus previous = c.getCurrentStatus();
        c.setCurrentStatus(newStatus);
        writeHistory(c, previous, newStatus, me, blankToNull(reason));
        return true;
    }

    /**
     * FR-09: move a customer (with its full history) to another active user.
     * Optionally the old owner's open follow-ups on this customer move too.
     */
    public Customer reassign(Long id, Long newOwnerId, boolean moveFollowUps, AppUser me) {
        guard.checkManager(me);
        Customer c = getAccessible(id, me);
        AppUser oldOwner = c.getOwner();
        AppUser newOwner = loadUser(newOwnerId, me);
        if (newOwner.getId().equals(oldOwner.getId())) throw new BusinessException(newOwner.getFullName() + " already owns this customer.");
        c.setOwner(newOwner);
        customers.flush();
        if (moveFollowUps) followUps.moveOpenFollowUps(id, oldOwner.getId(), newOwner);
        return customers.findById(id).orElseThrow();
    }

    @Transactional(readOnly = true)
    public Map<CustomerStatus, Long> countByStatus(Long ownerId, Long teamId) {
        Map<CustomerStatus, Long> counts = new EnumMap<>(CustomerStatus.class);
        for (CustomerStatus s : CustomerStatus.values()) counts.put(s, 0L);
        for (Object[] row : customers.countByStatus(ownerId, teamId)) {
            counts.put((CustomerStatus) row[0], (Long) row[1]);
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public Map<Long, LocalDateTime> lastActivityByCustomer() {
        Map<Long, LocalDateTime> map = new HashMap<>();
        for (Object[] row : activities.lastActivityPerCustomer()) {
            map.put((Long) row[0], (LocalDateTime) row[1]);
        }
        return map;
    }

    private void writeHistory(Customer c, CustomerStatus previous, CustomerStatus next, AppUser by, String reason) {
        StatusHistory h = new StatusHistory();
        h.setCustomer(c);
        h.setPreviousStatus(previous);
        h.setNewStatus(next);
        h.setChangedBy(by);
        h.setReason(reason);
        history.save(h);
    }

    private void apply(Customer c, CustomerForm f) {
        c.setCompanyName(f.getCompanyName().trim());
        c.setContactName(blankToNull(f.getContactName()));
        c.setEmail(blankToNull(f.getEmail()));
        c.setPhone(blankToNull(f.getPhone()));
    }

    /** Customers can only be owned by someone in the acting user's team. */
    private AppUser loadUser(Long id, AppUser me) {
        AppUser u = users.findById(id).orElseThrow(() -> new BusinessException("Selected user does not exist."));
        if (!u.sameTeam(me)) throw new BusinessException("Owners must be in your team.");
        if (!u.isActive()) throw new BusinessException(u.getFullName() + " is not an active user. Choose an active user.");
        return u;
    }

    static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
