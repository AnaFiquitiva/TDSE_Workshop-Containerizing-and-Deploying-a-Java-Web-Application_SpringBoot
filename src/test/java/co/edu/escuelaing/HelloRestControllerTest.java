package co.edu.escuelaing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HelloRestControllerTest {

    private final HelloRestController controller = new HelloRestController();

    @Test
    void greetsGivenName() {
        assertThat(controller.greeting("Pedro")).isEqualTo("Hello, Pedro!");
    }

    @Test
    void greetsWorldByDefault() {
        assertThat(controller.greeting("World")).isEqualTo("Hello, World!");
    }
}
