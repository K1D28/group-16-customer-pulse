package com.customerpulse;

import com.customerpulse.repository.CustomerRepository;
import com.customerpulse.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:clean-start;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "app.first-manager.name=Test Manager",
        "app.first-manager.email=manager@example.com",
        "app.first-manager.password=test-password"
})
class CleanStartupTests {

    @Autowired UserRepository users;
    @Autowired CustomerRepository customers;

    @Test
    void createsOnlyTheFirstManagerByDefault() {
        assertThat(users.count()).isEqualTo(1);
        assertThat(users.findByEmailIgnoreCase("manager@example.com")).isPresent();
        assertThat(customers.count()).isZero();
    }
}