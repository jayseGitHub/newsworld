package io.newsworld.collector.api;

import io.newsworld.collector.config.NewsWorldProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/config")
@RequiredArgsConstructor
public class ConfigController {

    private final NewsWorldProperties props;

    @PutMapping("/mistral-key")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateMistralKey(@RequestBody String key) {
        props.getMistral().setApiKey(key.strip());
    }
}
