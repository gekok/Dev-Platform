package dev.platform.user.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.platform.user.api.exception.GlobalExceptionHandler;
import dev.platform.user.application.dto.UserSummaryDto;
import dev.platform.user.application.port.in.GetCurrentUserPort;
import dev.platform.user.application.port.in.UserDirectoryPort;
import dev.platform.user.application.port.out.TokenPort;
import dev.platform.user.domain.AccountStatus;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

  @Mock
  private GetCurrentUserPort getCurrentUserPort;

  @Mock
  private UserDirectoryPort userDirectoryPort;

  @Mock
  private TokenPort tokenPort;

  @InjectMocks
  private UserController userController;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(userController)
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
  }

  @Test
  @DisplayName("GET /api/v1/users/me trả về 200 khi có Bearer token hợp lệ")
  void shouldReturnCurrentUserWhenTokenValid() throws Exception {
    UUID userId = UUID.randomUUID();
    UserSummaryDto summary = new UserSummaryDto(
        userId, "user@demo.local", "Test User", AccountStatus.ACTIVE, 1L, Set.of("USER"));

    when(tokenPort.parseAndValidate("valid.token")).thenReturn(userId);
    when(getCurrentUserPort.getCurrentUser(userId)).thenReturn(summary);

    mockMvc.perform(get("/api/v1/users/me")
            .header("Authorization", "Bearer valid.token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(userId.toString()))
        .andExpect(jsonPath("$.email").value("user@demo.local"))
        .andExpect(jsonPath("$.fullName").value("Test User"));
  }

  @Test
  @DisplayName("GET /api/v1/users/me trả về 401 khi thiếu Authorization header")
  void shouldReturn401WhenAuthHeaderMissing() throws Exception {
    mockMvc.perform(get("/api/v1/users/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  @DisplayName("GET /api/v1/users trả về danh sách người dùng tìm kiếm khi có Bearer token")
  void shouldSearchUsers() throws Exception {
    UUID callerId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UserSummaryDto summary = new UserSummaryDto(
        userId, "alex@demo.local", "Alex Morgan", AccountStatus.ACTIVE, 1L, Set.of("USER"));

    when(tokenPort.parseAndValidate("valid.token")).thenReturn(callerId);
    when(userDirectoryPort.searchUsers("alex", 20)).thenReturn(List.of(summary));

    mockMvc.perform(get("/api/v1/users")
            .header("Authorization", "Bearer valid.token")
            .param("query", "alex"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(userId.toString()))
        .andExpect(jsonPath("$[0].fullName").value("Alex Morgan"))
        .andExpect(jsonPath("$[0].email").value("alex@demo.local"));
  }

  @Test
  @DisplayName("GET /api/v1/users trả về 401 khi thiếu Authorization header")
  void shouldReturn401WhenSearchWithoutToken() throws Exception {
    mockMvc.perform(get("/api/v1/users").param("query", "alex"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  @DisplayName("GET /api/v1/users/profiles trả về danh sách hồ sơ tối thiểu khi có token")
  void shouldGetProfilesWithToken() throws Exception {
    UUID callerId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();
    UserSummaryDto summary = new UserSummaryDto(
        targetId, "alex@demo.local", "Alex Morgan", AccountStatus.ACTIVE, 1L, Set.of("USER"));

    when(tokenPort.parseAndValidate("valid.token")).thenReturn(callerId);
    when(userDirectoryPort.getProfiles(Set.of(targetId))).thenReturn(List.of(summary));

    mockMvc.perform(get("/api/v1/users/profiles")
            .header("Authorization", "Bearer valid.token")
            .param("ids", targetId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(targetId.toString()))
        .andExpect(jsonPath("$[0].displayName").value("Alex Morgan"));
  }
}
