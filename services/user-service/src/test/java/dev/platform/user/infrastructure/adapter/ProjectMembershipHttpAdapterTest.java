package dev.platform.user.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dev.platform.user.domain.exception.AuthorizationUnavailableException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

class ProjectMembershipHttpAdapterTest {
    private static final String BASE_URL = "http://board-service";
    private static final long PROJECT_ID = 42L;
    private static final UUID USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final String SERVICE_TOKEN = "test-service-token";
    private static final String MEMBERSHIP_URL =
            BASE_URL + "/internal/projects/" + PROJECT_ID + "/members/" + USER_ID;

    @Test
    @DisplayName("Cho phép Member ACTIVE khi Project ACTIVE và gửi service token")
    void shouldReturnTrueForActiveMember() {
        TestClient client = client(withSuccess(
                response("MEMBER", "ACTIVE", "ACTIVE"), MediaType.APPLICATION_JSON));

        assertThat(client.adapter().isActiveProjectMember(PROJECT_ID, USER_ID)).isTrue();
        client.server().verify();
    }

    @Test
    @DisplayName("Cho phép Owner ACTIVE khi Project ACTIVE")
    void shouldReturnTrueForActiveOwner() {
        TestClient client = client(withSuccess(
                response("OWNER", "ACTIVE", "ACTIVE"), MediaType.APPLICATION_JSON));

        assertThat(client.adapter().isActiveProjectMember(PROJECT_ID, USER_ID)).isTrue();
        client.server().verify();
    }

    @Test
    @DisplayName("Từ chối membership đã bị gỡ hoặc Project không ACTIVE")
    void shouldReturnFalseForInactiveMembershipOrProject() {
        TestClient removed = client(withSuccess(
                response("MEMBER", "REMOVED", "ACTIVE"), MediaType.APPLICATION_JSON));
        TestClient inactiveProject = client(withSuccess(
                response("MEMBER", "ACTIVE", "COMPLETED"), MediaType.APPLICATION_JSON));

        assertThat(removed.adapter().isActiveProjectMember(PROJECT_ID, USER_ID)).isFalse();
        assertThat(inactiveProject.adapter().isActiveProjectMember(PROJECT_ID, USER_ID)).isFalse();
        removed.server().verify();
        inactiveProject.server().verify();
    }

    @Test
    @DisplayName("Trả false khi Board không tìm thấy Membership")
    void shouldReturnFalseWhenMembershipIsNotFound() {
        TestClient client = client(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.adapter().isActiveProjectMember(PROJECT_ID, USER_ID)).isFalse();
        client.server().verify();
    }

    @Test
    @DisplayName("Không gọi Board khi service token chưa cấu hình")
    void shouldFailClosedWhenServiceTokenIsMissing() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ProjectMembershipHttpAdapter adapter = new ProjectMembershipHttpAdapter(builder, BASE_URL, "");

        assertThatThrownBy(() -> adapter.isActiveProjectMember(PROJECT_ID, USER_ID))
                .isInstanceOf(AuthorizationUnavailableException.class);
        server.verify();
    }

    @Test
    @DisplayName("Chuyển lỗi xác thực nội bộ hoặc lỗi Board thành authorization unavailable")
    void shouldFailClosedForBoardErrors() {
        TestClient unauthorized = client(withStatus(HttpStatus.UNAUTHORIZED));
        TestClient unavailable = client(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> unauthorized.adapter()
                .isActiveProjectMember(PROJECT_ID, USER_ID))
                .isInstanceOf(AuthorizationUnavailableException.class);
        assertThatThrownBy(() -> unavailable.adapter()
                .isActiveProjectMember(PROJECT_ID, USER_ID))
                .isInstanceOf(AuthorizationUnavailableException.class);
        unauthorized.server().verify();
        unavailable.server().verify();
    }

    @Test
    @DisplayName("Fail closed khi Board trả response thiếu thông tin membership")
    void shouldFailClosedForInvalidResponse() {
        TestClient client = client(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.adapter()
                .isActiveProjectMember(PROJECT_ID, USER_ID))
                .isInstanceOf(AuthorizationUnavailableException.class);
        client.server().verify();
    }

    private static TestClient client(ResponseCreator response) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(MEMBERSHIP_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Service-Token", SERVICE_TOKEN))
                .andRespond(response);
        return new TestClient(
                new ProjectMembershipHttpAdapter(builder, BASE_URL, SERVICE_TOKEN), server);
    }

    private static String response(String role, String status, String projectStatus) {
        return "{\"role\":\"" + role + "\",\"status\":\"" + status
                + "\",\"membershipVersion\":1,\"projectStatus\":\"" + projectStatus + "\"}";
    }

    private record TestClient(ProjectMembershipHttpAdapter adapter, MockRestServiceServer server) {}
}
