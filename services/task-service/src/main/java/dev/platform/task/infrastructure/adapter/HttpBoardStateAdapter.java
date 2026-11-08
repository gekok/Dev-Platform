package dev.platform.task.infrastructure.adapter;

import dev.platform.task.application.port.out.BoardStateReaderPort;
import dev.platform.task.application.query.BoardStateSnapshot;
import dev.platform.task.application.service.BoardStateUnavailableException;
import dev.platform.task.application.service.InvalidBoardTargetException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Component
public class HttpBoardStateAdapter implements BoardStateReaderPort {
    private final RestClient restClient;
    private final String baseUrl;
    private final String serviceToken;

    public HttpBoardStateAdapter(
            RestClient.Builder builder,
            @Value("${dev-platform.internal.board-service.base-url:http://localhost:8082}") String baseUrl,
            @Value("${dev-platform.security.internal-service-token:}") String serviceToken) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2_000);
        requestFactory.setReadTimeout(2_000);
        this.restClient = builder.requestFactory(requestFactory).build();
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.serviceToken = serviceToken;
    }

    @Override
    public BoardStateSnapshot getBoardState(Long boardId) {
        if (serviceToken.isBlank()) {
            throw new BoardStateUnavailableException(
                    "Board Service authentication is not configured.", null);
        }
        try {
            InternalBoardResponse response = restClient.get()
                    .uri(baseUrl + "/internal/boards/{boardId}", boardId)
                    .header("X-Service-Token", serviceToken)
                    .retrieve()
                    .body(InternalBoardResponse.class);
            if (response == null || response.boardId() == null || response.projectId() == null
                    || response.boardStatus() == null || response.projectStatus() == null
                    || response.columns() == null) {
                throw new BoardStateUnavailableException("Board Service returned an invalid response.", null);
            }
            return new BoardStateSnapshot(response.boardId(), response.projectId(),
                    response.boardStatus(), response.projectStatus(),
                    response.columns().stream()
                            .map(column -> new BoardStateSnapshot.ColumnSnapshot(
                                    column.id(), column.name(), column.position()))
                            .toList());
        } catch (HttpClientErrorException.NotFound exception) {
            throw new InvalidBoardTargetException();
        } catch (RestClientException exception) {
            throw new BoardStateUnavailableException("Board Service is unavailable.", exception);
        }
    }

    private record InternalBoardResponse(
            Long boardId,
            Long projectId,
            String boardStatus,
            String projectStatus,
            List<InternalColumn> columns) {
    }

    private record InternalColumn(Long id, String name, int position) {
    }
}
