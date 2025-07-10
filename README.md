# Tunnel Proxy Java Client

A Java client for connecting to the tunnel proxy server deployed on Render.com. This provides a secure tunnel from your local development server to the internet.

## Requirements

- Java 11 or higher
- Maven 3.6+ 
- A running tunnel proxy server (deployed on Render.com)

## Quick Start

```bash
# Build
mvn clean compile package

# Run
java -jar target/tunnel-client.jar
```


## Configuration

The client uses environment variables for configuration:

| Variable        | Description                         | Default                       |
|-----------------|-------------------------------------|-------------------------------|
| `TUNNEL_SERVER` | WebSocket URL of your tunnel server | `wss://your-app.onrender.com` |
| `LOCAL_PORT`    | Port of your local server           | `8080`                        |
| `TUNNEL_NAME`   | The name of the tunned              | `dev1`                        |

## Features

- ✅ **Request Forwarding**: Forwards HTTP requests to your local server
- ✅ **JSON Support**: Handles JSON requests and responses
- ✅ **Error Handling**: Robust error handling for production use
- ✅ **Health Checks**: Verifies local server is running
- ✅ **Logging**: Detailed request/response logging

## Usage Examples

### Basic Usage
```bash
# Start tunnel for localhost:8080
TUNNEL_SERVER=wss://opi-tunnel.onrender.com LOCAL_PORT=8080 java -jar target/tunnel-client.jar
```

### Custom Port and tunnel name
```bash
# Start tunnel for localhost:8080 and name it "dev1"
TUNNEL_SERVER=wss://opi-tunnel.onrender.com LOCAL_PORT=8080 TUNNEL_NAME=dev1 java -jar target/tunnel-client.jar
```