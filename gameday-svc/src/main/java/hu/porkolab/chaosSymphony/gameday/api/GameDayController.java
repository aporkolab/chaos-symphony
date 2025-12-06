package hu.porkolab.chaosSymphony.gameday.api;

import hu.porkolab.chaosSymphony.gameday.service.GameDayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/gameday")
@RequiredArgsConstructor
public class GameDayController {

    private final GameDayService gameDayService;

    @PostMapping("/start")
    public ResponseEntity<String> startGameday() {
        log.info("Received request to start GameDay scenario. Triggering async execution.");
        gameDayService.runGameDay();
        return ResponseEntity.accepted().body("GameDay scenario initiated in the background.");
    }
}
