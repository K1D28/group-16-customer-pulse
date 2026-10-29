package com.customerpulse.service;

import com.customerpulse.domain.*;
import com.customerpulse.repository.*;
import com.customerpulse.web.form.CaseForm;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Cases: a Manager assigns employees to a piece of work on a customer.
 * Each assignee marks their part done; when everyone is done the case closes itself.
 */
@Service
@Transactional
public class CaseService {

    public static final Set<CaseStatus> ACTIVE = EnumSet.of(CaseStatus.OPEN, CaseStatus.IN_PROGRESS);

    /** Done / total for one case. */
    public record Progress(int done, int total) {
        public int percent() { return total == 0 ? 0 : Math.round(done * 100f / total); }
    }

    private final CaseRepository cases;
    private final CaseAssignmentRepository assignments;
    private final CustomerRepository customers;
    private final UserRepository users;
    private final ActivityRepository activities;
    private final AccessGuard guard;

    public CaseService(CaseRepository cases, CaseAssignmentRepository assignments, CustomerRepository customers,
                       UserRepository users, ActivityRepository activities, AccessGuard guard) {
        this.cases = cases;
        this.assignments = assignments;
        this.customers = customers;
        this.users = users;
        this.activities = activities;
        this.guard = guard;
    }

    // ---------- reading ----------

    @Transactional(readOnly = true)
    public List<ServiceCase> list(AppUser me, Set<CaseStatus> statuses) {
        return cases.findInScope(me.isManager() ? null : me.getId(), guard.team(me), statuses);
    }

    @Transactional(readOnly = true)
    public Map<Long, List<CaseAssignment>> assignmentsFor(Collection<ServiceCase> list) {
        Map<Long, List<CaseAssignment>> map = new HashMap<>();
        if (list.isEmpty()) return map;
        List<Long> ids = list.stream().map(ServiceCase::getId).toList();
        for (CaseAssignment a : assignments.findByServiceCaseIdIn(ids)) {
            map.computeIfAbsent(a.getServiceCase().getId(), k -> new ArrayList<>()).add(a);
        }
        map.values().forEach(l -> l.sort(Comparator.comparing(CaseAssignment::getId)));
        return map;
    }

    @Transactional(readOnly = true)
    public List<CaseAssignment> assignments(Long caseId) {
        return assignments.findByServiceCaseIdOrderByIdAsc(caseId);
    }

    @Transactional(readOnly = true)
    public List<CaseAssignment> myOpenWork(AppUser me) {
        return assignments.findOpenForUser(me.getId());
    }

