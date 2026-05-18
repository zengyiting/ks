package com.example.recommend.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaRouteController {

    @GetMapping({
            "/",
            "/login",
            "/register",
            "/search",
            "/profile",
            "/favorites",
            "/cart",
            "/item/{id}"
    })
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}

