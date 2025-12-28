package com.example.camel;

import org.apache.camel.builder.RouteBuilder;

public class RabbitMqRoute extends RouteBuilder {

    @Override
    public void configure() {

        from("rabbitmq:localhost:5672/automation.exchange"
           + "?queue=automation.queue"
           + "&routingKey=automation.execute"
           + "&autoDelete=false")
        .routeId("rabbitmq-command-executor")

        .log("Received message: ${body}")

        // Execute command from message body
        .to("exec:java?args=${body}")

        .log("Command executed successfully");
    }
}
