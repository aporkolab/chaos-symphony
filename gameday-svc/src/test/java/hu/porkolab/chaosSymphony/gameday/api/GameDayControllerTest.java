package hu.porkolab.chaosSymphony.gameday.api;

import hu.porkolab.chaosSymphony.gameday.service.GameDayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GameDayControllerTest {

    @Mock
    private GameDayService gameDayService;

    private GameDayController controller;

    @BeforeEach
    void setup() {
        controller = new GameDayController(gameDayService);
    }

    @Test
    void shouldStartGameday() {
        doNothing().when(gameDayService).runGameDay();

        var response = controller.startGameday();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isEqualTo("GameDay scenario initiated in the background.");
        verify(gameDayService).runGameDay();
    }
}
