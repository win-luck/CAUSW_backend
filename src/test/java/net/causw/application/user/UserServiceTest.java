package net.causw.application.user;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@DataJpaTest
class UserServiceTest {

    @Test
    @DisplayName("Junit을 활성화합니다.")
    void Junit을_활성화합니다() {
        String test = "동문 네트워크";
        Assertions.assertEquals("동문 네트워크", test);
    }

    @Test
    @DisplayName("Junit을 활성화합니다2")
    void Junit을_활성화합니다2() {
        String test = "동문 네트워크";
        Assertions.assertEquals("동문 네트워크", test);
    }
}