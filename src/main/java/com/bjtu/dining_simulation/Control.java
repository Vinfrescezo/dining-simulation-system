package com.bjtu.dining_simulation;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Control {
    @RequestMapping("/hello")
    public String Hello()
    {
        System.out.println("Hello");
        return "Hello, BJTU Dining System!";
    }
}
