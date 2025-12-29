# Quick Start Guide

## What Was Created

A complete Apache Camel 4 microservice that enables .NET Core applications to execute Java applications via RabbitMQ.

## Project Files

```
camel-main-runner/
├── src/main/
│   ├── java/com/example/camel/
│   │   ├── Application.java          # Main entry point, AMQP configuration
│   │   └── RunnerRoute.java          # Execution route with error handling
│   └── resources/
│       └── application.properties    # RabbitMQ and app configuration
├── pom.xml                            # Maven build configuration
├── start-camel-runner.bat            # Windows startup script
├── README.md                          # Full documentation
├── MESSAGE_EXAMPLES.md                # Request/response examples
└── QUICKSTART.md                      # This file
```

## Configuration

Edit `src/main/resources/application.properties` to match your environment:

```properties
# Your RabbitMQ Server
rabbitmq.host=172.16.2.171
rabbitmq.port=5672
rabbitmq.username=inb
rabbitmq.password=inb123

# Queue Names (must match .NET Core configuration)
rabbitmq.queue.request=java.exec.queue
rabbitmq.queue.response=java.result.queue

# Default Java application path
java.app.jar.path=C:/apps/my-java-app/app.jar
```

## Build & Run

### 1. Build the Project

```bash
mvn clean package
```

This creates: `target/camel-runner-1.0.jar` (~11 MB)

### 2. Start the Service

**Option A - Using batch file:**
```bash
start-camel-runner.bat
```

**Option B - Using command line:**
```bash
java -jar target/camel-runner-1.0.jar
```

### 3. Verify Startup

You should see:
```
Starting Camel Java Executor Service...
RabbitMQ Host: 172.16.2.171
Request Queue: java.exec.queue
Response Queue: java.result.queue
Configuration loaded successfully
AMQP component configured successfully
```

## How It Works

### Flow Diagram

```
┌──────────────┐      ┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│  .NET Core   │─────>│   RabbitMQ   │─────>│    Camel     │─────>│ Java App     │
│  Application │      │ Request Queue│      │   Service    │      │ (JAR file)   │
└──────────────┘      └──────────────┘      └──────────────┘      └──────────────┘
       ^                                              │
       │               ┌──────────────┐              │
       └───────────────│   RabbitMQ   │<─────────────┘
                       │Response Queue│
                       └──────────────┘
```

### Message Flow

1. **Request**: .NET Core sends JSON to `java.exec.queue`
2. **Execution**: Camel receives message, executes Java app
3. **Response**: Camel sends results to `java.result.queue`
4. **Callback**: .NET Core receives execution results

## Test from .NET Core

### Install RabbitMQ Client

```bash
dotnet add package RabbitMQ.Client
```

### Send Execution Request

```csharp
using RabbitMQ.Client;
using System.Text;
using System.Text.Json;

var factory = new ConnectionFactory()
{
    HostName = "172.16.2.171",
    Port = 5672,
    UserName = "inb",
    Password = "inb123"
};

using var connection = factory.CreateConnection();
using var channel = connection.CreateModel();

// Send request
var request = new
{
    JobId = Guid.NewGuid().ToString(),
    JarPath = "C:/apps/my-java-app/app.jar",
    Arguments = new[] { "param1", "param2" }
};

string message = JsonSerializer.Serialize(request);
byte[] body = Encoding.UTF8.GetBytes(message);

channel.BasicPublish(
    exchange: "",
    routingKey: "java.exec.queue",
    basicProperties: null,
    body: body);

Console.WriteLine("Request sent!");
```

### Receive Response

```csharp
using RabbitMQ.Client;
using RabbitMQ.Client.Events;
using System.Text;

var factory = new ConnectionFactory()
{
    HostName = "172.16.2.171",
    Port = 5672,
    UserName = "inb",
    Password = "inb123"
};

using var connection = factory.CreateConnection();
using var channel = connection.CreateModel();

var consumer = new EventingBasicConsumer(channel);
consumer.Received += (model, ea) =>
{
    var body = ea.Body.ToArray();
    var message = Encoding.UTF8.GetString(body);
    Console.WriteLine($"Response: {message}");
    channel.BasicAck(ea.DeliveryTag, false);
};

channel.BasicConsume(
    queue: "java.result.queue",
    autoAck: false,
    consumer: consumer);

Console.WriteLine("Listening for responses...");
Console.ReadLine();
```

## Request Format

```json
{
  "JobId": "unique-job-id",
  "JarPath": "C:/apps/my-java-app/app.jar",
  "Arguments": ["arg1", "arg2", "arg3"]
}
```

## Response Format

**Success:**
```json
{
  "JobId": "unique-job-id",
  "ExitCode": 0,
  "Success": true,
  "Output": "Application output...",
  "ExecutionTimeMs": 1234,
  "Timestamp": "2025-12-28T14:30:45"
}
```

**Error:**
```json
{
  "JobId": "unique-job-id",
  "ExitCode": -1,
  "Success": false,
  "Error": "Error description",
  "Output": "",
  "Timestamp": "2025-12-28T14:30:45"
}
```

## Common Issues

### Connection Refused
- Verify RabbitMQ is running: `rabbitmqctl status`
- Check firewall settings
- Verify credentials in `application.properties`

### Java Application Not Found
- Check the path in `JarPath` field
- Ensure Java 21 is in PATH: `java -version`
- Verify file permissions

### Timeout Errors
- Default timeout: 5 minutes
- To change: Edit `RunnerRoute.java` line 118
- Increase if your Java app takes longer

## Running as Windows Service

To run as a Windows service, you can use:

1. **NSSM (Non-Sucking Service Manager)**
   ```bash
   nssm install CamelJavaExecutor "java.exe" "-jar" "D:\path\to\camel-runner-1.0.jar"
   nssm start CamelJavaExecutor
   ```

2. **WinSW (Windows Service Wrapper)**
   - Create XML config pointing to the JAR
   - Install as service

## Monitoring

### Check RabbitMQ Queues
1. Open: `http://172.16.2.171:15672`
2. Login with RabbitMQ credentials
3. Navigate to "Queues" tab
4. Check message counts on:
   - `java.exec.queue`
   - `java.result.queue`

### Application Logs
The application logs to console:
- Connection status
- Received requests
- Execution progress
- Results and errors

To save logs to file:
```bash
java -jar target/camel-runner-1.0.jar > logs/camel-service.log 2>&1
```

## Next Steps

1. Test with sample Java application
2. Integrate with your .NET Core application
3. Set up monitoring and alerting
4. Configure as Windows service for production
5. Review security settings (credentials, network access)

## More Information

- **Full Documentation**: See `README.md`
- **Message Examples**: See `MESSAGE_EXAMPLES.md`
- **Apache Camel Docs**: https://camel.apache.org/
- **RabbitMQ Docs**: https://www.rabbitmq.com/documentation.html

## Support

For issues or questions:
1. Check the logs for error messages
2. Verify RabbitMQ connection
3. Test with simple Java application first
4. Review configuration in `application.properties`
