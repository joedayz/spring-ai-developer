package com.joedayz.springai.m07;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/agents", consumes = MediaType.TEXT_PLAIN_VALUE)
class AgentController {

    private final AgentPatterns patterns;

    AgentController(AgentPatterns patterns) {
        this.patterns = patterns;
    }

    @PostMapping("/chain")
    List<String> chain(@RequestBody String ticket) {
        return patterns.chain(ticket);
    }

    @PostMapping("/route")
    AgentPatterns.Routed route(@RequestBody String message) {
        return patterns.route(message);
    }

    @PostMapping("/orchestrate")
    AgentPatterns.Orchestrated orchestrate(@RequestBody String task) {
        return patterns.orchestrate(task);
    }

    @PostMapping("/refine")
    AgentPatterns.Refined refine(@RequestBody String request,
                                 @RequestParam(defaultValue = "3") int maxIterations) {
        return patterns.refine(request, Math.clamp(maxIterations, 1, 5));
    }
}
