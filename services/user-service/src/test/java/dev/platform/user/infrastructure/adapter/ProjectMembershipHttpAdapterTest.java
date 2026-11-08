package dev.platform.user.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import dev.platform.user.domain.exception.AuthorizationUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ProjectMembershipHttpAdapterTest {
    private static final String URL = "http://board-service/internal/v1/projects/42/members/me";
    private static final String AUTHORIZATION = "Bearer signed.jwt";

    @Test
    @DisplayName("Gửi Bearer token tới board-service và chấp nhận 204")
    void shouldReturnTrueForMembershipApproval() {
        TestClient client = client(HttpStatus.NO_CONTENT);

        assertThat(client.adapter().isActiveProjectMember(42L, AUTHORIZATION)).isTrue();
        client.server().verify();
    }

    @Test
    @DisplayName("Chuyển 403 của board-service thành Membership denial")
    void shouldReturnFalseForMembershipDenial() {
        TestClient client = client(HttpStatus.FORBIDDEN);

        assertThat(client.adapter().isActiveProjectMember(42L, AUTHORIZATION)).isFalse();
        client.server().verify();
    }

    @Test
    @DisplayName("Chuyển lỗi board-service thành lỗi authorization unavailable")
    void shouldFailClosedForUnavailableBoardService() {
        TestClient client = client(HttpStatus.SERVICE_UNAVAILABLE);

        assertThatThrownBy(() -> client.adapter().isActiveProjectMember(42L, AUTHORIZATION))
                .isInstanceOf(AuthorizationUnavailableException.class);
        client.server().verify();
    }

    private static TestClient client(HttpStatus status) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", AUTHORIZATION))
                .andRespond(withStatus(status));
        return new TestClient(new ProjectMembershipHttpAdapter(builder, "http://board-service"), server);
    }

    private record TestClient(ProjectMembershipHttpAdapter adapter, MockRestServiceServer server) {}
}
