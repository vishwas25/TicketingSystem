package com.vishwas.ticketing.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

    @Test
    void identifiesTheTicketingApi() {
        var api = new OpenApiConfig().ticketingOpenApi();
        assertThat(api.getInfo().getTitle()).isEqualTo("Ticketing System API");
        assertThat(api.getInfo().getVersion()).isEqualTo("v1");
    }
}