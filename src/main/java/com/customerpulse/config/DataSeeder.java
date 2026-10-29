package com.customerpulse.config;

import com.customerpulse.domain.*;
import com.customerpulse.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Optional sample data for Team A (2 staff, customers, activities, follow-ups, cases).
 * Off by default; turn on with app.seed-demo-data=true. The automated tests use it.
 */
@Component
@Order(2)
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    public static final String DEMO_PASSWORD = "password123";

    private final UserRepository users;
    private final CustomerRepository customers;
    private final ActivityRepository activities;
    private final FollowUpRepository followUps;
    private final StatusHistoryRepository history;
    private final CaseRepository cases;
    private final CaseAssignmentRepository caseAssignments;
    private final PasswordEncoder encoder;

    private final Map<String, Customer> byName = new HashMap<>();
    private final LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);

    public DataSeeder(UserRepository users, CustomerRepository customers, ActivityRepository activities,
                      FollowUpRepository followUps, StatusHistoryRepository history, CaseRepository cases,
                      CaseAssignmentRepository caseAssignments, PasswordEncoder encoder) {
        this.cases = cases;
        this.caseAssignments = caseAssignments;
        this.users = users;
        this.customers = customers;
        this.activities = activities;
        this.followUps = followUps;
        this.history = history;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (customers.count() == 0 && users.findByEmailIgnoreCase("priya@example.com").isPresent()) seedBase();
        if (cases.count() == 0) seedCases();
    }

    private void seedBase() {
        AppUser priya = users.findByEmailIgnoreCase("priya@example.com").orElseThrow();
        AppUser nadia = user("Nadia Osei", "nadia@example.com", Role.STAFF, priya.getTeam());
        AppUser marcus = user("Marcus Lindqvist", "marcus@example.com", Role.STAFF, priya.getTeam());

        customer("ABC Company Ltd.", "Suchada Boon", "suchada@abc-th.com", "+66 89 123 4501", CustomerStatus.HOLD, nadia, 41, priya, "Budget under approval until next month");
        customer("Orion Freight Co.", "Wichai Meesuk", "wichai@orionfr.co", "+66 81 553 2290", CustomerStatus.ACTIVE, nadia, 43, nadia, "Contract signed");
        customer("Blue Harbor Logistics", "Kanya Srisuk", "kanya@bluehbr.com", "+66 92 771 0034", CustomerStatus.NEGOTIATING, nadia, 49, null, null);
        customer("Maple & Finch Retail", "David Okoye", "david@maplefinch.com", "+66 63 220 9981", CustomerStatus.ACTIVE, marcus, 42, marcus, "First order delivered");
        customer("Siam Textile Group", "Ploy Anantasin", "ploy@siamtextile.co", "+66 85 040 7712", CustomerStatus.NEGOTIATING, marcus, 54, null, null);
        customer("Northgate Realty", "Tom Vasquez", "tom@northgate.com", "+66 87 662 1150", CustomerStatus.INACTIVE, marcus, 81, priya, "No reply for 5 weeks");
        customer("Lotus Fresh Foods", "Arisa Chan", "arisa@lotusfresh.co", "+66 90 331 8807", CustomerStatus.ACTIVE, nadia, 46, nadia, "Standing weekly order agreed");
        customer("Riverside Print Co.", "Somchai Petch", "somchai@riverprint.com", "+66 84 118 2266", CustomerStatus.LOST, marcus, 90, priya, "Did not renew the contract");
        customer("Nimbus Cloud Systems", "Grace Lin", "grace@nimbuscloud.io", "+66 61 900 4432", CustomerStatus.NEGOTIATING, nadia, 40, null, null);
        customer("Golden Teak Furniture", "Anurak Sae", "anurak@goldenteak.co", "+66 88 245 6690", CustomerStatus.HOLD, marcus, 60, marcus, "Waiting on payment terms");
        customer("Everline Insurance", "Natcha Ruang", "natcha@everline.co", "+66 62 774 3321", CustomerStatus.ACTIVE, nadia, 52, nadia, "Policy bought");
        customer("Coral Bay Hotels", "James Whitfield", "james@coralbay.com", "+66 89 556 0021", CustomerStatus.NEGOTIATING, marcus, 45, null, null);
        customer("Pinnacle Steelworks", "Ravi Kumar", "ravi@pinnaclesteel.co", "+66 91 480 2287", CustomerStatus.INACTIVE, nadia, 78, priya, "Invoice unpaid, contact stopped");
        customer("Sunview Solar Co.", "Malee Thongdee", "malee@sunviewsolar.co", "+66 80 112 9945", CustomerStatus.ACTIVE, marcus, 48, marcus, "Installation approved");
        customer("Kinnaree Spa & Wellness", "Pim Jaidee", "pim@kinnareespa.co", "+66 82 300 4411", CustomerStatus.NEGOTIATING, nadia, 3, null, null);

        Activity abcProposal = act("ABC Company Ltd.", ActivityType.EMAIL, Channel.EMAIL, "Sent revised proposal v3 with updated pricing tiers for the annual logistics contract.", 6, nadia);
        act("ABC Company Ltd.", ActivityType.CHAT, Channel.LINE, "Client replied: \"Budget is under approval. Please contact me next month.\"", 1, nadia);
        act("Orion Freight Co.", ActivityType.CALL, Channel.PHONE, "Quarterly check-in. Discussed Q4 shipment volume increase and warehousing needs.", 3, nadia);
        act("Orion Freight Co.", ActivityType.MEETING, Channel.IN_PERSON, "On-site review of the new fleet-tracking dashboard with the ops manager.", 10, marcus);
        Activity blueQuote = act("Blue Harbor Logistics", ActivityType.QUOTATION, Channel.EMAIL, "Sent quotation #Q-2291 for the Bangkok–Chiang Mai cold-chain lane.", 9, nadia);
        act("Maple & Finch Retail", ActivityType.CHAT, Channel.LINE, "Reorder of the Q3 SKU set confirmed; delivery next Tuesday.", 2, marcus);
        act("Maple & Finch Retail", ActivityType.CALL, Channel.PHONE, "Handled late-delivery complaint and issued a partial credit.", 7, marcus);
        Activity siamMeeting = act("Siam Textile Group", ActivityType.MEETING, Channel.IN_PERSON, "Reviewed renewal terms; client is comparing vendors.", 16, marcus);
        act("Northgate Realty", ActivityType.EMAIL, Channel.EMAIL, "Check-in email after 5 weeks of silence. No response yet.", 41, marcus);
        act("Lotus Fresh Foods", ActivityType.CALL, Channel.PHONE, "Confirmed standing weekly order and added 2 new SKUs.", 6, nadia);
        act("Lotus Fresh Foods", ActivityType.CHAT, Channel.LINE, "Client asked for an earlier delivery window starting next week.", 1, nadia);
        act("Riverside Print Co.", ActivityType.NOTE, Channel.OTHER, "Account marked lost after non-renewal. Contract archived.", 50, marcus);
        act("Nimbus Cloud Systems", ActivityType.MEETING, Channel.ONLINE_MEETING, "Discovery call about cloud migration for a 40-seat team.", 5, nadia);
        Activity nimbusPricing = act("Nimbus Cloud Systems", ActivityType.QUOTATION, Channel.EMAIL, "Shared tiered SaaS pricing sheet; awaiting their internal review.", 0, nadia);
        act("Golden Teak Furniture", ActivityType.CALL, Channel.PHONE, "Discussed extending payment terms to Net-45.", 20, marcus);
        act("Everline Insurance", ActivityType.CHAT, Channel.LINE, "Sent renewal reminder with the updated premium quote.", 12, nadia);
        act("Coral Bay Hotels", ActivityType.QUOTATION, Channel.EMAIL, "Group-booking proposal for the Dec–Jan season sent.", 5, marcus);
        act("Pinnacle Steelworks", ActivityType.CALL, Channel.PHONE, "Called twice and left voicemail about the overdue invoice. No answer.", 38, nadia);
        act("Sunview Solar Co.", ActivityType.MEETING, Channel.IN_PERSON, "Completed rooftop survey for a 50 kW solar installation.", 8, marcus);
        act("Sunview Solar Co.", ActivityType.EMAIL, Channel.EMAIL, "Shared installation timeline and permit checklist.", 2, marcus);

        followUp("ABC Company Ltd.", abcProposal, nadia, 22, "Call to confirm budget approval", false);
        followUp("Blue Harbor Logistics", blueQuote, nadia, -2, "Follow up on cold-chain quotation", false);
        followUp("Siam Textile Group", siamMeeting, marcus, -5, "Send comparison sheet vs. competitor", false);
        followUp("Northgate Realty", null, marcus, -1, "Re-engagement call before churn review", false);
        followUp("Nimbus Cloud Systems", nimbusPricing, nadia, 3, "Check in after internal pricing review", false);
        followUp("Golden Teak Furniture", null, marcus, 1, "Confirm Net-45 terms in writing", false);
        followUp("Everline Insurance", null, nadia, -3, "Finalize policy renewal paperwork", false);
        followUp("Coral Bay Hotels", null, marcus, 4, "Send updated group rate for January dates", false);
        followUp("Pinnacle Steelworks", null, nadia, -6, "Escalate overdue invoice to manager", false);
        followUp("Orion Freight Co.", null, nadia, -1, "Schedule next quarterly review call", true);
        followUp("Maple & Finch Retail", null, marcus, -2, "Confirm delivery credit was applied", true);
        followUp("Sunview Solar Co.", null, marcus, 5, "Share final inspection report", false);

        log.info("Demo data loaded into {}: staff nadia@example.com and marcus@example.com, password: {}", priya.getTeam().getName(), DEMO_PASSWORD);
    }

    /** Three example cases: one open, one in progress, one closed. */
    private void seedCases() {
        AppUser priya = users.findByEmailIgnoreCase("priya@example.com").orElse(null);
        AppUser nadia = users.findByEmailIgnoreCase("nadia@example.com").orElse(null);
        AppUser marcus = users.findByEmailIgnoreCase("marcus@example.com").orElse(null);
        if (priya == null || nadia == null || marcus == null) return;
        for (Customer c : customers.findAll()) byName.putIfAbsent(c.getCompanyName(), c);
        if (!byName.containsKey("Siam Textile Group") || !byName.containsKey("Pinnacle Steelworks")
                || !byName.containsKey("Sunview Solar Co.")) return;

        // In progress: Marcus is done, Nadia still working
        ServiceCase renewal = serviceCase("Win the Siam Textile renewal", "Siam Textile Group", CasePriority.HIGH, 7, priya,
                CaseStatus.IN_PROGRESS, 4,
                "They are comparing us with two other vendors.\nMarcus: prepare the price comparison sheet.\nNadia: set up a call with their logistics lead about delivery times.");
        assign(renewal, marcus, true, 1, "Comparison sheet sent to Ploy");
        assign(renewal, nadia, false, 0, null);
        caseAct(renewal, marcus, ActivityType.EMAIL, Channel.EMAIL, "Sent price comparison sheet against both competing vendors.", 1);
        caseAct(renewal, nadia, ActivityType.CALL, Channel.PHONE, "Called their logistics lead; meeting booked for Friday to walk through delivery times.", 0);

        // Open: nobody has started yet
        ServiceCase invoice = serviceCase("Recover the overdue Pinnacle invoice", "Pinnacle Steelworks", CasePriority.MEDIUM, 10, priya,
                CaseStatus.OPEN, 1, "Invoice INV-2207 is 45 days late. Find out why and agree a payment date.");
        assign(invoice, nadia, false, 0, null);

        // Closed: everyone finished
        ServiceCase install = serviceCase("Hand over the Sunview solar install", "Sunview Solar Co.", CasePriority.LOW, -3, priya,
                CaseStatus.CLOSED, 12, "Make sure the customer has the inspection report and maintenance plan.");
        assign(install, marcus, true, 4, "Inspection report delivered");
        assign(install, nadia, true, 3, "Maintenance plan signed");
        install.setClosedAt(now.minusDays(3));
        install.setClosedBy(nadia);
        caseAct(install, marcus, ActivityType.MEETING, Channel.IN_PERSON, "Walked the client through the final inspection report on site.", 4);
        caseAct(install, nadia, ActivityType.EMAIL, Channel.EMAIL, "Sent the yearly maintenance plan; client signed and returned it.", 3);
    }

    private ServiceCase serviceCase(String title, String company, CasePriority priority, int dueInDays, AppUser by,
                                    CaseStatus status, int createdDaysAgo, String description) {
        ServiceCase c = new ServiceCase();
        c.setTitle(title);
        c.setDescription(description);
        c.setCustomer(byName.get(company));
        c.setPriority(priority);
        c.setDueDate(LocalDate.now().plusDays(dueInDays));
        c.setCreatedBy(by);
        c.setStatus(status);
        c.setCreatedAt(now.minusDays(createdDaysAgo));
        return cases.save(c);
    }

    private void assign(ServiceCase c, AppUser u, boolean done, int doneDaysAgo, String note) {
        CaseAssignment a = new CaseAssignment();
        a.setServiceCase(c);
        a.setUser(u);
        a.setAssignedAt(c.getCreatedAt());
        if (done) {
            a.setStatus(AssignmentStatus.DONE);
            a.setDoneAt(now.minusDays(doneDaysAgo));
            a.setDoneNote(note);
        }
        caseAssignments.save(a);
    }

    private void caseAct(ServiceCase c, AppUser by, ActivityType type, Channel channel, String outcome, int daysAgo) {
        Activity a = act(c.getCustomer().getCompanyName(), type, channel, outcome, daysAgo, by);
        a.setServiceCase(c);
    }

    private AppUser user(String name, String email, Role role, Team team) {
        AppUser u = new AppUser();
        u.setFullName(name);
        u.setEmail(email);
        u.setRole(role);
        u.setTeam(team);
        u.setPasswordHash(encoder.encode(DEMO_PASSWORD));
        u.setCreatedAt(now.minusDays(120));
        return users.save(u);
    }

    /** Creates the customer plus its "New lead created" entry and, if needed, one status change. */
    private void customer(String company, String contact, String email, String phone, CustomerStatus status,
                          AppUser owner, int createdDaysAgo, AppUser changedBy, String reason) {
        Customer c = new Customer();
        c.setCompanyName(company);
        c.setContactName(contact);
        c.setEmail(email);
        c.setPhone(phone);
        c.setOwner(owner);
        c.setCurrentStatus(status);
        c.setCreatedAt(now.minusDays(createdDaysAgo));
        customers.save(c);
        byName.put(company, c);

        history(c, null, CustomerStatus.NEGOTIATING, owner, c.getCreatedAt(), "New lead created");
        if (status != CustomerStatus.NEGOTIATING) {
            history(c, CustomerStatus.NEGOTIATING, status, changedBy, c.getCreatedAt().plusDays(createdDaysAgo / 2), reason);
        }
    }

    private void history(Customer c, CustomerStatus prev, CustomerStatus next, AppUser by, LocalDateTime at, String reason) {
        StatusHistory h = new StatusHistory();
        h.setCustomer(c);
        h.setPreviousStatus(prev);
        h.setNewStatus(next);
        h.setChangedBy(by);
        h.setChangedAt(at);
        h.setReason(reason);
        history.save(h);
    }

    private Activity act(String company, ActivityType type, Channel channel, String outcome, int daysAgo, AppUser by) {
        Activity a = new Activity();
        a.setCustomer(byName.get(company));
        a.setUser(by);
        a.setActivityType(type);
        a.setChannel(channel);
        a.setOutcome(outcome);
        a.setActivityDate(now.minusDays(daysAgo).withHour(9 + (int) (activities.count() % 8)).withMinute(30));
        a.setCreatedAt(a.getActivityDate().plusMinutes(5));
        return activities.save(a);
    }

    private void followUp(String company, Activity from, AppUser assignee, int dueInDays, String notes, boolean done) {
        FollowUp f = new FollowUp();
        f.setCustomer(byName.get(company));
        f.setActivity(from);
        f.setAssignedUser(assignee);
        f.setDueDate(LocalDate.now().plusDays(dueInDays));
        f.setNotes(notes);
        f.setStatus(done ? FollowUpStatus.COMPLETED : FollowUpStatus.PENDING); // overdue ones get flagged on startup
        followUps.save(f);
    }
}
