package com.gateway.apigateway.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class FallbackController {
    @RequestMapping("/contactsupport")
    public Mono<String> contactSupport(){
        return Mono.just("fallback error : An error occurred Please try after sometime or contact support team!!!");
    }
}

//in real projects you may have some complex fallback
// requirement like triggering an email to the support team or sending some default response
