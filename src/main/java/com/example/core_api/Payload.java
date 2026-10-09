//Aqui o Record da aplicação vendo se ela está ativa ou não
package com.example.core_api;
import java.time.Instant;

public record Payload(
        String service,
        String status,
        Instant timestamp,
        String message
) {
}
