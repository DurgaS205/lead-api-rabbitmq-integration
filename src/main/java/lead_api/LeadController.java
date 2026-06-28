package lead_api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Optional;

@RestController
public class LeadController {

    private static final String QUEUE_NAME = "lead-queue";
    private static final String KAFKA_TOPIC = "lead-events";

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired(required = false)
    private KafkaTemplate<String, String> kafkaTemplate;

    @PostMapping("/api/leads")
    public String receiveLead(@RequestBody Lead lead) {
        String message = "New lead: " + lead.getName() + " (" + lead.getEmail() + ") from " + lead.getCompany();

        rabbitTemplate.convertAndSend(QUEUE_NAME, message);
        System.out.println(" [API] Published to RabbitMQ: " + message);

        if (kafkaTemplate != null) {
            kafkaTemplate.send(KAFKA_TOPIC, message);
            System.out.println(" [API] Published to Kafka: " + message);
        } else {
            System.out.println(" [API] Kafka not available in this environment - skipped");
        }

        return "Lead received and published successfully";
    }
}