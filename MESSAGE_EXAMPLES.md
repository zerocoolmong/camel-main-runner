# Message Format Examples

This document provides examples of request and response messages for the Java Executor Service.

## Request Messages

### Example 1: Basic Execution with Arguments

```json
{
  "JobId": "job-2025-001",
  "JarPath": "C:/apps/calculator/calculator.jar",
  "Arguments": ["add", "10", "20"]
}
```

This executes: `java -jar C:/apps/calculator/calculator.jar add 10 20`

### Example 2: Using Default JAR Path

```json
{
  "JobId": "job-2025-002",
  "Arguments": ["--config", "production.yml", "--verbose"]
}
```

Uses the default JAR path from `application.properties`

### Example 3: No Arguments

```json
{
  "JobId": "job-2025-003",
  "JarPath": "C:/apps/report-generator/app.jar",
  "Arguments": []
}
```

Executes: `java -jar C:/apps/report-generator/app.jar`

### Example 4: Complex Arguments with Paths

```json
{
  "JobId": "job-2025-004",
  "JarPath": "C:/apps/data-processor/processor.jar",
  "Arguments": [
    "--input",
    "C:/data/input.csv",
    "--output",
    "C:/data/output.json",
    "--format",
    "json",
    "--compress"
  ]
}
```

### Example 5: Database Migration Example

```json
{
  "JobId": "migration-20251228-001",
  "JarPath": "C:/apps/liquibase/liquibase.jar",
  "Arguments": [
    "--changeLogFile=changelog.xml",
    "--url=jdbc:postgresql://localhost:5432/mydb",
    "--username=admin",
    "update"
  ]
}
```

## Response Messages

### Example 1: Successful Execution

```json
{
  "JobId": "job-2025-001",
  "ExitCode": 0,
  "Success": true,
  "Output": "Result: 30\nCalculation completed successfully.",
  "ExecutionTimeMs": 245,
  "Timestamp": "2025-12-28T14:30:45.123"
}
```

### Example 2: Failed Execution (Non-Zero Exit Code)

```json
{
  "JobId": "job-2025-002",
  "ExitCode": 1,
  "Success": false,
  "Output": "Error: Invalid configuration file\nValidation failed",
  "Error": "Process exited with code: 1",
  "ExecutionTimeMs": 1523,
  "Timestamp": "2025-12-28T14:35:12.456"
}
```

### Example 3: Error - JAR File Not Found

```json
{
  "JobId": "job-2025-003",
  "ExitCode": -1,
  "Success": false,
  "Error": "Cannot run program \"java\": CreateProcess error=2, The system cannot find the file specified",
  "Output": "",
  "Timestamp": "2025-12-28T14:40:22.789"
}
```

### Example 4: Timeout Error

```json
{
  "JobId": "job-2025-004",
  "ExitCode": -1,
  "Success": false,
  "Error": "Process execution timeout (5 minutes)",
  "Output": "",
  "Timestamp": "2025-12-28T14:45:33.012"
}
```

### Example 5: JSON Parsing Error

```json
{
  "JobId": "unknown",
  "ExitCode": -1,
  "Success": false,
  "Error": "Unrecognized token 'invalid': was expecting (JSON String, Number, Array, Object or token 'null', 'true' or 'false')",
  "Output": "",
  "Timestamp": "2025-12-28T14:50:44.345"
}
```

## C# Examples for .NET Core

### Send Request Message

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

// Declare queue
channel.QueueDeclare(queue: "java.exec.queue",
                     durable: true,
                     exclusive: false,
                     autoDelete: false,
                     arguments: null);

// Create request
var request = new
{
    JobId = Guid.NewGuid().ToString(),
    JarPath = "C:/apps/my-app/app.jar",
    Arguments = new[] { "param1", "param2" }
};

string jsonMessage = JsonSerializer.Serialize(request);
byte[] body = Encoding.UTF8.GetBytes(jsonMessage);

// Set persistent delivery
var properties = channel.CreateBasicProperties();
properties.Persistent = true;

// Publish message
channel.BasicPublish(exchange: "",
                    routingKey: "java.exec.queue",
                    basicProperties: properties,
                    body: body);

Console.WriteLine($"Sent: {jsonMessage}");
```

### Receive Response Message

```csharp
using RabbitMQ.Client;
using RabbitMQ.Client.Events;
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

// Declare queue
channel.QueueDeclare(queue: "java.result.queue",
                     durable: true,
                     exclusive: false,
                     autoDelete: false,
                     arguments: null);

var consumer = new EventingBasicConsumer(channel);
consumer.Received += (model, ea) =>
{
    byte[] body = ea.Body.ToArray();
    string message = Encoding.UTF8.GetString(body);

    // Deserialize response
    var response = JsonSerializer.Deserialize<JsonDocument>(message);

    string jobId = response.RootElement.GetProperty("JobId").GetString();
    bool success = response.RootElement.GetProperty("Success").GetBoolean();
    int exitCode = response.RootElement.GetProperty("ExitCode").GetInt32();
    string output = response.RootElement.GetProperty("Output").GetString();

    Console.WriteLine($"Job {jobId}: Success={success}, ExitCode={exitCode}");
    Console.WriteLine($"Output: {output}");

    // Acknowledge message
    channel.BasicAck(deliveryTag: ea.DeliveryTag, multiple: false);
};

