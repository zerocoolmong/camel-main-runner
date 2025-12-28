package com.example.camel;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.camel.builder.RouteBuilder;

import java.io.*;
import java.util.*;

public class RabbitMqRoute extends RouteBuilder {

    @Override
    public void configure() {

        from("amqp:queue:java.exec.queue?concurrentConsumers=1")
        .process(e -> {

            ObjectMapper m = new ObjectMapper();
            JsonNode n = m.readTree(e.getIn().getBody(String.class));

            List<String> cmd = new ArrayList<>();
            cmd.add("java");
            cmd.add("-jar");
            cmd.add("C:/apps/my-java-app/app.jar");

            for (JsonNode a : n.get("Arguments")) {
                cmd.add(a.asText());
            }

            Process p = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start();

            String out = new BufferedReader(
                    new InputStreamReader(p.getInputStream()))
                    .lines()
                    .reduce("", (a,b) -> a + "\n" + b);

            int exit = p.waitFor();

            ObjectNode res = m.createObjectNode();
            res.put("JobId", n.get("JobId").asText());
            res.put("ExitCode", exit);
            res.put("Output", out);

            e.getIn().setBody(m.writeValueAsString(res));
        })
        .to("amqp:queue:java.result.queue");
    }
}
