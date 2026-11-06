package dev.platform.user;

import dev.platform.user.api.UserController;
import dev.platform.user.application.port.out.PasswordEncoderPort;
import dev.platform.user.application.port.out.SaveUserPort;
import dev.platform.user.application.port.out.TokenPort;
import dev.platform.user.application.service.LoginUserService;
import dev.platform.user.infrastructure.security.BcryptPasswordAdapter;
import dev.platform.user.infrastructure.security.JwtTokenAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class UserServiceWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    JwtTokenAdapter.class,
                    BcryptPasswordAdapter.class,
                    LoginUserService.class,
                    UserController.class)
            .withBean(SaveUserPort.class, () -> mock(SaveUserPort.class));

    @Test
    @DisplayName("Spring Context khởi tạo thành công JwtTokenAdapter mà không gặp lỗi No default constructor found")
    void shouldSuccessfullyInstantiateJwtTokenAdapterAndWiring() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(JwtTokenAdapter.class);
            assertThat(context).hasSingleBean(TokenPort.class);
            assertThat(context).hasSingleBean(LoginUserService.class);
            assertThat(context).hasSingleBean(PasswordEncoderPort.class);
            assertThat(context).hasSingleBean(UserController.class);

            JwtTokenAdapter adapter = context.getBean(JwtTokenAdapter.class);
            assertThat(adapter.getPublicKey()).isNotNull();
            assertThat(adapter.getPublicKeyPem()).startsWith("-----BEGIN PUBLIC KEY-----");
        });
    }
}
