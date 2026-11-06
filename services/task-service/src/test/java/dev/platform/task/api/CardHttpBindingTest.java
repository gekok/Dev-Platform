package dev.platform.task.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import dev.platform.task.api.exception.GlobalExceptionHandler;
import dev.platform.task.application.port.in.*;
import dev.platform.task.application.service.ProjectAuthorizationGuard;

class CardHttpBindingTest {
    @Test
    void listBindsPathAndQueryParameters() throws Exception {
        GetCardsPort reader = mock(GetCardsPort.class);
        when(reader.getByBoard(7L, 8L, 9L, null, null, 3)).thenReturn(List.of());
        CardController controller = new CardController(mock(CreateCardPort.class), reader, mock(GetCardPort.class),
                mock(MoveCardPort.class), mock(AssignCardPort.class), mock(AddCommentPort.class),
                mock(ProjectAuthorizationGuard.class));
        ReflectionTestUtils.setField(controller, "trustedGatewayIdentityEnabled", true);
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/v1/projects/7/boards/8/cards")
                .param("columnId", "9").param("limit", "2")
                .header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isArray());
    }
}
