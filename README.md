# Tunnel Proxy Java Client

A Java client for connecting to the tunnel proxy server deployed on Render.com. This provides a secure tunnel from your local development server to the internet.

## Requirements

- Java 11 or higher
- Maven 3.6+ or Gradle 6.0+
- A running tunnel proxy server (deployed on Render.com)

## Quick Start

### 1. Using the Easy Script (Recommended)

```bash
# Make script executable
chmod +x tunnel.sh

# Run with defaults
./tunnel.sh

# Or with custom settings
TUNNEL_SERVER=wss://your-app.onrender.com LOCAL_PORT=8080 ./tunnel.sh
```

### 2. Using Maven

```bash
# Build
mvn clean compile package

# Run
TUNNEL_SERVER=wss://your-app.onrender.com LOCAL_PORT=3000 java -jar target/tunnel-client-1.0.0.jar
```

### 3. Using Gradle

```bash
# Build
./gradlew build

# Run directly with Gradle
TUNNEL_SERVER=wss://your-app.onrender.com LOCAL_PORT=3000 ./gradlew runTunnel

# Or run the JAR
java -jar build/libs/tunnel-client-1.0.0-all.jar
```

## Configuration

The client uses environment variables for configuration:

| Variable | Description | Default |
|----------|-------------|---------|
| `TUNNEL_SERVER` | WebSocket URL of your tunnel server | `wss://your-app.onrender.com` |
| `LOCAL_PORT` | Port of your local server | `3000` |
| `SUBDOMAIN` | Custom subdomain (optional) | Auto-generated |

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
TUNNEL_SERVER=wss://opi-tunnel.onrender.com ./tunnel.sh
```

### Custom Port
```bash
# Tunnel localhost:8080
TUNNEL_SERVER=wss://opi-tunnel.onrender.com LOCAL_PORT=8080 ./tunnel.sh
```

### Custom Subdomain
```bash
# Use custom subdomain