# Heronix-Hub Quick Start Guide

Get up and running with Heronix-Hub in 5 minutes!

## Step 1: Prerequisites

Ensure you have installed:

- **Java 17+**: [Download](https://adoptium.net/)
- **Maven 3.8+**: [Download](https://maven.apache.org/download.cgi)

Verify installations:
```bash
java -version
mvn -version
```

## Step 2: Build & Run

### Option A: Use the Run Script (Recommended)

**Windows:**
```bash
run.bat
```

**macOS/Linux:**
```bash
chmod +x run.sh
./run.sh
```

### Option B: Manual Build

```bash
# Build the project
mvn clean package

# Run the application
java -jar target/heronix-hub-1.0.0.jar
```

### Option C: Maven Spring Boot Plugin

```bash
mvn spring-boot:run
```

## Step 3: Login

When the application starts, you'll see the login screen.

**Default Credentials:**
- Username: `admin`
- Password: `admin123`

**IMPORTANT**: Change this password after first login!

## Step 4: View Dashboard

After login, you'll see the dashboard with product tiles for:
- Heronix-SIS
- Heronix-Scheduler
- Heronix-Time
- Heronix-POS

Products will show as "Not Installed" until their JAR files are placed in the correct locations.

## Step 5: Testing SSO

To test Single Sign-On:

1. Build a test product JAR (or use an existing Heronix product)
2. Place it in the location specified in the database (e.g., `./heronix-sis.jar`)
3. Add the `TokenReader` utility to the product
4. Click the product tile in Hub to launch it
5. The product should auto-login using the Hub token

## File Locations

After first run, Heronix-Hub creates these directories:

```
~/.heronix/
├── auth/
│   └── token.jwt           # SSO token (auto-generated)
├── config/
│   └── secret.key          # JWT signing key (auto-generated)
└── hub/
    ├── hub.db              # H2 database
    └── logs/
        └── hub.log         # Application logs
```

## Common Tasks

### Change Database Location

Edit `src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:h2:file:/your/custom/path/hub
```

### Change Token Expiration

Edit `src/main/resources/application.yml`:

```yaml
heronix:
  jwt:
    expiration-hours: 12  # Default is 8 hours
```

### Add a New User

Currently, users must be added directly to the database. Using H2 Console:

1. Access H2 Console: http://localhost:8080/h2-console (when app is running)
2. Connect using:
   - JDBC URL: `jdbc:h2:file:~/.heronix/hub/hub`
   - Username: `sa`
   - Password: (leave empty)
3. Run SQL:
```sql
INSERT INTO users (username, password_hash, full_name, role, is_active)
VALUES ('john.doe',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        'John Doe', 'TEACHER', TRUE);
```

**Note**: The password hash shown is for `admin123`. Generate new hashes using BCrypt.

### Register a New Product

Edit `src/main/resources/data.sql` or insert via H2 Console:

```sql
INSERT INTO products (product_code, product_name, executable_path, is_installed)
VALUES ('GRADEBOOK', 'Heronix-Gradebook', './heronix-gradebook.jar', FALSE);
```

### View Logs

```bash
# Windows
type %USERPROFILE%\.heronix\hub\logs\hub.log

# macOS/Linux
tail -f ~/.heronix/hub/logs/hub.log
```

## Development Mode

For development with hot reload:

```bash
mvn spring-boot:run -Dspring-boot.run.fork=false
```

## Building Native Installer

### Windows

```bash
jpackage --input target \
  --name HeronixHub \
  --main-jar heronix-hub-1.0.0.jar \
  --main-class com.heronixedu.hub.HubApplication \
  --type exe \
  --app-version 1.0.0 \
  --vendor "Heronix Education Systems LLC"
```

Creates: `HeronixHub-1.0.0.exe`

## Troubleshooting

### "Port already in use" Error

Spring Boot tries to use port 8080. If occupied:

Edit `src/main/resources/application.yml`:
```yaml
server:
  port: 8081
```

### Token File Not Found

Check that:
1. You successfully logged in to Hub
2. Token file exists: `~/.heronix/auth/token.jwt`
3. Other products are looking in the same location

### Products Don't Launch

Verify:
1. JAR file exists at the path specified in database
2. Java is in system PATH
3. File has execute permissions (Linux/macOS)

### Login Fails

Check:
1. Database exists: `~/.heronix/hub/hub.db`
2. Default user was created (check logs)
3. Password is correct: `admin123`

## Next Steps

- Read the full [README.md](README.md) for detailed information
- Integrate other Heronix products using `TokenReader`
- Customize the CSS in `src/main/resources/css/hub-style.css`
- Add more users and products to the database

## Support

For help, contact: **Heronix Education Systems LLC**

---

**Happy Coding! 🚀**
