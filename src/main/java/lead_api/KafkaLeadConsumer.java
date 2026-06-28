package lead_api;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

@Component
@Profile("local")
public class KafkaLeadConsumer {

    @KafkaListener(topics = "lead-events", groupId = "lead-api-group")
    public void consume(String message) {
        System.out.println(" [KAFKA CONSUMER] Received: " + message);
    }
}