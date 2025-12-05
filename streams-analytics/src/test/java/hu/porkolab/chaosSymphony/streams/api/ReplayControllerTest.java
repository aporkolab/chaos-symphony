package hu.porkolab.chaosSymphony.streams.api;

import hu.porkolab.chaosSymphony.streams.ReplayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReplayControllerTest {

    @Mock ReplayService replayService;

    private ReplayController controller;

    @BeforeEach
    void setup() {
        controller = new ReplayController(replayService);
    }

    @Test
    void shouldReplaySuccessfully() throws Exception {
        doNothing().when(replayService).replayFrom(eq("my-group"), any(Duration.class));

        var response = controller.replay(new ReplayController.ReplayRequest("my-group", "1h"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(replayService).replayFrom(eq("my-group"), eq(Duration.ofHours(1)));
    }

    @Test
    void shouldReplayWithMinutesDuration() throws Exception {
        doNothing().when(replayService).replayFrom(any(), any());

        var response = controller.replay(new ReplayController.ReplayRequest("test-group", "30m"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(replayService).replayFrom(eq("test-group"), eq(Duration.ofMinutes(30)));
    }

    @Test
    void shouldReturn500OnServiceException() throws Exception {
        doThrow(new ExecutionException("Kafka error", new RuntimeException()))
            .when(replayService).replayFrom(any(), any());

        var response = controller.replay(new ReplayController.ReplayRequest("fail-group", "1h"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void shouldReturn500OnInvalidDurationFormat() {
        var response = controller.replay(new ReplayController.ReplayRequest("my-group", "invalid"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        verifyNoInteractions(replayService);
    }
}
