@echo off
echo =========================================
echo Java Executor Service - Apache Camel 4
echo =========================================
echo.

cd /d D:\SELISE\INB\Automation\automation-apache-camel\camel-main-runner

if not exist target\camel-runner-1.0.jar (
    echo ERROR: JAR file not found!
    echo Please run: mvn clean package
    echo.
    pause
    exit /b 1
)

echo Starting Camel Java Executor Service...
echo.

java -jar target\camel-runner-1.0.jar

echo.
echo Service stopped.
pause
