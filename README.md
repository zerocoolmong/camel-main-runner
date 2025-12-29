# Java Executor Service - Apache Camel 4 + RabbitMQ

A microservice that executes Java applications based on messages received from RabbitMQ, enabling .NET Core applications to execute Java processes remotely.

## Architecture

```
.NET Core App --> RabbitMQ (Request Queue) --> Camel Service --> Java Application
                                                      |
                                                      v
.NET Core App <-- RabbitMQ (Response Queue) <-- Camel Service
```

## Features

- Execute Java applications with command-line arguments via RabbitMQ
- Asynchronous execution with response callbacks
- Configurable via properties file
- Error handling and timeout management
- Execution time tracking
- Works with .NET Core and other AMQP clients

## Prerequisites

- Java 21 (JDK)
- Maven 3.6+
- RabbitMQ Server
- Target Java application to execute

## Configuration

Edit `src/main/resources/application.properties`:

```properties
# RabbitMQ Connection
rabbitmq.host=172.16.2.171
rabbitmq.port=5672
rabbitmq.username=inb
rabbitmq.password=inb123
rabbitmq.vhost=/

# Queue Names
rabbitmq.queue.request=java.exec.queue
rabbitmq.queue.response=java.result.queue

# Consumer Settings
rabbitmq.concurrent.consumers=1

# Default Java Application Path
java.app.jar.path=C:/apps/my-java-app/app.jar
```

## Building

```bash
mvn clean package
```

This creates an executable uber-jar: `target/camel-runner-1.0.jar`

## Running

```bash
java -jar target/camel-runner-1.0.jar
```

Or use the provided batch file:
```bash
start-camel-runner.bat
```

## Message Format

### Request Message (from .NET Core to RabbitMQ)

Send a JSON message to the request queue:

```json
{
  "JobId": "unique-job-id-123",
  "JarPath": "C:/apps/my-java-app/app.jar",
  "Arguments": ["arg1", "arg2", "arg3"]
}
```

**Fields:**
- `JobId` (required): Unique identifier for tracking the job
- `JarPath` (optional): Path to JAR file. If not provided, uses default from properties
- `Arguments` (optional): Array of command-line arguments

### Response Message (from Camel to RabbitMQ)

The service sends a JSON response to the response queue:

```json
{
  "JobId": "unique-job-id-123",
  "ExitCode": 0,
  "Success": true,
  "Output": "Application output here...",
  "ExecutionTimeMs": 1523,
  "Timestamp": "2025-12-28T23:45:30"
}
```

**Success Response Fields:**
- `JobId`: Original job identifier
- `ExitCode`: Process exit code (0 = success)
- `Success`: Boolean indicating success
- `Output`: Standard output from the Java application
- `ExecutionTimeMs`: Execution time in milliseconds
- `Timestamp`: ISO 8601 timestamp

**Error Response Fields:**
```json
{
  "JobId": "unique-job-id-123",
  "ExitCode": -1,
  "Success": false,
  "Error": "Error description",
  "Output": "",
  "Timestamp": "2025-12-28T23:45:30"
}
```

## .NET Core Integration Example

