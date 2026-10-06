package be.fanotmz.docsearch.system;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {
    @GetMapping
    public SystemInfo info() {
        return new SystemInfo("DocSearch AI", "FOUNDATION", false);
    }

    public record SystemInfo(String name, String phase, boolean documentSearchAvailable) {}
}
