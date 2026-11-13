package com.customerpulse;

import com.customerpulse.domain.*;
import com.customerpulse.repository.*;
import com.customerpulse.service.*;
import com.customerpulse.web.form.ActivityForm;
import com.customerpulse.web.form.CustomerForm;
import com.customerpulse.web.form.FollowUpForm;
import com.customerpulse.web.form.UserForm;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Checks the SRS v1.1 business rules against the demo data. Each test rolls back. */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:rules;MODE=MySQL;DATABASE_TO_LOWER=TRUE", "app.seed-demo-data=true"})
@AutoConfigureMockMvc
@Transactional
class CustomerPulseApplicationTests {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CustomerRepository customers;
    @Autowired ActivityRepository activities;
    @Autowired FollowUpRepository followUps;
    @Autowired AuditLogRepository auditLog;
    @Autowired CustomerService customerService;
    @Autowired ActivityService activityService;
    @Autowired FollowUpService followUpService;
    @Autowired UserService userService;
    @Autowired DashboardService dashboardService;

    AppUser u(String email) { return users.findByEmailIgnoreCase(email).orElseThrow(); }

    Customer byName(String name) {
        return customers.findByDeletedAtIsNull().stream().filter(c -> c.getCompanyName().equals(name)).findFirst().orElseThrow();
    }

    List<AuditLog> auditFor(AuditRecordType type, Long id) {
        return auditLog.findByRecordTypeAndRecordIdOrderByIdAsc(type, id);
    }

    // ------------------------------------------------------------------ access (NFR-02)

    @Test
    void anonymousUserIsSentToLogin() throws Exception {
        mvc.perform(get("/dashboard")).andExpect(status().is3xxRedirection());
    }

    @Test
    void loginPageOffersManagerAndStaffDemoAccounts() throws Exception {
        mvc.perform(get("/login"))
           .andExpect(content().string(containsString("data-demo=\"priya@example.com\"")))
           .andExpect(content().string(containsString("data-demo=\"nadia@example.com\"")))
           .andExpect(content().string(containsString("data-demo=\"marcus@example.com\"")));
    }

