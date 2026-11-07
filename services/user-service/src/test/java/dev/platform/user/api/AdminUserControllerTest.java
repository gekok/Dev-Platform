package dev.platform.user.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.platform.user.api.exception.GlobalExceptionHandler;
import dev.platform.user.application.command.AdminCreateAccountCommand;
import dev.platform.user.application.dto.UserSummaryDto;
import dev.platform.user.application.port.in.AdminCreateAccountPort;
import dev.platform.user.application.port.in.ChangeUserStatusPort;
import dev.platform.user.application.port.in.GetCurrentUserPort;
import dev.platform.user.application.port.in.ManageSystemRolePort;
import dev.platform.user.application.port.out.TokenPort;
import dev.platform.user.domain.AccountStatus;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

  @Mock
  private AdminCreateAccountPort adminCreateAccountPort;

  @Mock
  private ChangeUserStatusPort changeUserStatusPort;

  @Mock
  private ManageSystemRolePort manageSystemRolePort;

  @Mock
  private GetCurrentUserPort getCurrentUserPort;

  @Mock
  private TokenPort tokenPort;

  @InjectMocks
  private AdminUserController adminUserController;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(adminUserController)
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
  }

  @Test
  @DisplayName("POST /api/v1/admin/accounts trả về 201 Created khi Admin tạo tài khoản")
  void shouldCreateAccountSuccessfully() throws Exception {
    UUID adminId = UUID.randomUUID();
    UserSummaryDto adminUser = new UserSummaryDto(
        adminId, "admin@demo.local", "Admin", AccountStatus.ACTIVE, 1L, Set.of("SYSTEM_ADMIN"));

    when(tokenPort.parseAndValidate("admin.token")).thenReturn(adminId);
    when(getCurrentUserPort.getCurrentUser(adminId)).thenReturn(adminUser);

    UUID newUserId = UUID.randomUUID();
    UserSummaryDto createdUser = new UserSummaryDto(
        newUserId, "new@demo.local", "New User", AccountStatus.ACTIVE, 1L, Set.of("USER"));
    when(adminCreateAccountPort.createAccount(any(AdminCreateAccountCommand.class))).thenReturn(createdUser);

    String requestJson = """
        {
          "email": "new@demo.local",
          "password": "Password123!",
          "fullName": "New User",
          "roles": ["USER"]
        }
        """;

    mockMvc.perform(post("/api/v1/admin/accounts")
            .header("Authorization", "Bearer admin.token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value("new@demo.local"));
  }

  @Test
  @DisplayName("POST /api/v1/admin/accounts trả về 403 Forbidden khi người gọi không phải SYSTEM_ADMIN")
  void shouldReturn403WhenCallerIsNotAdmin() throws Exception {
    UUID normalUserId = UUID.randomUUID();
    UserSummaryDto normalUser = new UserSummaryDto(
        normalUserId, "normal@demo.local", "User", AccountStatus.ACTIVE, 1L, Set.of("USER"));

    when(tokenPort.parseAndValidate("user.token")).thenReturn(normalUserId);
    when(getCurrentUserPort.getCurrentUser(normalUserId)).thenReturn(normalUser);

    String requestJson = """
        {
          "email": "new@demo.local",
          "password": "Password123!",
          "fullName": "New User"
        }
        """;

    mockMvc.perform(post("/api/v1/admin/accounts")
            .header("Authorization", "Bearer user.token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("SYSTEM_ADMIN_REQUIRED"));
  }

  @Test
  @DisplayName("POST /api/v1/admin/accounts trả về 400 Bad Request khi mật khẩu vượt quá 72 bytes UTF-8")
  void shouldReturn400WhenPasswordExceeds72Bytes() throws Exception {
    String oversizedPassword = "a".repeat(73);
    String requestJson = String.format("""
        {
          "email": "new@demo.local",
          "password": "%s",
          "fullName": "New User"
        }
        """, oversizedPassword);

    mockMvc.perform(post("/api/v1/admin/accounts")
            .header("Authorization", "Bearer admin.token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  @DisplayName("POST /api/v1/admin/accounts trả về 409 Conflict khi xảy ra trùng lặp đồng thời (DataIntegrityViolationException)")
  void shouldReturn409WhenConcurrentDuplicateEmailOccurs() throws Exception {
    UUID adminId = UUID.randomUUID();
    UserSummaryDto adminUser = new UserSummaryDto(
        adminId, "admin@demo.local", "Admin", AccountStatus.ACTIVE, 1L, Set.of("SYSTEM_ADMIN"));

    when(tokenPort.parseAndValidate("admin.token")).thenReturn(adminId);
    when(getCurrentUserPort.getCurrentUser(adminId)).thenReturn(adminUser);
    when(adminCreateAccountPort.createAccount(any(AdminCreateAccountCommand.class)))
        .thenThrow(new DataIntegrityViolationException("Unique constraint violation: duplicate email in users"));

    String requestJson = """
        {
          "email": "duplicate@demo.local",
          "password": "Password123!",
          "fullName": "Duplicate User"
        }
        """;

    mockMvc.perform(post("/api/v1/admin/accounts")
            .header("Authorization", "Bearer admin.token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
  }

  @Test
  @DisplayName("POST /api/v1/admin/accounts trả về 400 Bad Request khi gửi payload JSON hỏng (malformed JSON)")
  void shouldReturn400WhenMalformedJsonSent() throws Exception {
    String malformedJson = "{ \"email\": \"new@demo.local\", \"password\": ";

    mockMvc.perform(post("/api/v1/admin/accounts")
            .header("Authorization", "Bearer admin.token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(malformedJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  @DisplayName("PATCH /api/v1/admin/users/{userId}/status đổi trạng thái thành công")
  void shouldChangeStatusSuccessfully() throws Exception {
    UUID adminId = UUID.randomUUID();
    UserSummaryDto adminUser = new UserSummaryDto(
        adminId, "admin@demo.local", "Admin", AccountStatus.ACTIVE, 1L, Set.of("SYSTEM_ADMIN"));

    when(tokenPort.parseAndValidate("admin.token")).thenReturn(adminId);
    when(getCurrentUserPort.getCurrentUser(adminId)).thenReturn(adminUser);

    UUID targetUserId = UUID.randomUUID();
    String requestJson = """
        {
          "status": "LOCKED"
        }
        """;

    mockMvc.perform(patch("/api/v1/admin/users/" + targetUserId + "/status")
            .header("Authorization", "Bearer admin.token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
        .andExpect(status().isNoContent());

    verify(changeUserStatusPort).changeStatus(targetUserId, AccountStatus.LOCKED);
  }

  @Test
  @DisplayName("PUT /api/v1/admin/users/{userId}/roles cập nhật toàn bộ vai trò thành công")
  void shouldUpdateRolesSuccessfully() throws Exception {
    UUID adminId = UUID.randomUUID();
    UserSummaryDto adminUser = new UserSummaryDto(
        adminId, "admin@demo.local", "Admin", AccountStatus.ACTIVE, 1L, Set.of("SYSTEM_ADMIN"));

    when(tokenPort.parseAndValidate("admin.token")).thenReturn(adminId);
    when(getCurrentUserPort.getCurrentUser(adminId)).thenReturn(adminUser);

    UUID targetUserId = UUID.randomUUID();
    String requestJson = """
        {
          "roles": ["USER", "SYSTEM_ADMIN"]
        }
        """;

    mockMvc.perform(put("/api/v1/admin/users/" + targetUserId + "/roles")
            .header("Authorization", "Bearer admin.token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
        .andExpect(status().isNoContent());

    verify(manageSystemRolePort).updateRoles(targetUserId, Set.of("USER", "SYSTEM_ADMIN"));
  }
}
