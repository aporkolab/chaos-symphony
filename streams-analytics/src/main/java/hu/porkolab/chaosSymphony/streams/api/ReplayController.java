package hu.porkolab.chaosSymphony.streams.api;

import hu.porkolab.chaosSymphony.streams.ReplayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/replay")
@RequiredArgsConstructor
@Slf4j
public class ReplayController {

    private final ReplayService replayService;

    public record ReplayRequest(String consumerGroupId, String duration) {}

    @PostMapping
    public ResponseEntity<Void> replay(@RequestBody ReplayRequest request) {
        try {
            // A simple duration parser, a real app might use a more robust one
            Duration duration = Duration.parse("PT" + request.duration().toUpperCase());
            replayService.replayFrom(request.consumerGroupId(), duration);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to replay consumer group {}", request.consumerGroupId(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