// Start consuming
channel.BasicConsume(queue: "java.result.queue",
                     autoAck: false,
                     consumer: consumer);

Console.WriteLine("Waiting for responses...");
Console.ReadLine();
```

### Complete .NET Core Service Example

```csharp
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using RabbitMQ.Client;
using RabbitMQ.Client.Events;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;

public class JavaExecutorService : BackgroundService
{
    private readonly ILogger<JavaExecutorService> _logger;
    private IConnection _connection;
    private IModel _channel;
    private const string REQUEST_QUEUE = "java.exec.queue";
    private const string RESPONSE_QUEUE = "java.result.queue";

    public JavaExecutorService(ILogger<JavaExecutorService> logger)
    {
        _logger = logger;
        InitializeRabbitMQ();
    }

    private void InitializeRabbitMQ()
    {
        var factory = new ConnectionFactory()
        {
            HostName = "172.16.2.171",
            Port = 5672,
            UserName = "inb",
            Password = "inb123",
            AutomaticRecoveryEnabled = true,
            NetworkRecoveryInterval = TimeSpan.FromSeconds(10)
        };

        _connection = factory.CreateConnection();
        _channel = _connection.CreateModel();

        _channel.QueueDeclare(REQUEST_QUEUE, durable: true, exclusive: false, autoDelete: false);
        _channel.QueueDeclare(RESPONSE_QUEUE, durable: true, exclusive: false, autoDelete: false);
    }

    protected override Task ExecuteAsync(CancellationToken stoppingToken)
    {
        stoppingToken.ThrowIfCancellationRequested();

        var consumer = new EventingBasicConsumer(_channel);
        consumer.Received += (model, ea) =>
        {
            try
            {
                var body = ea.Body.ToArray();
                var message = Encoding.UTF8.GetString(body);
                var response = JsonSerializer.Deserialize<JavaExecutionResponse>(message);

                _logger.LogInformation(
                    "Received response for Job {JobId}: Success={Success}, ExitCode={ExitCode}",
                    response.JobId, response.Success, response.ExitCode);

                if (response.Success)
                {
                    _logger.LogInformation("Output: {Output}", response.Output);
                }
                else
                {
                    _logger.LogError("Error: {Error}", response.Error);
                }

                _channel.BasicAck(ea.DeliveryTag, false);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error processing response");
                _channel.BasicNack(ea.DeliveryTag, false, true);
            }
        };

        _channel.BasicConsume(RESPONSE_QUEUE, autoAck: false, consumer: consumer);

        return Task.CompletedTask;
    }

    public void ExecuteJavaApplication(string jobId, string jarPath, params string[] args)
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

        _channel.BasicPublish("", REQUEST_QUEUE, properties, body);

        _logger.LogInformation("Sent execution request for Job {JobId}", jobId);
    }

    public override void Dispose()
    {
        _channel?.Close();
        _connection?.Close();
        base.Dispose();
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
```

## Python Example

```python
import pika
import json
import uuid

# Connection parameters
credentials = pika.PlainCredentials('inb', 'inb123')
parameters = pika.ConnectionParameters(
    host='172.16.2.171',
    port=5672,
    credentials=credentials
)

connection = pika.BlockingConnection(parameters)
channel = connection.channel()

# Declare queues
channel.queue_declare(queue='java.exec.queue', durable=True)
channel.queue_declare(queue='java.result.queue', durable=True)

# Send request
request = {
    'JobId': str(uuid.uuid4()),
    'JarPath': 'C:/apps/my-app/app.jar',
    'Arguments': ['arg1', 'arg2']
}

channel.basic_publish(
    exchange='',
    routing_key='java.exec.queue',
    body=json.dumps(request),
    properties=pika.BasicProperties(delivery_mode=2)
)

print(f"Sent: {request}")

# Receive response
def callback(ch, method, properties, body):
    response = json.loads(body)
    print(f"Job {response['JobId']}: Success={response['Success']}")
    print(f"Output: {response['Output']}")
    ch.basic_ack(delivery_tag=method.delivery_tag)

channel.basic_consume(
    queue='java.result.queue',
    on_message_callback=callback
)

print('Waiting for responses...')
channel.start_consuming()
```

## Testing with RabbitMQ Management UI

1. Open RabbitMQ Management UI: `http://172.16.2.171:15672`
2. Navigate to Queues tab
3. Click on `java.exec.queue`
4. Scroll to "Publish message"
5. Paste this in the Payload:

```json
{
  "JobId": "test-001",
  "JarPath": "C:/apps/my-java-app/app.jar",
  "Arguments": ["test", "argument"]
}
```

6. Click "Publish message"
7. Check `java.result.queue` for the response
