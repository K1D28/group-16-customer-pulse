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
    /** FR-12: at most this many similar names are listed in the warning. */
    public static final int MAX_SIMILAR = 5;
    /** FR-12: shorter names (after removing spaces and punctuation) are only checked for exact matches. */
    public static final int MIN_SIMILAR_LENGTH = 3;
    /** FR-02 step 5: reason on the first status-history entry. */
    public static final String NEW_CUSTOMER_REASON = "New customer created";

    /** Result of the FR-12 check: an exact match blocks saving; similar names only warn. */
    public record DuplicateCheck(Customer exact, List<Customer> similar) {
        public boolean blocked() { return exact != null; }
    }

    private final CustomerRepository customers;
    private final UserRepository users;
    private final ActivityRepository activities;
    private final FollowUpRepository followUps;
    private final StatusHistoryRepository history;
    private final AccessGuard guard;
    private final AuditService audit;

    public CustomerService(CustomerRepository customers, UserRepository users, ActivityRepository activities,
                           FollowUpRepository followUps, StatusHistoryRepository history, AccessGuard guard,
                           AuditService audit) {
        this.customers = customers;
        this.users = users;
        this.activities = activities;
        this.followUps = followUps;
        this.history = history;
        this.guard = guard;
        this.audit = audit;
    }

    /** FR-11: search by company or contact name with an optional status, 10 per page, sorted by company name. */
    @Transactional(readOnly = true)
    public Page<Customer> search(AppUser me, String q, CustomerStatus status, int page) {
        String text = (q == null || q.isBlank()) ? null : q.trim();
        PageRequest pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by("companyName"));
        return customers.search(guard.scope(me), status, text, pageable);
    }

    @Transactional(readOnly = true)
    public List<Customer> listInScope(AppUser me) {
        return customers.findInScope(guard.scope(me));
    }

    /** FR-03 step 2: a customer only if it exists, is not deleted, and is in the user's scope. */
    @Transactional(readOnly = true)
    public Customer getAccessible(Long id, AppUser me) {
        Customer c = customers.findById(id).orElseThrow(() -> new NotFoundException("Customer not found"));
        guard.checkAccess(me, c);
        return c;
    }

    // ------------------------------------------------------------------ FR-12 duplicate names

    /**
     * Compares the name with every other non-deleted customer (all owners).
     * Exact = same name ignoring upper/lower case. Similar = one name contains the other,
     * ignoring upper/lower case, spaces and punctuation ("ABC Co." vs "ABC Company").
     */
    @Transactional(readOnly = true)
    public DuplicateCheck checkDuplicates(String companyName, Long excludeId) {
        String exactKey = exactKey(companyName);
        String key = similarKey(companyName);
        Customer exact = null;
        List<Customer> similar = new ArrayList<>();
        for (Customer c : customers.findByDeletedAtIsNull()) {
            if (c.getId().equals(excludeId)) continue;
            if (exactKey(c.getCompanyName()).equals(exactKey)) {
                exact = c;
                continue;
            }
            String other = similarKey(c.getCompanyName());
            if (key.length() < MIN_SIMILAR_LENGTH || other.length() < MIN_SIMILAR_LENGTH) continue;
            if (other.contains(key) || key.contains(other)) similar.add(c);
        }
        similar.sort(Comparator.comparing(Customer::getCompanyName, String.CASE_INSENSITIVE_ORDER));
        return new DuplicateCheck(exact, similar.size() > MAX_SIMILAR ? similar.subList(0, MAX_SIMILAR) : similar);
    }

    static String exactKey(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    /** Lower case with spaces and punctuation removed: "ABC Co." -> "abcco". */
    static String similarKey(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    public static String duplicateMessage(Customer exact) {
        return "A customer with this company name already exists (owner: " + exact.getOwner().getFullName() + ")";
    }

    private void blockExactDuplicate(String companyName, Long excludeId) {
        DuplicateCheck check = checkDuplicates(companyName, excludeId);
        if (check.blocked()) throw new BusinessException(duplicateMessage(check.exact()));
    }

    // ------------------------------------------------------------------ FR-02 create / update / delete

    /** New customers start as NEGOTIATING with a first status-history entry and an audit entry. */
    public Customer create(CustomerForm form, AppUser me) {
        blockExactDuplicate(form.getCompanyName(), null);
        Customer c = new Customer();
        apply(c, form);
        // Staff always own what they create; a Manager may choose any active user (default: self)
        c.setOwner(me.isManager() && form.getOwnerId() != null ? activeUser(form.getOwnerId()) : me);
        c.setCurrentStatus(CustomerStatus.NEGOTIATING);
        customers.save(c);
        writeHistory(c, null, CustomerStatus.NEGOTIATING, me, NEW_CUSTOMER_REASON);
        audit.created(me, AuditRecordType.CUSTOMER, c.getId(), c, AuditService.snapshot(c));
        return c;
    }

    /** Edits the master data only. The owner changes through FR-09 and the status through FR-06. */
    public Customer update(Long id, CustomerForm form, AppUser me) {
        Customer c = getAccessible(id, me);
        if (!exactKey(form.getCompanyName()).equals(exactKey(c.getCompanyName()))) {
            blockExactDuplicate(form.getCompanyName(), id);
        }
        Map<String, Object> before = AuditService.snapshot(c);
        apply(c, form);
        audit.updated(me, AuditRecordType.CUSTOMER, c.getId(), c, before, AuditService.snapshot(c));
        return c;
    }

    /** Soft delete: the customer and its activities and follow-ups disappear from every screen but stay in the database. */
    public Customer delete(Long id, AppUser me) {
        Customer c = getAccessible(id, me);
        c.setDeletedAt(LocalDateTime.now());
        audit.deleted(me, AuditRecordType.CUSTOMER, c.getId(), c, AuditService.snapshot(c));
        return c;
    }

    // ------------------------------------------------------------------ FR-06 status

    /**
     * Saves the status, the history entry and the audit entry in one transaction.
     * Returns false (and saves nothing) when the status is unchanged.
     */
    public boolean changeStatus(Long id, CustomerStatus newStatus, String reason, AppUser me) {
        Customer c = getAccessible(id, me);
        if (newStatus == null) throw new BusinessException("Choose a new status.");
        if (c.getCurrentStatus() == newStatus) return false;
        String why = reason == null ? "" : reason.trim();
        if (why.isEmpty()) throw new BusinessException("A reason is required to change the status.");
        if (why.length() > 255) throw new BusinessException("The reason can be up to 255 characters.");
        CustomerStatus previous = c.getCurrentStatus();
        Map<String, Object> before = AuditService.snapshot(c);
        c.setCurrentStatus(newStatus);
        writeHistory(c, previous, newStatus, me, why);
        audit.updated(me, AuditRecordType.CUSTOMER, c.getId(), c, before, AuditService.snapshot(c));
        return true;
    }

    // ------------------------------------------------------------------ FR-09 reassign

    /**
     * Moves the customer to another active user. In the same transaction, all of its open
     * (PENDING and OVERDUE) follow-ups move to the new owner; completed ones keep their assignee.
     * Returns the number of follow-ups moved.
     */
    public int reassign(Long id, Long newOwnerId, AppUser me) {
        guard.checkManager(me);
        Customer c = getAccessible(id, me);
        if (newOwnerId == null) throw new BusinessException("Choose the new owner.");
        AppUser newOwner = activeUser(newOwnerId);
        if (newOwner.getId().equals(c.getOwner().getId())) {
            throw new BusinessException(newOwner.getFullName() + " already owns this customer.");
        }
        Map<String, Object> before = AuditService.snapshot(c);
        c.setOwner(newOwner);
        audit.updated(me, AuditRecordType.CUSTOMER, c.getId(), c, before, AuditService.snapshot(c));
        List<FollowUp> open = followUps.findOpenByCustomer(c.getId());
        for (FollowUp f : open) {
            Map<String, Object> fBefore = AuditService.snapshot(f);
            f.setAssignedUser(newOwner);
            audit.updated(me, AuditRecordType.FOLLOW_UP, f.getId(), c, fBefore, AuditService.snapshot(f));
        }
        return open.size();
    }

    @Transactional(readOnly = true)
    public int openFollowUpCount(Long customerId) {
        return followUps.findOpenByCustomer(customerId).size();
    }

    // ------------------------------------------------------------------ helpers

    @Transactional(readOnly = true)
    public Map<CustomerStatus, Long> countByStatus(Long ownerId) {
        Map<CustomerStatus, Long> counts = new EnumMap<>(CustomerStatus.class);
        for (CustomerStatus s : CustomerStatus.values()) counts.put(s, 0L);
        for (Object[] row : customers.countByStatus(ownerId)) {
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
        c.setProjectBudget(f.getProjectBudget());
    }

    /** Owners must be active users (FR-02, FR-09). */
    private AppUser activeUser(Long id) {
        AppUser u = users.findById(id).orElseThrow(() -> new BusinessException("Selected user does not exist."));
        if (!u.isActive()) throw new BusinessException(u.getFullName() + " is not an active user. Choose an active user.");
        return u;
    }

    static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
