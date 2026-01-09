# Heronix-Hub

**Single Sign-On Launcher for All Heronix Products**

Version: 1.0.0 MVP

---

## Overview

Heronix-Hub is a unified launcher and authentication system for all Heronix products (SIS, Scheduler, Time, POS, etc.). Users log in once to Hub and access all products without re-entering credentials—true Single Sign-On (SSO).

## Features

- **Single Entry Point**: One login screen for all Heronix applications
- **Single Sign-On (SSO)**: File-based JWT token sharing across products
- **Product Launcher**: Visual tile-based dashboard to launch installed products
- **Secure Authentication**: BCrypt password hashing with JWT tokens
- **Auto-Login**: Products automatically detect valid tokens and skip login
- **8-Hour Sessions**: Tokens expire after 8 hours (full school day)
- **Offline Support**: Works 100% offline, no network required

## Technology Stack

- **Application**: JavaFX 21
- **Backend**: Java 17 + Spring Boot 3
- **Database**: H2 embedded (upgradeable to PostgreSQL)
- **Authentication**: JWT (JSON Web Tokens)
- **SSO Mechanism**: File-based token storage
- **Build**: Maven
- **Packaging**: jpackage (native installers)

## Prerequisites

- **Java 17** or higher
- **Maven 3.8+**
- **JavaFX 21** (included as Maven dependency)

## Quick Start

### 1. Build the Project

```bash
cd heronix-hub
mvn clean package
```

### 2. Run the Application

```bash
mvn spring-boot:run
```

Or run the JAR directly:

```bash
java -jar target/heronix-hub-1.0.0.jar
```

### 3. Login

- **Username**: `admin`
- **Password**: `admin123`

**IMPORTANT**: Change the admin password immediately after first login!

## Project Structure

```
heronix-hub/
├── pom.xml                          # Maven dependencies
├── src/main/
│   ├── java/com/heronixedu/hub/
│   │   ├── HubApplication.java      # Main JavaFX + Spring Boot app
│   │   ├── controller/
│   │   │   ├── LoginController.java
│   │   │   └── DashboardController.java
│   │   ├── model/
│   │   │   ├── User.java            # JPA entity
│   │   │   └── Product.java         # JPA entity
│   │   ├── repository/
│   │   │   ├── UserRepository.java
│   │   │   └── ProductRepository.java
│   │   ├── service/
│   │   │   ├── TokenService.java    # JWT generation & file I/O
│   │   │   ├── AuthenticationService.java
│   │   │   └── ProductLauncherService.java
│   │   └── util/
│   │       └── TokenReader.java     # Utility for other products
│   └── resources/
│       ├── application.yml          # Spring Boot config
│       ├── schema.sql               # Database schema
│       ├── data.sql                 # Default data
│       ├── fxml/
│       │   ├── login.fxml
│       │   └── dashboard.fxml
│       └── css/
│           └── hub-style.css
```

## How SSO Works

### Flow

1. User opens Hub → sees login screen
2. User enters credentials → Hub validates against database
3. If valid → generate JWT token containing userId, username, role, expiration
4. Save token to file: `~/.heronix/auth/token.jwt`
5. Show dashboard with product tiles
6. User clicks "Heronix-SIS" tile → Hub launches `heronix-sis.jar`
7. SIS checks for token file → if exists and valid → auto-login (skip SIS login screen)
8. User switches to Scheduler → same token → auto-login
9. User logs out → Hub deletes token file → all products lose authentication

### Key Files

- **Token File**: `~/.heronix/auth/token.jwt`
- **Secret Key**: `~/.heronix/config/secret.key`
- **Database**: `~/.heronix/hub/hub.db`
- **Logs**: `~/.heronix/hub/logs/hub.log`

## Integrating Other Products

To add SSO support to other Heronix products, copy the `TokenReader` utility class and use it in your application:

```java
import com.heronixedu.hub.util.TokenReader;
import com.heronixedu.hub.model.User;

public void start(Stage stage) {
    if (TokenReader.hasValidToken()) {
        User user = TokenReader.getUserFromToken();
        showMainScreen(user); // Skip login screen!
    } else {
        showLoginScreen(); // Normal login
    }
}
```

