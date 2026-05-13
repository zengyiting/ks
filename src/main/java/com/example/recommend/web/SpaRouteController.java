package com.example.recommend.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaRouteController {

    @GetMapping("/login")
    public String forwardToLogin() {
        return "forward:/login.html";
    }

    @GetMapping({
            "/favorites",
            "/cart",
            "/item/{id}"
    })
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}

