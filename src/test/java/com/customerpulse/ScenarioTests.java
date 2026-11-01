package com.customerpulse;

import com.customerpulse.domain.*;
import com.customerpulse.repository.*;
import com.customerpulse.service.DashboardService;
import com.customerpulse.service.FollowUpService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The 8 scenarios from "Customer Pulse — Scenario-Based Use Cases", run in order on a clean start
 * (one team; only the first Manager exists). Each step of a scenario is a check below.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:scenarios;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "app.seed-demo-data=false"})
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ScenarioTests {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CustomerRepository customers;
    @Autowired ActivityRepository activities;
    @Autowired FollowUpRepository followUps;
    @Autowired StatusHistoryRepository history;
    @Autowired FollowUpService followUpService;
    @Autowired DashboardService dashboardService;
    @Autowired org.springframework.transaction.support.TransactionTemplate tx;

    /** Runs a check inside a transaction so linked records (owner, ...) can be read. */
    <T> T inTx(java.util.function.Supplier<T> work) { return tx.execute(st -> work.get()); }

    static Long abcId, xyzId, quoteFollowUpId;

    static RequestPostProcessor priya() { return user("priya@example.com").roles("MANAGER"); }
    static RequestPostProcessor nadia() { return user("nadia@example.com").roles("STAFF"); }
    static RequestPostProcessor marcus() { return user("marcus@example.com").roles("STAFF"); }

    AppUser u(String email) { return users.findByEmailIgnoreCase(email).orElseThrow(); }
    Customer customer(Long id) { return customers.findById(id).orElseThrow(); }

    @Test @Order(0)
    void setup_managerAddsStaff() throws Exception {
        for (String[] s : List.of(new String[]{"Nadia Osei", "nadia@example.com"}, new String[]{"Marcus Lindqvist", "marcus@example.com"})) {
            mvc.perform(post("/users").with(priya()).with(csrf())
                    .param("fullName", s[0]).param("email", s[1]).param("password", "welcome123").param("role", "STAFF"))
               .andExpect(redirectedUrl("/users"));
        }
        assertThat(u("nadia@example.com").getRole()).isEqualTo(Role.STAFF);
    }

    // ---------------------------------------------------------------------------------------------
    @Test @Order(1)
    void scenario1_registeringANewCustomer() throws Exception {
        // 1. Staff logs in with work email and password
        mvc.perform(formLogin("/login").userParameter("email").user("nadia@example.com").password("welcome123"))
           .andExpect(redirectedUrl("/dashboard"));
        // 2. Customers > Add Customer
        mvc.perform(get("/customers/new").with(nadia())).andExpect(status().isOk());
        // Alternative: empty company name is not saved
        mvc.perform(post("/customers").with(nadia()).with(csrf()).param("companyName", ""))
           .andExpect(status().isOk()).andExpect(content().string(containsString("Company name is required")));
        assertThat(customers.count()).isZero();
        // 3–5. Enter details and save
        String url = mvc.perform(post("/customers").with(nadia()).with(csrf())
                        .param("companyName", "ABC Company").param("contactName", "Somchai Boon")
                        .param("email", "somchai@abc.co.th").param("phone", "0812345678"))
                .andExpect(redirectedUrlPattern("/customers/*"))
                .andExpect(flash().attribute("success", containsString("created as Negotiating")))
                .andReturn().getResponse().getRedirectedUrl();
        abcId = Long.valueOf(url.substring(url.lastIndexOf('/') + 1));
        // 6. Initial status NEGOTIATING
        assertThat(customer(abcId).getCurrentStatus()).isEqualTo(CustomerStatus.NEGOTIATING);
        // 7. First history entry "New customer created"
        List<StatusHistory> h = history.findByCustomerIdOrderByChangedAtDescIdDesc(abcId);
        assertThat(h).hasSize(1);
        assertThat(h.get(0).getReason()).isEqualTo("New customer created");
        assertThat(h.get(0).getPreviousStatus()).isNull();
        // Alternative: an exact match (ignoring upper/lower case) is blocked
        mvc.perform(post("/customers").with(nadia()).with(csrf()).param("companyName", "abc company").param("confirmDuplicate", "true"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("A customer with this company name already exists (owner: Nadia Osei)")));
        // Alternative: a similar name warns and does not save until "Save anyway"
        mvc.perform(post("/customers").with(nadia()).with(csrf()).param("companyName", "ABC Co."))
           .andExpect(status().isOk()).andExpect(content().string(containsString("similar name already exists")));
        assertThat(customers.count()).isEqualTo(1);
        // Expected: appears in the Staff member's customer list
        mvc.perform(get("/customers").with(nadia())).andExpect(content().string(containsString("ABC Company")));
        assertThat(inTx(() -> customer(abcId).getOwner().getEmail())).isEqualTo("nadia@example.com");
    }

    // ---------------------------------------------------------------------------------------------
    @Test @Order(2)
    void scenario2_recordingAMeetingAndSchedulingAFollowUp() throws Exception {
        // 1–2. Open profile, Log Activity
        mvc.perform(get("/customers/" + abcId).with(nadia())).andExpect(status().isOk());
        mvc.perform(get("/activities/new").param("customerId", abcId.toString()).with(nadia())).andExpect(status().isOk());
        // Alternative: missing outcome and date show errors, nothing saved
        mvc.perform(post("/activities").with(nadia()).with(csrf())
                        .param("customerId", abcId.toString()).param("activityType", "MEETING").param("channel", "IN_PERSON")
                        .param("outcome", "").param("activityDate", ""))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("Outcome is required")))
           .andExpect(content().string(containsString("Date and time are required")));
        assertThat(activities.count()).isZero();
        // Alternative: a future date is rejected
        mvc.perform(post("/activities").with(nadia()).with(csrf())
                        .param("customerId", abcId.toString()).param("activityType", "MEETING").param("channel", "IN_PERSON")
                        .param("outcome", "Planned meeting").param("activityDate", LocalDateTime.now().plusDays(1).format(DT)))
           .andExpect(status().isOk()).andExpect(content().string(containsString("cannot be in the future")));
        assertThat(activities.count()).isZero();
        // 3–5. MEETING / IN_PERSON, date, outcome, save
        mvc.perform(post("/activities").with(nadia()).with(csrf())
                        .param("customerId", abcId.toString()).param("activityType", "MEETING").param("channel", "IN_PERSON")
                        .param("outcome", "Customer requested a revised quotation.")
                        .param("activityDate", LocalDateTime.now().format(DT)))
           // 6. Added to timeline and a follow-up is offered
           .andExpect(redirectedUrl("/customers/" + abcId))
           .andExpect(flash().attributeExists("offerFollowUpFor"));
        Activity meeting = activities.findByCustomerIdAndDeletedAtIsNullOrderByActivityDateDesc(abcId).get(0);
        assertThat(meeting.getActivityType()).isEqualTo(ActivityType.MEETING);
        assertThat(meeting.getUser().getEmail()).isEqualTo("nadia@example.com"); // user is fetched with the timeline
        mvc.perform(get("/customers/" + abcId).with(nadia()))
           .andExpect(content().string(containsString("Customer requested a revised quotation.")));
        // 7. Follow-up three days later
        mvc.perform(post("/followups").with(nadia()).with(csrf())
                        .param("customerId", abcId.toString()).param("activityId", meeting.getId().toString())
                        .param("dueDate", LocalDate.now().plusDays(3).toString())
                        .param("notes", "Send revised quotation to ABC Company"))
           .andExpect(redirectedUrl("/customers/" + abcId));
        // 8. Saved as PENDING and assigned to the customer's owner automatically
        FollowUp f = followUps.findByCustomerIdAndDeletedAtIsNullOrderByDueDateAsc(abcId).get(0);
        quoteFollowUpId = f.getId();
        assertThat(f.getStatus()).isEqualTo(FollowUpStatus.PENDING);
        assertThat(f.getAssignedUser().getEmail()).isEqualTo("nadia@example.com");
        assertThat(f.getActivity().getId()).isEqualTo(meeting.getId()); // activity is fetched with the list
    }

    // ---------------------------------------------------------------------------------------------
    @Test @Order(3)
    void scenario3_detectingAndCompletingAnOverdueFollowUp() throws Exception {
        // Time passes: the follow-up was due yesterday and was not done
        FollowUp f = followUps.findById(quoteFollowUpId).orElseThrow();
        f.setDueDate(LocalDate.now().minusDays(1));
        followUps.save(f);
        // 3. The daily job marks it OVERDUE (FR-05)
        followUpService.refreshOverdue();
        assertThat(followUps.findById(quoteFollowUpId).orElseThrow().getStatus()).isEqualTo(FollowUpStatus.OVERDUE);
        // 1–4. Dashboard shows it with customer and due date
        mvc.perform(get("/dashboard").with(nadia()))
           .andExpect(content().string(containsString("Send revised quotation to ABC Company")))
           .andExpect(content().string(containsString("1 day overdue")))
           .andExpect(content().string(containsString("ABC Company")));
        // 5. Open the customer from the alert
        mvc.perform(get("/customers/" + abcId).with(nadia())).andExpect(status().isOk());
        // 6–7. Complete it
        mvc.perform(post("/followups/" + quoteFollowUpId + "/complete").with(nadia()).with(csrf()))
           .andExpect(status().is3xxRedirection());
        FollowUp done = followUps.findById(quoteFollowUpId).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(FollowUpStatus.COMPLETED);
        assertThat(done.getCompletedAt()).isNotNull();
        // Alternative: nothing in scope -> "No alerts"
        mvc.perform(get("/dashboard").with(marcus())).andExpect(content().string(containsString("No alerts")));
    }

    // ---------------------------------------------------------------------------------------------
    @Test @Order(4)
    void scenario4_identifyingAStagnantCustomer() throws Exception {
        String url = mvc.perform(post("/customers").with(nadia()).with(csrf())
                        .param("companyName", "XYZ Company").param("contactName", "Ploy").param("email", "ploy@xyz.co"))
                .andReturn().getResponse().getRedirectedUrl();
        xyzId = Long.valueOf(url.substring(url.lastIndexOf('/') + 1));
        // Last contact was 18 days ago
        mvc.perform(post("/activities").with(nadia()).with(csrf())
                .param("customerId", xyzId.toString()).param("activityType", "CALL").param("channel", "PHONE")
                .param("outcome", "Discussed renewal timeline.")
                .param("activityDate", LocalDateTime.now().minusDays(18).format(DT)));
        // 1–3. Dashboard flags XYZ as stagnant (> 14 days)
        mvc.perform(get("/dashboard").with(nadia()))
           .andExpect(content().string(containsString("XYZ Company")))
           .andExpect(content().string(containsString("18 days inactive")));
        // Alternative: ABC had activity recently, so it is not stagnant
        assertThat(stagnantNames("nadia@example.com")).contains("XYZ Company").doesNotContain("ABC Company");
        // 4–5. Open profile: status, owner and timeline
        mvc.perform(get("/customers/" + xyzId).with(nadia()))
           .andExpect(content().string(containsString("Negotiating")))
           .andExpect(content().string(containsString("Nadia Osei")))
           .andExpect(content().string(containsString("Discussed renewal timeline.")));
        // 7. Log the new contact -> no longer stagnant
        mvc.perform(post("/activities").with(nadia()).with(csrf())
                .param("customerId", xyzId.toString()).param("activityType", "CALL").param("channel", "PHONE")
                .param("outcome", "Called Ploy to check in.").param("activityDate", LocalDateTime.now().format(DT)))
           .andExpect(redirectedUrl("/customers/" + xyzId));
        assertThat(stagnantNames("nadia@example.com")).doesNotContain("XYZ Company");
    }

    private List<String> stagnantNames(String email) {
        return inTx(() -> dashboardService.alerts(u(email)).stagnant().stream().map(s -> s.customer().getCompanyName()).toList());
    }

    // ---------------------------------------------------------------------------------------------
    @Test @Order(5)
    void scenario5_changingLifecycleStatus() throws Exception {
        String reason = "Customer accepted the proposal and confirmed the agreement.";
        // 1–5. NEGOTIATING -> ACTIVE with reason
        mvc.perform(post("/customers/" + abcId + "/status").with(nadia()).with(csrf())
                        .param("newStatus", "ACTIVE").param("reason", reason))
           .andExpect(flash().attribute("success", containsString("Active")));
        // 6. Profile shows ACTIVE
        assertThat(customer(abcId).getCurrentStatus()).isEqualTo(CustomerStatus.ACTIVE);
        // Expected: who, when, why are kept
        StatusHistory latest = history.findByCustomerIdOrderByChangedAtDescIdDesc(abcId).get(0);
        assertThat(latest.getPreviousStatus()).isEqualTo(CustomerStatus.NEGOTIATING);
        assertThat(latest.getNewStatus()).isEqualTo(CustomerStatus.ACTIVE);
        assertThat(latest.getChangedBy().getEmail()).isEqualTo("nadia@example.com"); // fetched with the history
        assertThat(latest.getChangedAt()).isNotNull();
        assertThat(latest.getReason()).isEqualTo(reason);
        // Alternative: same status again -> message, nothing saved
        int before = history.findByCustomerIdOrderByChangedAtDescIdDesc(abcId).size();
        mvc.perform(post("/customers/" + abcId + "/status").with(nadia()).with(csrf()).param("newStatus", "ACTIVE"))
           .andExpect(flash().attribute("info", containsString("already Active")));
        // Alternative: a blank reason shows an error and saves nothing
        mvc.perform(post("/customers/" + abcId + "/status").with(nadia()).with(csrf()).param("newStatus", "HOLD").param("reason", "  "))
           .andExpect(flash().attribute("error", containsString("reason is required")));
        assertThat(history.findByCustomerIdOrderByChangedAtDescIdDesc(abcId)).hasSize(before);
        assertThat(customer(abcId).getCurrentStatus()).isEqualTo(CustomerStatus.ACTIVE);
        // 7. Manager sees it in the Customer Overview
        mvc.perform(get("/overview").with(priya())).andExpect(content().string(containsString(reason)));
    }

    // ---------------------------------------------------------------------------------------------
    @Test @Order(6)
    void scenario6_reassigningToAnotherStaffMember() throws Exception {
        // Nadia still has an open follow-up on ABC
        mvc.perform(post("/followups").with(nadia()).with(csrf())
                .param("customerId", abcId.toString())
                .param("dueDate", LocalDate.now().plusDays(5).toString()).param("notes", "Send signed contract copy"));
        // 1–2. Search ABC, open profile (the dialog shows how many open follow-ups will move)
        mvc.perform(get("/customers").param("q", "ABC").with(priya()))
           .andExpect(content().string(containsString("ABC Company")));
        mvc.perform(get("/customers/" + abcId).with(priya()))
           .andExpect(content().string(containsString("1 open follow-up will be transferred")));
        mvc.perform(get("/customers/" + abcId).with(priya())).andExpect(status().isOk());

        // Alternative: replacement is not an active user -> refused
        mvc.perform(post("/users").with(priya()).with(csrf()).param("fullName", "Kim Tran")
                .param("email", "kim@example.com").param("password", "welcome123").param("role", "STAFF"));
        Long kimId = u("kim@example.com").getId();
        mvc.perform(post("/users/" + kimId + "/deactivate").with(priya()).with(csrf()));
        assertThat(u("kim@example.com").isActive()).isFalse();
        mvc.perform(post("/customers/" + abcId + "/owner").with(priya()).with(csrf()).param("ownerId", kimId.toString()))
           .andExpect(flash().attribute("error", containsString("not an active user")));
        assertThat(inTx(() -> customer(abcId).getOwner().getEmail())).isEqualTo("nadia@example.com");
        // An inactive user cannot log in either
        mvc.perform(formLogin("/login").userParameter("email").user("kim@example.com").password("welcome123"))
           .andExpect(redirectedUrl("/login?error"));

        // 3–5. Reassign to Marcus (active); open follow-ups always move with the customer
        Long marcusId = u("marcus@example.com").getId();
        mvc.perform(post("/customers/" + abcId + "/owner").with(priya()).with(csrf())
                        .param("ownerId", marcusId.toString()))
           .andExpect(flash().attribute("success", containsString("Marcus Lindqvist")))
           .andExpect(flash().attribute("success", containsString("1 open follow-up moved")));
        assertThat(inTx(() -> customer(abcId).getOwner().getId())).isEqualTo(marcusId);
        // 6. Appears in the new owner's list
        mvc.perform(get("/customers").with(marcus())).andExpect(content().string(containsString("ABC Company")));
        // 7. New owner sees earlier activities and follow-ups
        mvc.perform(get("/customers/" + abcId).with(marcus()))
           .andExpect(content().string(containsString("Customer requested a revised quotation.")))
           .andExpect(content().string(containsString("Send revised quotation to ABC Company")))
           .andExpect(content().string(containsString("Send signed contract copy")));
        List<FollowUp> fus = followUps.findByCustomerIdAndDeletedAtIsNullOrderByDueDateAsc(abcId);
        assertThat(fus).filteredOn(FollowUp::isOpen).isNotEmpty().allMatch(x -> x.getAssignedUser().getId().equals(marcusId));
        assertThat(fus).filteredOn(x -> !x.isOpen()).isNotEmpty().allMatch(x -> x.getAssignedUser().getEmail().equals("nadia@example.com"));
        // The previous owner no longer has access
        mvc.perform(get("/customers/" + abcId).with(nadia())).andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------------------------------------
    @Test @Order(7)
    void scenario7_preventingUnauthorizedAccess() throws Exception {
        // 1. Login works; a wrong password gives the generic error
        mvc.perform(formLogin("/login").userParameter("email").user("marcus@example.com").password("wrong"))
           .andExpect(redirectedUrl("/login?error"));
        mvc.perform(formLogin("/login").userParameter("email").user("marcus@example.com").password("welcome123"))
           .andExpect(redirectedUrl("/dashboard"));
        // 2–5. Direct URL to Nadia's customer is refused and nothing is exposed
        mvc.perform(get("/customers/" + xyzId).with(marcus()))
           .andExpect(status().isForbidden())
           .andExpect(content().string(not(containsString("ploy@xyz.co"))));
        mvc.perform(get("/customers/" + xyzId + "/edit").with(marcus())).andExpect(status().isForbidden());
        mvc.perform(post("/customers/" + xyzId + "/status").with(marcus()).with(csrf()).param("newStatus", "LOST"))
           .andExpect(status().isForbidden());
        assertThat(customer(xyzId).getCurrentStatus()).isEqualTo(CustomerStatus.NEGOTIATING);
        // ...including activities and follow-ups opened by their ID
        Long xyzActivity = activities.findByCustomerIdAndDeletedAtIsNullOrderByActivityDateDesc(xyzId).get(0).getId();
        mvc.perform(get("/activities/" + xyzActivity + "/edit").with(marcus())).andExpect(status().isForbidden());
        mvc.perform(post("/activities/" + xyzActivity + "/delete").with(marcus()).with(csrf())).andExpect(status().isForbidden());
        // Manager-only pages are refused too
        mvc.perform(get("/overview").with(marcus())).andExpect(status().isForbidden());
        mvc.perform(get("/audit").with(marcus())).andExpect(status().isForbidden());
        mvc.perform(get("/users").with(marcus())).andExpect(status().isForbidden());
        // Alternative: the Manager is allowed
        mvc.perform(get("/customers/" + xyzId).with(priya())).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------------------------------------
    @Test @Order(8)
    void scenario8_monitoringCustomerStatuses() throws Exception {
        // 1–2. Counts by status and the customer list
        mvc.perform(get("/overview").with(priya()))
           .andExpect(model().attribute("total", 2L))
           .andExpect(model().attribute("counts", hasEntry(CustomerStatus.ACTIVE, 1L)))
           .andExpect(model().attribute("counts", hasEntry(CustomerStatus.NEGOTIATING, 1L)))
           .andExpect(model().attribute("customers", hasSize(2)));
        // 3. Owner and status filter the customer list; the date range filters changes and activities
        mvc.perform(get("/overview").param("ownerId", u("marcus@example.com").getId().toString()).with(priya()))
           .andExpect(model().attribute("customers", hasSize(1)))
           .andExpect(content().string(containsString("ABC Company")));
        mvc.perform(get("/overview").param("status", "NEGOTIATING").with(priya()))
           .andExpect(model().attribute("customers", hasSize(1)))
           .andExpect(content().string(containsString("XYZ Company")));
        mvc.perform(get("/overview").param("from", LocalDate.now().toString()).param("to", LocalDate.now().toString()).with(priya()))
           .andExpect(model().attribute("transitions", not(empty())))
           .andExpect(content().string(containsString("Called Ploy to check in.")));
        // Alternative: no match -> empty result, not unrelated records
        mvc.perform(get("/overview").param("status", "LOST").with(priya()))
           .andExpect(model().attribute("customers", empty()))
           .andExpect(content().string(containsString("No results found")));
        mvc.perform(get("/overview").param("from", "2030-01-01").with(priya()))
           .andExpect(content().string(containsString("No status changes in this period.")))
           .andExpect(content().string(containsString("No activity in this period.")));
        // 4. Open a customer: status history and timeline
        mvc.perform(get("/customers/" + abcId).with(priya()))
           .andExpect(content().string(containsString("Status history")))
           .andExpect(content().string(containsString("Customer accepted the proposal")));
        // 6. Reassignment is available from the profile
        mvc.perform(get("/customers/" + xyzId).with(priya())).andExpect(content().string(containsString("Reassign owner")));
    }
}
