package dev.platform.user.api;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.platform.user.api.exception.GlobalExceptionHandler;
import dev.platform.user.application.dto.UserSummaryDto;
import dev.platform.user.application.port.in.GetCurrentUserPort;
import dev.platform.user.application.port.in.GetAccountStatusPort;
import dev.platform.user.application.port.in.UserDirectoryPort;
import dev.platform.user.application.port.out.ProjectMembershipPort;
import dev.platform.user.application.port.out.TokenPort;
import dev.platform.user.domain.AccountStatus;
import dev.platform.user.domain.exception.AccountNotFoundException;
import dev.platform.user.domain.exception.AuthorizationUnavailableException;
import dev.platform.user.domain.exception.AuthenticationException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
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
  private GetAccountStatusPort getAccountStatusPort;

  @Mock
  private TokenPort tokenPort;

  @Mock
  private ProjectMembershipPort projectMembershipPort;

  @InjectMocks
  private UserController userController;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(
            userController, new InternalUserController(getAccountStatusPort, "test-service-token"))
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
  }

  @Test
  @DisplayName("GET /internal/users/{userId} rejects requests without the service token")
  void shouldRejectInternalAccountLookupWithoutServiceToken() throws Exception {
    mockMvc.perform(get("/internal/users/{userId}", UUID.randomUUID()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  @DisplayName("GET /internal/users/{userId} returns only status for a valid service token")
  void shouldReturnAccountStatusForValidServiceToken() throws Exception {
    UUID userId = UUID.randomUUID();
    when(getAccountStatusPort.getAccountStatus(userId)).thenReturn(AccountStatus.ACTIVE);

    mockMvc.perform(get("/internal/users/{userId}", userId)
            .header("X-Service-Token", "test-service-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.email").doesNotExist())
        .andExpect(jsonPath("$.fullName").doesNotExist());
  }

  @Test
  @DisplayName("GET /internal/users/{userId} rejects an invalid service token")
  void shouldRejectInternalAccountLookupWithInvalidServiceToken() throws Exception {
    mockMvc.perform(get("/internal/users/{userId}", UUID.randomUUID())
            .header("X-Service-Token", "invalid-token"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

    verifyNoInteractions(getAccountStatusPort);
  }

  @Test
  @DisplayName("GET /internal/users/{userId} fails closed when no service token is configured")
  void shouldRejectInternalAccountLookupWhenServiceTokenIsNotConfigured() throws Exception {
    MockMvc unconfiguredMvc = MockMvcBuilders.standaloneSetup(
            new InternalUserController(getAccountStatusPort, ""))
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();

    unconfiguredMvc.perform(get("/internal/users/{userId}", UUID.randomUUID())
            .header("X-Service-Token", "test-service-token"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

    verifyNoInteractions(getAccountStatusPort);
  }

  @Test
  @DisplayName("GET /internal/users/{userId} returns 404 when the Account does not exist")
  void shouldReturn404WhenInternalAccountLookupDoesNotFindAccount() throws Exception {
    UUID userId = UUID.randomUUID();
    when(getAccountStatusPort.getAccountStatus(userId))
        .thenThrow(new AccountNotFoundException("Account not found: " + userId));

    mockMvc.perform(get("/internal/users/{userId}", userId)
            .header("X-Service-Token", "test-service-token"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
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
            .header("Authorization", "bearer valid.token"))
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
    when(getCurrentUserPort.getCurrentUser(callerId)).thenReturn(new UserSummaryDto(
        callerId, "caller@demo.local", "Caller", AccountStatus.ACTIVE, 1L, Set.of("USER")));
    when(userDirectoryPort.searchUsers("alex", 20)).thenReturn(List.of(summary));

    mockMvc.perform(get("/api/v1/users")
            .header("Authorization", "Bearer valid.token")
            .param("query", "alex"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(userId.toString()))
        .andExpect(jsonPath("$[0].fullName").value("Alex Morgan"))
        .andExpect(jsonPath("$[0].email").doesNotExist());
  }

  @Test
  @DisplayName("GET /api/v1/users từ chối token còn hạn của tài khoản không còn ACTIVE")
  void shouldRejectDirectoryAccessForInactiveCaller() throws Exception {
    UUID callerId = UUID.randomUUID();
    when(tokenPort.parseAndValidate("inactive.token")).thenReturn(callerId);
    when(getCurrentUserPort.getCurrentUser(callerId))
        .thenThrow(new AuthenticationException("Account is locked"));

    mockMvc.perform(get("/api/v1/users")
            .header("Authorization", "Bearer inactive.token")
            .param("query", "alex"))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(userDirectoryPort);
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
    when(getCurrentUserPort.getCurrentUser(callerId)).thenReturn(new UserSummaryDto(
        callerId, "caller@demo.local", "Caller", AccountStatus.ACTIVE, 1L, Set.of("USER")));
    when(projectMembershipPort.isActiveProjectMember(42L, callerId)).thenReturn(true);
    when(userDirectoryPort.getProfiles(Set.of(targetId))).thenReturn(List.of(summary));

    mockMvc.perform(get("/api/v1/users/profiles")
            .header("Authorization", "Bearer valid.token")
            .param("projectId", "42")
            .param("ids", targetId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(targetId.toString()))
        .andExpect(jsonPath("$[0].displayName").value("Alex Morgan"));
  }

  @Test
  @DisplayName("GET /api/v1/users/profiles vẫn xác minh Membership khi ids rỗng")
  void shouldRequireMembershipForEmptyProfileLookup() throws Exception {
    UUID callerId = UUID.randomUUID();
    when(tokenPort.parseAndValidate("valid.token")).thenReturn(callerId);
    when(getCurrentUserPort.getCurrentUser(callerId)).thenReturn(new UserSummaryDto(
        callerId, "caller@demo.local", "Caller", AccountStatus.ACTIVE, 1L, Set.of("USER")));
    when(projectMembershipPort.isActiveProjectMember(42L, callerId)).thenReturn(false);

    mockMvc.perform(get("/api/v1/users/profiles")
            .header("Authorization", "Bearer valid.token")
            .param("projectId", "42"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PROJECT_MEMBERSHIP_REQUIRED"));

    verify(projectMembershipPort).isActiveProjectMember(42L, callerId);
    verifyNoInteractions(userDirectoryPort);
  }

  @Test
  @DisplayName("GET /api/v1/users/profiles từ chối caller không thuộc Project")
  void shouldDenyProfileLookupWhenCallerIsNotProjectMember() throws Exception {
    UUID callerId = UUID.randomUUID();
    UserSummaryDto caller = new UserSummaryDto(
        callerId, "caller@demo.local", "Caller", AccountStatus.ACTIVE, 1L, Set.of("USER"));
    when(tokenPort.parseAndValidate("valid.token")).thenReturn(callerId);
    when(getCurrentUserPort.getCurrentUser(callerId)).thenReturn(caller);
    when(projectMembershipPort.isActiveProjectMember(42L, callerId)).thenReturn(false);

    mockMvc.perform(get("/api/v1/users/profiles")
            .header("Authorization", "Bearer valid.token")
            .param("projectId", "42")
            .param("ids", UUID.randomUUID().toString()))
        .andExpect(status().isForbidden());

    verifyNoInteractions(userDirectoryPort);
  }

  @Test
  @DisplayName("GET /api/v1/users/profiles từ chối token còn hạn của tài khoản không còn ACTIVE")
  void shouldRejectProfileAccessForInactiveCaller() throws Exception {
    UUID callerId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();
    when(tokenPort.parseAndValidate("inactive.token")).thenReturn(callerId);
    when(getCurrentUserPort.getCurrentUser(callerId))
        .thenThrow(new AuthenticationException("Account is locked"));

    mockMvc.perform(get("/api/v1/users/profiles")
            .header("Authorization", "Bearer inactive.token")
            .param("ids", targetId.toString()))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(userDirectoryPort, projectMembershipPort);
  }

  @Test
  @DisplayName("GET /api/v1/users/profiles yêu cầu projectId")
  void shouldRequireProjectIdForProfileLookup() throws Exception {
    UUID callerId = UUID.randomUUID();
    when(tokenPort.parseAndValidate("valid.token")).thenReturn(callerId);
    when(getCurrentUserPort.getCurrentUser(callerId)).thenReturn(new UserSummaryDto(
        callerId, "caller@demo.local", "Caller", AccountStatus.ACTIVE, 1L, Set.of("USER")));

    mockMvc.perform(get("/api/v1/users/profiles")
            .header("Authorization", "Bearer valid.token")
            .param("ids", UUID.randomUUID().toString()))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(projectMembershipPort, userDirectoryPort);
  }

  @Test
  @DisplayName("GET /api/v1/users/profiles fail closed khi board-service không xác minh được quyền")
  void shouldFailClosedWhenMembershipCheckIsUnavailable() throws Exception {
    UUID callerId = UUID.randomUUID();
    when(tokenPort.parseAndValidate("valid.token")).thenReturn(callerId);
    when(getCurrentUserPort.getCurrentUser(callerId)).thenReturn(new UserSummaryDto(
        callerId, "caller@demo.local", "Caller", AccountStatus.ACTIVE, 1L, Set.of("USER")));
    when(projectMembershipPort.isActiveProjectMember(42L, callerId))
        .thenThrow(new AuthorizationUnavailableException("membership service unavailable"));

    mockMvc.perform(get("/api/v1/users/profiles")
            .header("Authorization", "Bearer valid.token")
            .param("projectId", "42")
            .param("ids", UUID.randomUUID().toString()))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("AUTHORIZATION_UNAVAILABLE"));

    verifyNoInteractions(userDirectoryPort);
  }

  @Test
  @DisplayName("GET /api/v1/users/profiles từ chối batch lớn hơn 100 thay vì trả thiếu dữ liệu")
  void shouldRejectProfileBatchOver100() throws Exception {
    UUID callerId = UUID.randomUUID();
    when(tokenPort.parseAndValidate("valid.token")).thenReturn(callerId);
    when(getCurrentUserPort.getCurrentUser(callerId)).thenReturn(new UserSummaryDto(
        callerId, "caller@demo.local", "Caller", AccountStatus.ACTIVE, 1L, Set.of("USER")));
    String[] ids = IntStream.range(0, 101)
        .mapToObj(index -> UUID.randomUUID().toString())
        .toArray(String[]::new);

    mockMvc.perform(get("/api/v1/users/profiles")
            .header("Authorization", "Bearer valid.token")
            .param("projectId", "42")
            .param("ids", ids))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(userDirectoryPort);
  }

  @Test
  @DisplayName("GET /api/v1/users từ chối limit nằm ngoài khoảng 1 đến 100")
  void shouldRejectInvalidDirectoryLimit() throws Exception {
    UUID callerId = UUID.randomUUID();
    when(tokenPort.parseAndValidate("valid.token")).thenReturn(callerId);
    when(getCurrentUserPort.getCurrentUser(callerId)).thenReturn(new UserSummaryDto(
        callerId, "caller@demo.local", "Caller", AccountStatus.ACTIVE, 1L, Set.of("USER")));

    mockMvc.perform(get("/api/v1/users")
            .header("Authorization", "Bearer valid.token")
            .param("query", "alex")
            .param("limit", "0"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(userDirectoryPort);
  }
}
