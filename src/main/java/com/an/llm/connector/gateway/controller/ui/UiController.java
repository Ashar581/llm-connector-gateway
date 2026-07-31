package com.an.llm.connector.gateway.controller.ui;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UiController {

    @GetMapping({
            "/ui/",
            "/ui",
            "/ui/{path:[^\\.]*}"
    })
    public String forward() {
        return "forward:/ui/index.html";
    }

    @GetMapping("/")
    public String redirectToUi() {
        return "redirect:/ui";
    }
}
