package lead_api;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class LeadConsumer {

    @RabbitListener(queues = "lead-queue")
    public void receiveMessage(String message) {
        System.out.println(" [CONSUMER] New lead notification: " + message);

        // This is where real logic would go later, e.g.:
        // - send a Slack/email notification
        // - save to a database
        // - update a dashboard
    }
}