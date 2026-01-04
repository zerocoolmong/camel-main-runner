package com.automaton.camel;

import org.apache.camel.CamelContext;
import org.apache.camel.component.rabbitmq.RabbitMQComponent;
import org.apache.camel.main.Main;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Application {


    private static final Properties properties = new Properties();

    public static void main(String[] args) throws Exception {

        // Load configuration
        loadProperties();

        Main main = new Main();

        // Configure RabbitMQ component
        configureRabbitMqComponent(main);

        // Add route
        main.configure().addRoutesBuilder(new RunnerRoute(properties));

        System.out.println("Starting Camel Java Executor Service...");
        System.out.println("RabbitMQ Host: " + properties.getProperty("rabbitmq.host"));
        System.out.println("Request Queue: " + properties.getProperty("rabbitmq.queue.request"));
        System.out.println("Response Queue: " + properties.getProperty("rabbitmq.queue.response"));

        main.run();
    }

    private static void configureRabbitMqComponent(Main main) {
        main.addMainListener(new org.apache.camel.main.MainListenerSupport() {
            @Override
            public void afterConfigure(org.apache.camel.main.BaseMainSupport baseMain) {
                try {
                    CamelContext context = baseMain.getCamelContext();

                    // Create and configure RabbitMQ component
                    RabbitMQComponent rabbitmq = new RabbitMQComponent();
                    rabbitmq.setHostname(properties.getProperty("rabbitmq.host", "localhost"));
                    rabbitmq.setPortNumber(Integer.parseInt(properties.getProperty("rabbitmq.port", "5672")));
                    rabbitmq.setUsername(properties.getProperty("rabbitmq.username", "guest"));
                    rabbitmq.setPassword(properties.getProperty("rabbitmq.password", "guest"));
                    rabbitmq.setVhost(properties.getProperty("rabbitmq.vhost", "/"));

                    context.addComponent("rabbitmq", rabbitmq);

                    System.out.println("RabbitMQ component configured successfully");
                    System.out.println("Connected to: " + rabbitmq.getHostname() + ":" + rabbitmq.getPortNumber());
                } catch (Exception ex) {
                    System.err.println("Failed to configure RabbitMQ component: " + ex.getMessage());
                    ex.printStackTrace();
                    throw new RuntimeException(ex);
                }
            }

            @Override
            public void afterStart(org.apache.camel.main.BaseMainSupport baseMain) {
                try {
                    CamelContext context = baseMain.getCamelContext();
                    System.out.println("\n===========================================");
                    System.out.println("Camel Context Started: " + context.getName());
                    System.out.println("Total Routes: " + context.getRoutes().size());
//                    context.getRoutes().forEach(route -> {
//                        System.out.println("  - Route ID: " + route.getId() +
//                                         " | Status: " + route.getRouteController().getRouteStatus(route.getId()));
//                    });
                    System.out.println("===========================================");
                    System.out.println("Listening on queue: " + properties.getProperty("rabbitmq.queue.request"));
                    System.out.println("Ready to process commands...\n");
                } catch (Exception ex) {
                    System.err.println("Error in afterStart: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
        });
    }

    private static void loadProperties() {
        try (InputStream input = Application.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (input == null) {
                System.err.println("Unable to find application.properties, using defaults");
                setDefaultProperties();
                return;
            }
            properties.load(input);
            System.out.println("Configuration loaded successfully");
        } catch (IOException ex) {
            System.err.println("Error loading properties: " + ex.getMessage());
            setDefaultProperties();
        }
    }

    private static void setDefaultProperties() {
        properties.setProperty("rabbitmq.host", "localhost");
        properties.setProperty("rabbitmq.port", "5672");
        properties.setProperty("rabbitmq.username", "guest");
        properties.setProperty("rabbitmq.password", "guest");
        properties.setProperty("rabbitmq.vhost", "/");
        properties.setProperty("rabbitmq.queue.request", "java.exec.queue");
        properties.setProperty("rabbitmq.queue.response", "java.result.queue");
        properties.setProperty("rabbitmq.concurrent.consumers", "1");
        properties.setProperty("java.app.jar.path", "app.jar");
    }

    public static Properties getProperties() {
        return properties;
    }
}