    @Test
    @WithUserDetails("nadia@example.com")
    void phoneMustBeExactlyTenDigits() throws Exception {
        for (String bad : new String[]{"081234567", "08123456789", "081-234-5678", "+66812345678"}) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/customers")
                            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                            .param("companyName", "Phone Test Co").param("phone", bad))
               .andExpect(status().isOk())
               .andExpect(content().string(containsString("Enter a 10-digit phone number")));
        }
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/customers")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .param("companyName", "Phone Test Co").param("phone", "0812345678"))
           .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithUserDetails("nadia@example.com")
    void activityTypesCarryTheirDefaultChannel() throws Exception {
        mvc.perform(get("/activities/new"))
           .andExpect(content().string(containsString("value=\"CALL\" data-channel=\"PHONE\"")))
           .andExpect(content().string(containsString("value=\"MEETING\" data-channel=\"IN_PERSON\"")))
           .andExpect(content().string(containsString("value=\"CHAT\" data-channel=\"LINE\"")));
    }

    @Test
    @WithUserDetails("priya@example.com")
    void managerSeesEverything() throws Exception {
        mvc.perform(get("/customers/" + byName("Maple & Finch Retail").getId())).andExpect(status().isOk());
        mvc.perform(get("/overview")).andExpect(status().isOk());
        mvc.perform(get("/users")).andExpect(status().isOk());
        mvc.perform(get("/audit")).andExpect(status().isOk());
        // There are no team pages any more
        mvc.perform(get("/team")).andExpect(status().isNotFound());
    }

    @Test
    @WithUserDetails("nadia@example.com")
    void staffOnlySeesOwnCustomers() throws Exception {
        mvc.perform(get("/customers/" + byName("ABC Company Ltd.").getId())).andExpect(status().isOk());
        mvc.perform(get("/customers/" + byName("Maple & Finch Retail").getId())).andExpect(status().isForbidden());
        mvc.perform(get("/customers")).andExpect(content().string(not(containsString("Maple &amp; Finch Retail"))));
        mvc.perform(get("/users")).andExpect(status().isForbidden());
        mvc.perform(get("/overview")).andExpect(status().isForbidden());
        mvc.perform(get("/audit")).andExpect(status().isForbidden());
        mvc.perform(get("/activities/new")).andExpect(status().isOk());
    }

    @Test
    void staffCannotReachAnotherStaffMembersFollowUpById() {
        AppUser nadia = u("nadia@example.com");
        FollowUp marcusFollowUp = followUpService.forCustomer(byName("Siam Textile Group").getId()).get(0);
        assertThatThrownBy(() -> followUpService.complete(marcusFollowUp.getId(), nadia)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> followUpService.delete(marcusFollowUp.getId(), nadia)).isInstanceOf(AccessDeniedException.class);
    }

    // ------------------------------------------------------------------ FR-02 soft delete

    @Test
    void staffSoftDeletesOwnCustomerAndItDisappearsEverywhere() throws Exception {
        AppUser nadia = u("nadia@example.com");
        Customer blue = byName("Blue Harbor Logistics"); // has an overdue follow-up
        customerService.delete(blue.getId(), nadia);

        // Still in the database, with a deleted time
        Customer stored = customers.findById(blue.getId()).orElseThrow();
        assertThat(stored.getDeletedAt()).isNotNull();
        // Gone from lists, search and alerts
        assertThat(customerService.search(nadia, "Blue Harbor", null, 0).getTotalElements()).isZero();
        assertThat(dashboardService.alerts(nadia).overdue()).noneMatch(f -> f.getCustomer().getId().equals(blue.getId()));
        // Treated as "not found"
        assertThatThrownBy(() -> customerService.getAccessible(blue.getId(), nadia)).isInstanceOf(NotFoundException.class);
        // Audited
        assertThat(auditFor(AuditRecordType.CUSTOMER, blue.getId())).extracting(AuditLog::getAction).contains(AuditAction.DELETE);
        // Its name no longer counts as a duplicate
        assertThat(customerService.checkDuplicates("Blue Harbor Logistics", null).blocked()).isFalse();
    }

    @Test
    void staffCannotDeleteAnotherStaffMembersCustomer() {
        Customer maple = byName("Maple & Finch Retail");
        assertThatThrownBy(() -> customerService.delete(maple.getId(), u("nadia@example.com")))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ------------------------------------------------------------------ FR-12 duplicate names

    @Test
    void exactDuplicateIsBlockedAcrossAllOwnersAndSimilarNamesWarn() {
        // Nadia cannot see Marcus's "Siam Textile Group", but the exact name is still blocked
        CustomerForm form = new CustomerForm();
        form.setCompanyName("  siam TEXTILE group ");
        assertThatThrownBy(() -> customerService.create(form, u("nadia@example.com")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists (owner: Marcus Lindqvist)");

        // "ABC Co." vs "ABC Company Ltd.": similar once spaces and punctuation are ignored
        CustomerService.DuplicateCheck check = customerService.checkDuplicates("ABC Co.", null);
        assertThat(check.blocked()).isFalse();
        assertThat(check.similar()).extracting(Customer::getCompanyName).containsExactly("ABC Company Ltd.");

        // Names under 3 characters are only checked for exact matches
        assertThat(customerService.checkDuplicates("AB", null).similar()).isEmpty();
    }

    // ------------------------------------------------------------------ FR-04 activities

    @Test
    void ownerCanEditActivitiesLoggedByOthersAndDeleteKeepsFollowUpLinks() {
        AppUser nadia = u("nadia@example.com");
        Customer orion = byName("Orion Freight Co.");
        // Marcus logged this meeting on Nadia's customer
        Activity byMarcus = activityService.timeline(orion.getId()).stream()
                .filter(a -> a.getUser().getEmail().equals("marcus@example.com")).findFirst().orElseThrow();
        ActivityForm form = ActivityForm.from(byMarcus);
        form.setOutcome("Corrected by the owner");
        activityService.update(byMarcus.getId(), form, nadia);
        assertThat(activities.findById(byMarcus.getId()).orElseThrow().getUser().getEmail()).isEqualTo("marcus@example.com");
        assertThat(auditFor(AuditRecordType.ACTIVITY, byMarcus.getId()).get(0).getUser().getEmail()).isEqualTo("nadia@example.com");

        // Deleting an activity keeps the follow-ups created from it
        Customer abc = byName("ABC Company Ltd.");
        FollowUp linked = followUpService.forCustomer(abc.getId()).stream().filter(f -> f.getActivity() != null).findFirst().orElseThrow();
        Long activityId = linked.getActivity().getId();
        activityService.delete(activityId, nadia);
        assertThat(activityService.timeline(abc.getId())).noneMatch(a -> a.getId().equals(activityId));
        assertThat(followUps.findById(linked.getId()).orElseThrow().getActivity().getId()).isEqualTo(activityId);
    }

    @Test
    void futureActivityDateIsRejected() {
        ActivityForm form = new ActivityForm();
        form.setCustomerId(byName("ABC Company Ltd.").getId());
        form.setActivityType(ActivityType.CALL);
        form.setChannel(Channel.PHONE);
        form.setOutcome("Not yet");
        form.setActivityDate(LocalDateTime.now().plusHours(2));
        assertThatThrownBy(() -> activityService.log(form, u("nadia@example.com"))).isInstanceOf(BusinessException.class);
    }

    // ------------------------------------------------------------------ FR-05 follow-ups

    @Test
    void followUpRules() {
        AppUser priya = u("priya@example.com");
        Customer abc = byName("ABC Company Ltd.");

        // A Manager creates it, but it is assigned to the customer's owner
        FollowUpForm form = new FollowUpForm();
        form.setCustomerId(abc.getId());
        form.setDueDate(LocalDate.now());
        form.setNotes("Due today");
        FollowUp f = followUpService.create(form, priya);
        assertThat(f.getAssignedUser().getEmail()).isEqualTo("nadia@example.com");
        assertThat(f.getStatus()).isEqualTo(FollowUpStatus.PENDING);

        // A past due date is rejected when creating
        form.setDueDate(LocalDate.now().minusDays(1));
        assertThatThrownBy(() -> followUpService.create(form, priya)).isInstanceOf(BusinessException.class);

        // Due today is not overdue
        followUpService.refreshOverdue();
        assertThat(followUps.findById(f.getId()).orElseThrow().getStatus()).isEqualTo(FollowUpStatus.PENDING);

        // An OVERDUE follow-up: notes-only edit keeps the past date; a new date returns it to PENDING
        FollowUp overdue = followUpService.forCustomer(byName("Blue Harbor Logistics").getId()).stream()
                .filter(x -> x.getStatus() == FollowUpStatus.OVERDUE).findFirst().orElseThrow();
        LocalDate pastDate = overdue.getDueDate();
        FollowUpForm edit = FollowUpForm.from(overdue);
        edit.setNotes("Notes only");
        followUpService.update(overdue.getId(), edit, priya);
        assertThat(followUps.findById(overdue.getId()).orElseThrow().getDueDate()).isEqualTo(pastDate);
        assertThat(followUps.findById(overdue.getId()).orElseThrow().getStatus()).isEqualTo(FollowUpStatus.OVERDUE);
        edit.setDueDate(LocalDate.now().plusDays(2));
        followUpService.update(overdue.getId(), edit, priya);
        assertThat(followUps.findById(overdue.getId()).orElseThrow().getStatus()).isEqualTo(FollowUpStatus.PENDING);

        // Completed: completion time recorded, read-only, still deletable
        followUpService.complete(f.getId(), priya);
        assertThat(followUps.findById(f.getId()).orElseThrow().getCompletedAt()).isNotNull();
        assertThatThrownBy(() -> followUpService.update(f.getId(), FollowUpForm.from(f), priya)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> followUpService.complete(f.getId(), priya)).isInstanceOf(BusinessException.class);
        followUpService.delete(f.getId(), priya);
        assertThat(followUps.findById(f.getId()).orElseThrow().getDeletedAt()).isNotNull();
        assertThat(followUpService.forCustomer(abc.getId())).noneMatch(x -> x.getId().equals(f.getId()));
    }

    // ------------------------------------------------------------------ FR-07 stagnant

    @Test
    void onlyNegotiatingAndActiveCustomersCanBeStagnant() {
        List<DashboardService.StagnantItem> stagnant = dashboardService.stagnant(null);
        assertThat(stagnant).allMatch(s -> DashboardService.WATCHED.contains(s.customer().getCurrentStatus()));
        assertThat(stagnant).allMatch(s -> s.daysInactive() > DashboardService.STAGNANT_DAYS);
        // Northgate is INACTIVE with 41 quiet days: not flagged. Siam is NEGOTIATING with 16 quiet days: flagged.
        assertThat(stagnant).extracting(s -> s.customer().getCompanyName())
                .doesNotContain("Northgate Realty").contains("Siam Textile Group");
        // Most days inactive first
        for (int i = 1; i < stagnant.size(); i++) {
            assertThat(stagnant.get(i - 1).daysInactive()).isGreaterThanOrEqualTo(stagnant.get(i).daysInactive());
        }
    }

    // ------------------------------------------------------------------ FR-09 reassign

    @Test
    void reassignMovesAllOpenFollowUpsAndAuditsEach() {
        Customer siam = byName("Siam Textile Group"); // Marcus's, one open follow-up
        AppUser nadia = u("nadia@example.com");
        int moved = customerService.reassign(siam.getId(), nadia.getId(), u("priya@example.com"));
        assertThat(moved).isEqualTo(1);
        assertThat(followUpService.forCustomer(siam.getId())).filteredOn(FollowUp::isOpen)
                .allMatch(f -> f.getAssignedUser().getId().equals(nadia.getId()));
        assertThat(auditFor(AuditRecordType.CUSTOMER, siam.getId())).anyMatch(a -> a.getChanges().contains("owner_id"));
        // Staff cannot reassign
        assertThatThrownBy(() -> customerService.reassign(siam.getId(), u("marcus@example.com").getId(), nadia))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ------------------------------------------------------------------ FR-10 users

    @Test
    void userRules() {
        AppUser priya = u("priya@example.com");
        AppUser nadia = u("nadia@example.com");
        // Owns customers -> cannot be deactivated
        assertThatThrownBy(() -> userService.setActive(nadia.getId(), false, priya)).hasMessageContaining("still owns");
        // Cannot deactivate yourself
        assertThatThrownBy(() -> userService.setActive(priya.getId(), false, priya)).hasMessageContaining("your own account");

        // Last active Manager: another Manager cannot demote or deactivate them
        UserForm form = new UserForm();
        form.setFullName("Sam Lee");
        form.setEmail("sam@example.com");
        form.setPassword("welcome123");
        form.setRole(Role.MANAGER);
        AppUser sam = userService.create(form, priya);
        userService.setActive(sam.getId(), false, priya);           // allowed: Priya is still an active Manager
        assertThat(users.countByRoleAndActiveTrue(Role.MANAGER)).isEqualTo(1);
        UserForm demote = UserForm.from(priya);
        demote.setRole(Role.STAFF);
        assertThatThrownBy(() -> userService.update(priya.getId(), demote, sam)).hasMessageContaining("at least one active Manager");
        assertThatThrownBy(() -> userService.setActive(priya.getId(), false, sam)).hasMessageContaining("at least one active Manager");

        // A short password reset is refused; a valid one is audited only as "password changed"
        UserForm reset = UserForm.from(nadia);
        reset.setPassword("short");
        assertThatThrownBy(() -> userService.update(nadia.getId(), reset, priya)).hasMessageContaining("at least 8");
        reset.setPassword("longenough1");
        userService.update(nadia.getId(), reset, priya);
        List<AuditLog> entries = auditFor(AuditRecordType.USER, nadia.getId());
        AuditLog last = entries.get(entries.size() - 1);
        assertThat(last.getChanges()).contains(AuditService.PASSWORD_CHANGED).doesNotContain("longenough1").doesNotContain("$2a$");
    }

    // ------------------------------------------------------------------ FR-13 audit

    @Test
    void everyChangeByAUserIsAudited() {
        AppUser nadia = u("nadia@example.com");
        long before = auditLog.count();
        CustomerForm form = new CustomerForm();
        form.setCompanyName("Fresh Audit Co");
        Customer c = customerService.create(form, nadia);
        customerService.changeStatus(c.getId(), CustomerStatus.ACTIVE, "Contract signed", nadia);
        assertThat(auditLog.count()).isEqualTo(before + 2);
        List<AuditLog> entries = auditFor(AuditRecordType.CUSTOMER, c.getId());
        assertThat(entries.get(0).getAction()).isEqualTo(AuditAction.CREATE);
        assertThat(entries.get(1).getChanges()).isEqualTo("{\"current_status\":[\"NEGOTIATING\",\"ACTIVE\"]}");

        // The daily OVERDUE job is not audited
        long beforeJob = auditLog.count();
        followUpService.refreshOverdue();
        assertThat(auditLog.count()).isEqualTo(beforeJob);
    }
}
