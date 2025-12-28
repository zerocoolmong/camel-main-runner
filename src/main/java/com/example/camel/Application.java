package com.example.camel;

import org.apache.camel.CamelContext;
import org.apache.camel.component.amqp.AMQPComponent;
import org.apache.camel.main.BaseMainSupport;
import org.apache.camel.main.Main;
import org.apache.camel.main.MainListener;
import org.apache.qpid.jms.JmsConnectionFactory;

public class Application {

    public static void main(String[] args) throws Exception {

        Main main = new Main();

        main.configure().addRoutesBuilder(new RabbitMqRoute());

        main.addMainListener(new MainListener() {

            @Override
            public void beforeInitialize(BaseMainSupport main) {

            }

            @Override
            public void beforeConfigure(BaseMainSupport main) {

            }

            @Override
            public void afterConfigure(BaseMainSupport main) {

            }

            @Override
            public void beforeStart(BaseMainSupport main) {

            }

            @Override
            public void afterStart(BaseMainSupport main) {

            }

            @Override
            public void beforeStop(BaseMainSupport main) {

            }

            @Override
            public void afterStop(BaseMainSupport main) {

            }

            public void beforeInitialize(Main main) {
            }

            public void afterInitialize(Main main) {
                try {
                    CamelContext ctx = main.getCamelContext();

                    String uri = "amqp://inb:inb123@172.16.2.171:5672/";
                    JmsConnectionFactory factory = new JmsConnectionFactory(uri);

                    AMQPComponent amqp = new AMQPComponent();
                    amqp.setConnectionFactory(factory);

                    ctx.addComponent("amqp", amqp);

                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        });

        main.run();
    }
}
