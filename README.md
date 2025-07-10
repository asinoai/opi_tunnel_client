# Tunnel Proxy Java Client

A Java client for connecting to the tunnel proxy server deployed on Render.com. This provides a secure tunnel from your local development server to the internet.

## Requirements

- Java 11 or higher
- Maven 3.6+ or Gradle 6.0+
- A running tunnel proxy server (deployed on Render.com)

## Quick Start

```bash
# Build
mvn clean compile package

# Run
java -jar target/tunnel.jar
```


## Configuration

The client uses environment variables for configuration:

| Variable        | Description                         | Default                       |
|-----------------|-------------------------------------|-------------------------------|
| `TUNNEL_SERVER` | WebSocket URL of your tunnel server | `wss://your-app.onrender.com` |
| `LOCAL_PORT`    | Port of your local server           | `8080`                        |
| `TUNNEL_NAME`   | The name of the tunned              | `dev1`                        |

## Features

- ✅ **Automatic Reconnection**: Reconnects automatically if connection drops
- ✅ **Request Forwarding**: Forwards HTTP requests to your local server
- ✅ **JSON Support**: Handles JSON requests and responses
- ✅ **Error Handling**: Robust error handling for production use
- ✅ **Health Checks**: Verifies local server is running
- ✅ **Logging**: Detailed request/response logging
- ✅ **Custom Subdomains**: Support for custom subdomain names

## Usage Examples

### Basic Usage
```bash
# Start tunnel for localhost:3000
TUNNEL_SERVER=wss://opi-tunnel.onrender.com LOCAL_PORT=8080 java -jar target/tunnel.jar
```

### Custom Port and tunnel name
```bash
# Tunnel localhost:8080
TUNNEL_SERVER=wss://opi-tunnel.onrender.com LOCAL_PORT=8080 TUNNEL_NAME=dev1 java -jar target/tunnel.jar
```