**CRITICAL**: All products must:
1. Use the same token file location: `~/.heronix/auth/token.jwt`
2. Use the same secret key file: `~/.heronix/config/secret.key`
3. Include the JWT library dependencies in their `pom.xml`

## Database Schema

### Users Table

```sql
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE
);
```

### Products Table

```sql
CREATE TABLE products (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_code VARCHAR(50) UNIQUE NOT NULL,
    product_name VARCHAR(100) NOT NULL,
    executable_path VARCHAR(255) NOT NULL,
    is_installed BOOLEAN DEFAULT FALSE,
    last_launched TIMESTAMP
);
```

## Registered Products

The following products are pre-registered in the database:

- **Heronix-SIS** (Student Information System)
- **Heronix-Scheduler** (Class Scheduler)
- **Heronix-Time** (Time Tracking)
- **Heronix-POS** (Point of Sale)

**Note**: Products are shown as "Not Installed" until their JAR files are found at the specified paths.

## Building Native Installers

### Windows (.exe)

```bash
jpackage --input target \
  --name HeronixHub \
  --main-jar heronix-hub-1.0.0.jar \
  --main-class com.heronixedu.hub.HubApplication \
  --type exe \
  --app-version 1.0.0 \
  --vendor "Heronix Education Systems LLC" \
  --icon src/main/resources/icon.ico
```

### macOS (.dmg)

```bash
jpackage --input target \
  --name HeronixHub \
  --main-jar heronix-hub-1.0.0.jar \
  --main-class com.heronixedu.hub.HubApplication \
  --type dmg \
  --app-version 1.0.0 \
  --vendor "Heronix Education Systems LLC" \
  --icon src/main/resources/icon.icns
```

### Linux (.deb)

```bash
jpackage --input target \
  --name heronix-hub \
  --main-jar heronix-hub-1.0.0.jar \
  --main-class com.heronixedu.hub.HubApplication \
  --type deb \
  --app-version 1.0.0 \
  --vendor "Heronix Education Systems LLC" \
  --icon src/main/resources/icon.png
```

## Configuration

Edit `src/main/resources/application.yml` to customize:

- Database location
- Token expiration time (default: 8 hours)
- Log file location
- Token file path
- Secret key file path

## Security Notes

1. **Password Hashing**: Passwords are hashed using BCrypt with strength 10
2. **Token Expiration**: Tokens expire after 8 hours
3. **File Permissions**: Token and key files are set to `rw-------` (600) on Unix/Linux
4. **Default Password**: Change the admin password immediately after first login
5. **Secret Key**: Generated automatically on first run and stored securely

## MVP Success Criteria

✅ User logs in with admin/admin123
✅ Dashboard displays product tiles
✅ Clicking tile launches product
✅ Product auto-logs in user (reads token file)
✅ User can switch between products without re-login
✅ Logout deletes token and returns to login screen
✅ Token expires after 8 hours
✅ Database persists users and products
✅ Packages as native installer (.exe, .dmg, .deb)

## Troubleshooting

### Application won't start

- Check that Java 17+ is installed: `java -version`
- Verify Maven build succeeded: `mvn clean package`
- Check logs at: `~/.heronix/hub/logs/hub.log`

### Products don't auto-login

- Verify token file exists: `~/.heronix/auth/token.jwt`
- Verify secret key exists: `~/.heronix/config/secret.key`
- Check that the product includes `TokenReader` utility
- Ensure product uses the same JWT library version

### Product won't launch

- Check that the JAR file exists at the specified path
- Verify the path in the database matches the actual file location
- Check that Java is in the system PATH
- Review logs for detailed error messages

## Next Steps (Phase 2)

After MVP completion, consider:

- User management UI (create/edit/delete users)
- Product auto-discovery (scan for installed JARs)
- Settings panel (change password, theme)
- License management
- Update checker
- System health monitoring
- Dark mode support
- Multi-language support

## Support

For issues, questions, or feature requests, contact:
**Heronix Education Systems LLC**

---

## License

© 2025 Heronix Education Systems LLC. All rights reserved.

---

**Build Heronix-Hub. Keep it simple. Ship it fast. Enhance later.**
