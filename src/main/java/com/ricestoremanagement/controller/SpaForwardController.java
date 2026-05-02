package com.ricestoremanagement.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {
    @GetMapping({
            "/",
            "/admin",
            "/admin/orders",
            "/admin/products",
            "/admin/handover",
            "/shipper",
            "/chat"
    })
    public String forwardSpaRoutes() {
        return "forward:/index.html";
    }
}
