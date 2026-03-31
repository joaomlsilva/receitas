# Deployment Guide — Receitas App

This guide covers two deployment options for a live Linux server.

---

## Option A — Embedded Tomcat (Recommended)

Spring Boot includes an embedded Tomcat server. You only need Java 17 on the server — no separate Tomcat installation required.

### 1. Prerequisites on the server

```bash
sudo apt update
sudo apt install -y openjdk-17-jdk
java -version   # should print 17.x.x
```

### 2. Build the JAR locally

```bash
cd receitas-app
./mvnw clean package -DskipTests
```

The output file will be:
```
receitas-app/target/receitas-app-1.0.0.jar
```

### 3. Transfer files to the server

```bash
# Create the app directory on the server
ssh user@your-server "mkdir -p /opt/receitas-app/data"

# Copy the JAR
scp receitas-app/target/receitas-app-1.0.0.jar user@your-server:/opt/receitas-app/

# Copy the data file
scp receitas-app/data/receitas.json user@your-server:/opt/receitas-app/data/
```

### 4. Run the application

```bash
ssh user@your-server
cd /opt/receitas-app
java -jar receitas-app-1.0.0.jar --receitas.data.path=/opt/receitas-app/data/receitas.json
```

The app will be available at `http://your-server:8080`.

### 5. Run as a systemd service (keeps it running after reboot)

Create the service file on the server:

```bash
sudo nano /etc/systemd/system/receitas-app.service
```

Paste the following (replace `user` with your actual Linux username):

```ini
[Unit]
Description=Receitas App
After=network.target

[Service]
User=user
WorkingDirectory=/opt/receitas-app
ExecStart=/usr/bin/java -jar /opt/receitas-app/receitas-app-1.0.0.jar --receitas.data.path=/opt/receitas-app/data/receitas.json
SuccessExitStatus=143
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

Enable and start:

```bash
sudo systemctl daemon-reload
sudo systemctl enable receitas-app
sudo systemctl start receitas-app
sudo systemctl status receitas-app
```

---

## Option B — External Tomcat (WAR deployment)

Use this if you already have a standalone Tomcat server and want to deploy the app as a `.war` file.

### 1. Prerequisites on the server

```bash
sudo apt update
sudo apt install -y openjdk-17-jdk tomcat10
```

### 2. Modify the application for WAR packaging

**a) Change `pom.xml`** — add `<packaging>war</packaging>` and mark embedded Tomcat as provided:

```xml
<packaging>war</packaging>

<!-- inside <dependencies> -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-tomcat</artifactId>
    <scope>provided</scope>
</dependency>
```

**b) Modify `ReceitasApplication.java`** to extend `SpringBootServletInitializer`:

```java
package com.receitas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class ReceitasApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(ReceitasApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(ReceitasApplication.class, args);
    }
}
```

### 3. Build the WAR

```bash
cd receitas-app
./mvnw clean package -DskipTests
```

Output: `receitas-app/target/receitas-app-1.0.0.war`

### 4. Deploy to Tomcat

```bash
# Copy data file to a known location on the server
ssh user@your-server "mkdir -p /opt/receitas-app/data"
scp receitas-app/data/receitas.json user@your-server:/opt/receitas-app/data/

# Copy the WAR to Tomcat's webapps directory
scp receitas-app/target/receitas-app-1.0.0.war user@your-server:/var/lib/tomcat10/webapps/receitas.war
```

### 5. Configure the data path

Set the system property so Tomcat passes it to the app:

```bash
sudo nano /etc/tomcat10/tomcat10.conf
```

Add:

```
JAVA_OPTS="-Dreceitas.data.path=/opt/receitas-app/data/receitas.json"
```

### 6. Start Tomcat

```bash
sudo systemctl restart tomcat10
sudo systemctl enable tomcat10
```

The app will be available at `http://your-server:8080/receitas`.

---

## Firewall

Allow HTTP traffic on port 8080 (or 80 if using a reverse proxy):

```bash
sudo ufw allow 8080/tcp
sudo ufw reload
```

## Optional — Reverse proxy with Nginx on port 80

```bash
sudo apt install -y nginx
```

Create `/etc/nginx/sites-available/receitas`:

```nginx
server {
    listen 80;
    server_name your-domain.com;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

```bash
sudo ln -s /etc/nginx/sites-available/receitas /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

The site will then be reachable on port 80.

---

## Logs

| Method        | Command                                            |
|---------------|----------------------------------------------------|
| Embedded JAR  | `sudo journalctl -u receitas-app -f`               |
| External WAR  | `sudo tail -f /var/log/tomcat10/catalina.out`      |