```csharp
using RabbitMQ.Client;
using RabbitMQ.Client.Events;
using System.Text;
using System.Text.Json;

public class JavaExecutorClient
{
    private readonly IConnection _connection;
    private readonly IModel _channel;
    private const string REQUEST_QUEUE = "java.exec.queue";
    private const string RESPONSE_QUEUE = "java.result.queue";

    public JavaExecutorClient()
    {
        var factory = new ConnectionFactory()
        {
            HostName = "172.16.2.171",
            Port = 5672,
            UserName = "inb",
            Password = "inb123",
            VirtualHost = "/"
        };

        _connection = factory.CreateConnection();
        _channel = _connection.CreateModel();

        // Declare queues
        _channel.QueueDeclare(REQUEST_QUEUE, durable: true, exclusive: false);
        _channel.QueueDeclare(RESPONSE_QUEUE, durable: true, exclusive: false);
    }

    public void ExecuteJavaApp(string jobId, string jarPath, string[] args)
    {
        var request = new
        {
            JobId = jobId,
            JarPath = jarPath,
            Arguments = args
        };

        string message = JsonSerializer.Serialize(request);
        byte[] body = Encoding.UTF8.GetBytes(message);

        var properties = _channel.CreateBasicProperties();
        properties.Persistent = true;

        _channel.BasicPublish(exchange: "", routingKey: REQUEST_QUEUE,
                            basicProperties: properties, body: body);

        Console.WriteLine($"Sent execution request: {jobId}");
    }

    public void ListenForResponses(Action<JavaExecutionResponse> onResponse)
    {
        var consumer = new EventingBasicConsumer(_channel);
        consumer.Received += (model, ea) =>
        {
            var body = ea.Body.ToArray();
            var message = Encoding.UTF8.GetString(body);
            var response = JsonSerializer.Deserialize<JavaExecutionResponse>(message);
            onResponse(response);
            _channel.BasicAck(ea.DeliveryTag, false);
        };

        _channel.BasicConsume(queue: RESPONSE_QUEUE, autoAck: false, consumer: consumer);
    }
}

public class JavaExecutionResponse
{
    public string JobId { get; set; }
    public int ExitCode { get; set; }
    public bool Success { get; set; }
    public string Output { get; set; }
    public long ExecutionTimeMs { get; set; }
    public string Timestamp { get; set; }
    public string Error { get; set; }
}

// Usage
var client = new JavaExecutorClient();

// Send execution request
client.ExecuteJavaApp(
    jobId: Guid.NewGuid().ToString(),
    jarPath: "C:/apps/my-java-app/app.jar",
    args: new[] { "param1", "param2" }
);

// Listen for responses
client.ListenForResponses(response =>
{
    Console.WriteLine($"Job {response.JobId}: Success={response.Success}, ExitCode={response.ExitCode}");
    Console.WriteLine($"Output: {response.Output}");
});
```

## Timeout Configuration

Default timeout: 5 minutes per execution

To modify, edit `RunnerRoute.java` line 118:
```java
boolean finished = process.waitFor(5, TimeUnit.MINUTES);
```

## Error Handling

The service handles errors gracefully:
- Invalid JSON messages
- Missing Java application files
- Process execution failures
- Timeout conditions

All errors are returned as response messages with `Success: false` and error details.

## Logging

The application logs to console:
- Startup information
- Received requests
- Execution progress
- Completion status
- Errors and exceptions

## Queue Setup in RabbitMQ

Create the queues in RabbitMQ (if not auto-created):

```bash
# Using rabbitmqadmin
rabbitmqadmin declare queue name=java.exec.queue durable=true
rabbitmqadmin declare queue name=java.result.queue durable=true
```

Or use RabbitMQ Management UI.

## Project Structure

```
camel-main-runner/
├── src/main/
│   ├── java/com/example/camel/
│   │   ├── Application.java      # Main application and AMQP configuration
│   │   └── RunnerRoute.java      # Camel route for execution logic
│   └── resources/
│       └── application.properties # Configuration file
├── pom.xml                        # Maven dependencies
└── README.md                      # This file
```

## Dependencies

- Apache Camel 4.3.0 (camel-main, camel-amqp)
- Apache Qpid JMS 2.6.0 (AMQP client)
- Jackson 2.17.1 (JSON processing)
- Java 21

## Troubleshooting

**Connection refused:**
- Verify RabbitMQ is running
- Check host/port in application.properties
- Verify credentials

**Java application not found:**
- Check the JarPath in your request
- Verify the path is accessible from the server
- Update default path in application.properties

**Timeout errors:**
- Increase timeout in RunnerRoute.java
- Check if Java application is hanging
- Review application logs

## License

MIT License