    @Transactional(readOnly = true)
    public List<ServiceCase> forCustomer(Long customerId) {
        return cases.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    public static Progress progress(List<CaseAssignment> list) {
        int done = (int) list.stream().filter(CaseAssignment::isDone).count();
        return new Progress(done, list.size());
    }

    /** Managers, assignees, and the customer's owner can open a case. */
    @Transactional(readOnly = true)
    public ServiceCase getViewable(Long id, AppUser me) {
        ServiceCase c = cases.findById(id).orElseThrow(() -> new NotFoundException("Case not found"));
        if (isAssignee(c, me) || guard.canAccess(me, c.getCustomer())) return c;
        throw new AccessDeniedException("You are not assigned to this case.");
    }

    @Transactional(readOnly = true)
    public boolean isAssignee(ServiceCase c, AppUser me) {
        return assignments.existsByServiceCaseIdAndUserId(c.getId(), me.getId());
    }

    /** Activities can be added while the case is open, by Managers and assignees. */
    @Transactional(readOnly = true)
    public boolean canLog(ServiceCase c, AppUser me) {
        return !c.isClosed() && (me.isManager() || isAssignee(c, me));
    }

    // ---------- writing (Manager) ----------

    public ServiceCase create(CaseForm form, AppUser me) {
        guard.checkManager(me);
        ServiceCase c = new ServiceCase();
        apply(c, form, me);
        c.setCreatedBy(me);
        c.setStatus(CaseStatus.OPEN);
        cases.save(c);
        for (Long uid : new LinkedHashSet<>(form.getAssigneeIds())) addAssignee(c, loadUser(uid, me));
        return c;
    }

    public ServiceCase update(Long id, CaseForm form, AppUser me) {
        guard.checkManager(me);
        ServiceCase c = getViewable(id, me);
        apply(c, form, me);

        Set<Long> wanted = new LinkedHashSet<>(form.getAssigneeIds());
        List<CaseAssignment> current = assignments.findByServiceCaseIdOrderByIdAsc(id);
        for (CaseAssignment a : current) {
            if (!wanted.remove(a.getUser().getId())) assignments.delete(a);
        }
        boolean added = !wanted.isEmpty();
        for (Long uid : wanted) addAssignee(c, loadUser(uid, me));
        assignments.flush();

        // Someone new joined a closed case: it needs work again
        if (added && c.isClosed()) {
            c.setClosedAt(null);
            c.setClosedBy(null);
            c.setStatus(CaseStatus.IN_PROGRESS);
        }
        recompute(c, me);
        return c;
    }

    public void close(Long id, AppUser me) {
        guard.checkManager(me);
        ServiceCase c = getViewable(id, me);
        if (c.isClosed()) return;
        markClosed(c, me);
    }

    public void reopen(Long id, AppUser me) {
        guard.checkManager(me);
        ServiceCase c = getViewable(id, me);
        if (!c.isClosed()) return;
        // Everyone has to confirm again, otherwise the case would close straight away
        for (CaseAssignment a : assignments.findByServiceCaseIdOrderByIdAsc(id)) {
            a.setStatus(AssignmentStatus.ASSIGNED);
            a.setDoneAt(null);
        }
        c.setClosedAt(null);
        c.setClosedBy(null);
        c.setStatus(CaseStatus.IN_PROGRESS);
    }

    /** Activities are kept on the customer timeline; only the link to the case is removed. */
    public void delete(Long id, AppUser me) {
        guard.checkManager(me);
        ServiceCase c = getViewable(id, me);
        activities.detachCase(c.getId());
        assignments.deleteByCaseId(c.getId());
        cases.deleteById(c.getId());
    }

    // ---------- writing (assignee) ----------

    public ServiceCase markDone(Long id, String note, AppUser me) {
        ServiceCase c = getViewable(id, me);
        if (c.isClosed()) throw new BusinessException("This case is already closed.");
        CaseAssignment a = assignments.findByServiceCaseIdAndUserId(id, me.getId())
                .orElseThrow(() -> new BusinessException("You are not assigned to this case."));
        if (note != null && note.length() > 255) throw new BusinessException("Note can be up to 255 characters.");
        a.setStatus(AssignmentStatus.DONE);
        a.setDoneAt(LocalDateTime.now());
        a.setDoneNote(CustomerService.blankToNull(note));
        assignments.flush();
        recompute(c, me);
        return c;
    }

    public ServiceCase undoDone(Long id, AppUser me) {
        ServiceCase c = getViewable(id, me);
        CaseAssignment a = assignments.findByServiceCaseIdAndUserId(id, me.getId())
                .orElseThrow(() -> new BusinessException("You are not assigned to this case."));
        a.setStatus(AssignmentStatus.ASSIGNED);
        a.setDoneAt(null);
        if (c.isClosed()) {
            c.setClosedAt(null);
            c.setClosedBy(null);
            c.setStatus(CaseStatus.IN_PROGRESS);
        }
        assignments.flush();
        recompute(c, me);
        return c;
    }

    /** Called after an activity is logged on the case. */
    public void onActivityLogged(ServiceCase c) {
        if (c.getStatus() == CaseStatus.OPEN) c.setStatus(CaseStatus.IN_PROGRESS);
    }

    // ---------- helpers ----------

    /** Open → In progress once work starts; Closed once every assignee is done. */
    private void recompute(ServiceCase c, AppUser by) {
        if (c.isClosed()) return;
        Progress p = progress(assignments.findByServiceCaseIdOrderByIdAsc(c.getId()));
        if (p.total() > 0 && p.done() == p.total()) {
            markClosed(c, by);
        } else if (p.done() > 0 || activities.countByServiceCaseId(c.getId()) > 0) {
            c.setStatus(CaseStatus.IN_PROGRESS);
        } else {
            c.setStatus(CaseStatus.OPEN);
        }
    }

    private void markClosed(ServiceCase c, AppUser by) {
        c.setStatus(CaseStatus.CLOSED);
        c.setClosedAt(LocalDateTime.now());
        c.setClosedBy(by);
    }

    private void addAssignee(ServiceCase c, AppUser u) {
        CaseAssignment a = new CaseAssignment();
        a.setServiceCase(c);
        a.setUser(u);
        assignments.save(a);
    }

    private void apply(ServiceCase c, CaseForm f, AppUser me) {
        c.setTitle(f.getTitle().trim());
        c.setDescription(CustomerService.blankToNull(f.getDescription()));
        c.setPriority(f.getPriority());
        c.setDueDate(f.getDueDate());
        if (c.getId() == null || !c.getCustomer().getId().equals(f.getCustomerId())) {
            Customer customer = customers.findById(f.getCustomerId())
                    .orElseThrow(() -> new BusinessException("Selected customer does not exist."));
            guard.checkAccess(me, customer);
            c.setCustomer(customer);
        }
    }

    private AppUser loadUser(Long id, AppUser me) {
        AppUser u = users.findById(id).orElseThrow(() -> new BusinessException("Selected employee does not exist."));
        if (!u.sameTeam(me)) throw new BusinessException("You can only assign people in your own team.");
        if (!u.isActive()) throw new BusinessException(u.getFullName() + " is not an active user.");
        return u;
    }
}
