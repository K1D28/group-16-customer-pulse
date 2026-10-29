package com.customerpulse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;

import com.customerpulse.domain.CaseStatus;
import com.customerpulse.domain.ServiceCase;
import com.customerpulse.repository.CaseRepository;
import com.customerpulse.repository.UserRepository;
import com.customerpulse.service.CaseService;
import com.customerpulse.web.form.CaseForm;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:test;MODE=MySQL;DATABASE_TO_LOWER=TRUE", "app.seed-demo-data=true"})
@AutoConfigureMockMvc
class CustomerPulseApplicationTests {

    @Autowired
    MockMvc mvc;

    @Test
    void anonymousUserIsSentToLogin() throws Exception {
        mvc.perform(get("/dashboard")).andExpect(status().is3xxRedirection());
    }

    @Test
    @WithUserDetails("priya@example.com")
    void managerSeesEverything() throws Exception {
        mvc.perform(get("/dashboard")).andExpect(status().isOk());
        mvc.perform(get("/customers/4")).andExpect(status().isOk());
        mvc.perform(get("/team")).andExpect(status().isOk());
        mvc.perform(get("/users")).andExpect(status().isOk());
    }

    @Test
    @WithUserDetails("nadia@example.com")
    void staffOnlySeesOwnCustomers() throws Exception {
        mvc.perform(get("/customers/1")).andExpect(status().isOk());      // Nadia's
        mvc.perform(get("/customers/4")).andExpect(status().isForbidden()); // Marcus's
        mvc.perform(get("/users")).andExpect(status().isForbidden());
        mvc.perform(get("/team")).andExpect(status().isForbidden());
        mvc.perform(get("/activities")).andExpect(status().isForbidden());   // activity log is Manager-only
        mvc.perform(get("/activities/new")).andExpect(status().isOk());      // but Staff can still log
    }

    @Test
    @WithUserDetails("daniel@example.com")
    void managerOnlySeesOwnTeam() throws Exception {
        // Team A's customers, cases and staff are hidden from Team B's manager
        mvc.perform(get("/customers/1")).andExpect(status().isForbidden());
        mvc.perform(get("/cases/1")).andExpect(status().isForbidden());
        mvc.perform(get("/users/4/edit")).andExpect(status().isNotFound());
        mvc.perform(get("/customers")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("ABC Company"))));
        mvc.perform(get("/activities")).andExpect(status().isOk());
    }

    @Autowired
    com.customerpulse.service.TeamService teamService;

    @Test
    @Transactional
    void managerCanRenameTeamButNamesAreUnique() {
        var daniel = users.findByEmailIgnoreCase("daniel@example.com").orElseThrow();
        assertThat(teamService.rename("  Bangkok Sales ", daniel).getName()).isEqualTo("Bangkok Sales");
        assertThatThrownBy(() -> teamService.rename("team a", daniel))
                .isInstanceOf(com.customerpulse.service.BusinessException.class);
        var nadia = users.findByEmailIgnoreCase("nadia@example.com").orElseThrow();
        assertThatThrownBy(() -> teamService.rename("Mine", nadia)).isInstanceOf(AccessDeniedException.class);
    }

    @Autowired
    CaseService caseService;
    @Autowired
    UserRepository users;
    @Autowired
    CaseRepository cases;

    @Test
    @Transactional
    void caseClosesWhenEveryAssigneeIsDone() {
        var priya = users.findByEmailIgnoreCase("priya@example.com").orElseThrow();
        var nadia = users.findByEmailIgnoreCase("nadia@example.com").orElseThrow();
        var marcus = users.findByEmailIgnoreCase("marcus@example.com").orElseThrow();

        CaseForm form = new CaseForm();
        form.setTitle("Test case");
        form.setCustomerId(12L); // a customer owned by Marcus
        form.setAssigneeIds(List.of(nadia.getId(), marcus.getId()));
        ServiceCase c = caseService.create(form, priya);
        assertThat(c.getStatus()).isEqualTo(CaseStatus.OPEN);

        // Staff cannot create cases
        assertThatThrownBy(() -> caseService.create(form, nadia)).isInstanceOf(AccessDeniedException.class);

        // Nadia can log on the case even though Marcus owns the customer
        assertThat(caseService.canLog(c, nadia)).isTrue();

        caseService.markDone(c.getId(), "done", nadia);
        assertThat(cases.findById(c.getId()).orElseThrow().getStatus()).isEqualTo(CaseStatus.IN_PROGRESS);

        caseService.markDone(c.getId(), null, marcus);
        assertThat(cases.findById(c.getId()).orElseThrow().getStatus()).isEqualTo(CaseStatus.CLOSED);
        assertThat(caseService.canLog(cases.findById(c.getId()).orElseThrow(), nadia)).isFalse();
    }

    @Test
    @WithUserDetails("marcus@example.com")
    void staffCannotOpenCasesTheyAreNotOn() throws Exception {
        mvc.perform(get("/cases")).andExpect(status().isOk());
        mvc.perform(get("/cases/2")).andExpect(status().isForbidden()); // Nadia's invoice case
        mvc.perform(get("/cases/new")).andExpect(status().isForbidden());
    }
}
