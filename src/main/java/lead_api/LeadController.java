package lead_api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@RestController
public class LeadController {

    private static final String QUEUE_NAME = "lead-queue";

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @PostMapping("/api/leads")
    public String receiveLead(@RequestBody Lead lead) {
        String message = "New lead: " + lead.getName() + " (" + lead.getEmail() + ") from " + lead.getCompany();

        rabbitTemplate.convertAndSend(QUEUE_NAME, message);

        System.out.println(" [API] Published to RabbitMQ: " + message);

        return "Lead received and published successfully";
    }
}