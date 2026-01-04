package com.automaton.camel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class RunnerRoute extends RouteBuilder {

    private final Properties properties;

    public RunnerRoute(Properties properties) {
        this.properties = properties;
        System.out.println("=== RunnerRoute instance created ===");
    }

    @Override
    public void configure() {

        String requestQueue = properties.getProperty("rabbitmq.queue.request");
        String responseQueue = properties.getProperty("rabbitmq.queue.response");

        System.out.println("=== RunnerRoute.configure() called ===");
        System.out.println("Configuring route to consume from: " + requestQueue);
        System.out.println("Responses will be sent to: " + responseQueue);

        log.info("Configuring route to consume from: {}", requestQueue);
        log.info("Responses will be sent to: {}", responseQueue);

        // Error handler for the route
        onException(Exception.class)
                .handled(true)
                .process(exchange -> {
                    Exception cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                    ObjectMapper mapper = new ObjectMapper();
                    ObjectNode errorResponse = mapper.createObjectNode();

                    // Try to get JobId from original message
                    String jobId = "unknown";
                    try {
                        String originalBody = exchange.getIn().getHeader("OriginalBody", String.class);
                        if (originalBody != null) {
                            JsonNode request = mapper.readTree(originalBody);
                            if (request.has("JobId")) {
                                jobId = request.get("JobId").asText();
                            }
                        }
                    } catch (Exception e) {
                        // Ignore
                    }

                    errorResponse.put("JobId", jobId);
                    errorResponse.put("ExitCode", -1);
                    errorResponse.put("Success", false);
                    errorResponse.put("Error", cause.getMessage());
                    errorResponse.put("Output", "");
                    errorResponse.put("Timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));

                    exchange.getIn().setBody(mapper.writeValueAsString(errorResponse));
                    log.error("Error processing job {}: {}", jobId, cause.getMessage(), cause);
                })
                .to("rabbitmq:" + responseQueue);

        // Main route for executing Java applications
        from("rabbitmq:direct-exchange?queue="
                + requestQueue
                + "&declare=false")
                .routeId("java-executor-route")
                .log("========== RECEIVED MESSAGE ==========")
                //.log("Message Body: ${body}")
                //.log("======================================")


                // Store original body for error handling
                .process(exchange -> {
                    exchange.getIn().setHeader("OriginalBody", exchange.getIn().getBody(String.class));
                })

                // Process the message and execute Java application
                .process(exchange -> {
                    System.out.println("=== PROCESSING MESSAGE IN RunnerRoute ===");
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode request = mapper.readTree(exchange.getIn().getBody(String.class));

                    log.info("Request response mong camel: {}", request);

                    // Parse Body
                    JsonNode body = request.has("Body") ? request.get("Body") : null;

                    // Parse Headers
                    JsonNode headers = request.has("Headers") ? request.get("Headers") : null;

                    // Parse SecurityContext from Headers
                    JsonNode securityContext = null;
                    if (headers != null && headers.has("SecurityContext")) {
                        securityContext = headers.get("SecurityContext");
                    }

                    log.info("Body: {}", body);
                    log.info("Headers: {}", headers);
                    log.info("SecurityContext: {}", securityContext);

                    // Extract request parameters
                    String jobId = request.has("JobId") ? request.get("JobId").asText() : "unknown";

                    // Example: Extract values from Body
                    String exampleBodyField = (body != null && body.has("Environment"))
                        ? body.get("Environment").asText()
                        : null;

                    // Example: Extract values from SecurityContext
                    String userId = (securityContext != null && securityContext.has("UserId"))
                        ? securityContext.get("UserId").asText()
                        : null;
                    String token = (securityContext != null && securityContext.has("Token"))
                        ? securityContext.get("Token").asText()
                        : null;
//                    String jarPath = request.has("JarPath")
//                            ? request.get("JarPath").asText()
//                            : properties.getProperty("java.app.jar.path");

//                    List<String> arguments = new ArrayList<>();
//                    if (request.has("Arguments") && request.get("Arguments").isArray()) {
//                        for (JsonNode arg : request.get("Arguments")) {
//                            arguments.add(arg.asText());
//                        }
//                    }

                    List<String> arguments = List.of(
                            "--email", "automation.inb.dev.test101@yopmail.com",
                            "--password", "EpfNDE8vVgYpMjV@",
                            "--org", "Demo",
                            "--env", "CRUDTest",
                            "--browser", "api"
                    );

                    // Build command
                    List<String> command = new ArrayList<>();
                    command.add("java");
                    command.add("-jar");
                    command.add("D:\\SELISE\\INB\\Automation\\selise-automation-cloudapp\\target\\INB-AUTOMATION-TOOL-9.0.1-dev.jar");
                    command.addAll(arguments);

                    log.info("Executing Job " + jobId + ": " + String.join(" ", command));

                    // Execute the process
                    ProcessBuilder processBuilder = new ProcessBuilder(command);
                    processBuilder.redirectErrorStream(true);

                    long startTime = System.currentTimeMillis();
                    Process process = processBuilder.start();

                    // Read output
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(process.getInputStream()));
                    StringBuilder output = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }

                    // Wait for completion (with timeout)
                    boolean finished = process.waitFor(5, TimeUnit.MINUTES);
                    int exitCode = finished ? process.exitValue() : -1;

                    if (!finished) {
                        process.destroyForcibly();
                        throw new RuntimeException("Process execution timeout (5 minutes)");
                    }

                    long executionTime = System.currentTimeMillis() - startTime;

                    // Build response
                    ObjectNode response = mapper.createObjectNode();
                    response.put("JobId", jobId);
                    response.put("ExitCode", exitCode);
                    response.put("Success", exitCode == 0);
                    response.put("Output", output.toString().trim());
                    response.put("ExecutionTimeMs", executionTime);
                    response.put("Timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));

                    if (exitCode != 0) {
                        response.put("Error", "Process exited with code: " + exitCode);
                    }

                    exchange.getIn().setBody(mapper.writeValueAsString(response));

                    log.info("Job " + jobId + " completed with exit code: " + exitCode +
                            " in " + executionTime + "ms");
                })

                // Send response back to RabbitMQ
                .to("rabbitmq:" + responseQueue)
                .log("========== RESPONSE SENT ==========")
                .log("Response: ${body}")
                .log("===================================");
    }
}
