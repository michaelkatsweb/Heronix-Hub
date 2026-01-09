# Heronix-Hub Installation & Deployment Guide

Complete guide for building, running, and deploying Heronix-Hub.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Installation Steps](#installation-steps)
3. [Building the Application](#building-the-application)
4. [Running the Application](#running-the-application)
5. [Testing the Application](#testing-the-application)
6. [Creating Native Installers](#creating-native-installers)
7. [Deployment](#deployment)
8. [Troubleshooting](#troubleshooting)

---

## Prerequisites

### Required Software

| Software | Version | Download Link |
|----------|---------|---------------|
| Java JDK | 17+ | https://adoptium.net/ |
| Maven | 3.8+ | https://maven.apache.org/download.cgi |
| Git | Latest | https://git-scm.com/downloads |

### Verify Installation

Open a terminal/command prompt and run:

```bash
# Check Java version
java -version
# Should show: openjdk version "17.x.x" or higher

# Check Maven version
mvn -version
# Should show: Apache Maven 3.8.x or higher

# Check Git version (if using version control)
git --version
# Should show: git version 2.x.x
```

### System Requirements

- **OS**: Windows 10/11, macOS 10.14+, or Linux (Ubuntu 20.04+)
- **RAM**: 2 GB minimum, 4 GB recommended
- **Disk**: 500 MB free space
- **Display**: 1024x768 minimum resolution

---

## Installation Steps

### Step 1: Navigate to Project Directory

```bash
cd H:\Heronix\heronix-hub
```

Or on macOS/Linux:
```bash
cd ~/Heronix/heronix-hub
```

### Step 2: Verify Project Structure

Ensure all files are present:

```bash
# Windows
dir

# macOS/Linux
ls -la
```

You should see:
- `pom.xml`
- `src/` directory
- `run.bat` and `run.sh`
- Documentation files (README.md, etc.)

---

## Building the Application

### Option 1: Using Build Scripts (Recommended)

**Windows:**
```bash
run.bat
```

**macOS/Linux:**
```bash
chmod +x run.sh
./run.sh
```

The script will:
1. Clean previous builds
2. Compile and package the application
3. Run the JAR file

### Option 2: Manual Build

```bash
# Clean previous build
mvn clean

# Compile and package
mvn package

# Skip tests if needed
mvn package -DskipTests

# With verbose output
mvn package -X
```

### Build Output

After successful build, you should see:

```
target/
├── heronix-hub-1.0.0.jar       ← Executable JAR
├── classes/                     ← Compiled classes
└── maven-archiver/              ← Build metadata
```

---

## Running the Application

### Method 1: Using JAR File (Production)

```bash
java -jar target/heronix-hub-1.0.0.jar
```

### Method 2: Using Maven Plugin (Development)

```bash
mvn spring-boot:run
```

### Method 3: Using Build Scripts

**Windows:**
```bash
run.bat
```

**macOS/Linux:**
```bash
./run.sh
```

### Expected Output

When the application starts successfully, you should see:

```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v3.2.0)

INFO: Starting HubApplication...
INFO: Started HubApplication in X.XXX seconds
```

The JavaFX window should open automatically.

---

## Testing the Application

### Test 1: Login

1. Application opens showing login screen
2. Enter credentials:
   - Username: `admin`
   - Password: `admin123`
3. Click "Login" button
4. Dashboard should appear

**Expected**: Login successful, dashboard displayed

### Test 2: Token File Creation

After successful login, check token file exists:

**Windows:**
```bash
type %USERPROFILE%\.heronix\auth\token.jwt
```

**macOS/Linux:**
```bash
cat ~/.heronix/auth/token.jwt
```

**Expected**: JWT token string displayed

### Test 3: Auto-Login

1. Close the application (keep logged in)
2. Reopen the application
3. Should skip login screen and show dashboard directly

**Expected**: Auto-login using saved token

### Test 4: Product Tiles

On the dashboard, you should see tiles for:
- Heronix-SIS
- Heronix-Scheduler
- Heronix-Time
- Heronix-POS

All showing "Not Installed" status (until JAR files are placed)

**Expected**: 4 product tiles displayed

### Test 5: Logout

1. Click "Logout" button on dashboard
2. Should return to login screen
3. Token file should be deleted

**Windows:**
```bash
dir %USERPROFILE%\.heronix\auth\
```

**macOS/Linux:**
```bash
ls ~/.heronix/auth/
```

**Expected**: token.jwt file deleted

### Test 6: Database Creation

After first run, check database exists:

**Windows:**
```bash
dir %USERPROFILE%\.heronix\hub\
```

**macOS/Linux:**
```bash
ls ~/.heronix/hub/
```

**Expected**: `hub.db` file exists

### Test 7: Logs

Check application logs:

**Windows:**
```bash
type %USERPROFILE%\.heronix\hub\logs\hub.log
```

**macOS/Linux:**
```bash
tail -f ~/.heronix/hub/logs/hub.log
```

**Expected**: Log entries showing application activity

---

## Creating Native Installers

### Prerequisites for jpackage

- **Windows**: WiX Toolset 3.11+ (for .exe) or Inno Setup
- **macOS**: Xcode command line tools
- **Linux**: dpkg (for .deb) or rpm (for .rpm)

### Windows Installer (.exe)

```bash
jpackage --input target \
  --name HeronixHub \
  --main-jar heronix-hub-1.0.0.jar \
  --main-class com.heronixedu.hub.HubApplication \
  --type exe \
  --app-version 1.0.0 \
  --vendor "Heronix Education Systems LLC" \
  --description "Single Sign-On Launcher for Heronix Products" \
  --win-dir-chooser \
  --win-menu \
  --win-shortcut
```

**Output**: `HeronixHub-1.0.0.exe`

### macOS Installer (.dmg)

```bash
jpackage --input target \
  --name HeronixHub \
  --main-jar heronix-hub-1.0.0.jar \
  --main-class com.heronixedu.hub.HubApplication \
  --type dmg \
  --app-version 1.0.0 \
  --vendor "Heronix Education Systems LLC" \
  --description "Single Sign-On Launcher for Heronix Products" \
  --mac-package-name "HeronixHub"
```

**Output**: `HeronixHub-1.0.0.dmg`

### Linux Installer (.deb)

```bash
jpackage --input target \
  --name heronix-hub \
  --main-jar heronix-hub-1.0.0.jar \
  --main-class com.heronixedu.hub.HubApplication \
  --type deb \
  --app-version 1.0.0 \
  --vendor "Heronix Education Systems LLC" \
  --description "Single Sign-On Launcher for Heronix Products" \
  --linux-shortcut
```

**Output**: `heronix-hub_1.0.0_amd64.deb`

### Installation of Native Packages

**Windows:**
```bash
# Double-click HeronixHub-1.0.0.exe
# Or run from command line:
HeronixHub-1.0.0.exe /S
```

**macOS:**
```bash
# Mount DMG
hdiutil attach HeronixHub-1.0.0.dmg

# Copy app to Applications
cp -R /Volumes/HeronixHub/HeronixHub.app /Applications/

# Unmount DMG
hdiutil detach /Volumes/HeronixHub
```

**Linux:**
```bash
# Install DEB package
sudo dpkg -i heronix-hub_1.0.0_amd64.deb

# Or using apt
sudo apt install ./heronix-hub_1.0.0_amd64.deb
```

---

## Deployment

### Development Environment

Use Maven Spring Boot plugin for development:

```bash
mvn spring-boot:run
```

Features:
- Fast startup
- Auto-reload (with spring-boot-devtools)
- Easy debugging

### Production Environment

Use executable JAR for production:

```bash
# Run in foreground
java -jar heronix-hub-1.0.0.jar

# Run in background (Linux/macOS)
nohup java -jar heronix-hub-1.0.0.jar > /dev/null 2>&1 &

# Run as Windows service
# Use tools like NSSM or Apache Commons Daemon
```

### Production Configuration

Create a production configuration file:

**application-prod.yml:**

```yaml
spring:
  datasource:
    url: jdbc:h2:file:/opt/heronix/hub/hub

logging:
  level:
    com.heronixedu.hub: INFO
    org.springframework: WARN
  file:
    name: /var/log/heronix/hub.log

heronix:
  jwt:
    expiration-hours: 8
```

Run with production profile:

```bash
java -jar heronix-hub-1.0.0.jar --spring.profiles.active=prod
```

### Database Backup

Backup H2 database:

**Windows:**
```bash
copy %USERPROFILE%\.heronix\hub\hub.db %USERPROFILE%\Desktop\hub-backup.db
```

**macOS/Linux:**
```bash
cp ~/.heronix/hub/hub.db ~/Desktop/hub-backup.db
```

### Migration to PostgreSQL

For production deployments with multiple users:

1. Install PostgreSQL
2. Create database: `CREATE DATABASE heronix_hub;`
3. Update `application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/heronix_hub
    username: heronix
    password: your_password
    driver-class-name: org.postgresql.Driver
  jpa:
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
```

4. Add PostgreSQL dependency to `pom.xml`:

```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

---

## Troubleshooting

### Build Issues

**Issue**: Maven build fails with "package does not exist"

**Solution**:
```bash
mvn clean install -U
```

**Issue**: JavaFX modules not found

**Solution**: Ensure Java 17+ with JavaFX is installed. Or use Maven JavaFX plugin (already configured).

**Issue**: Spring Boot version incompatibility

**Solution**: Check `pom.xml` uses Spring Boot 3.2.0 or higher.

### Runtime Issues

**Issue**: Application won't start

**Solution**:
1. Check Java version: `java -version` (must be 17+)
2. Check logs: `~/.heronix/hub/logs/hub.log`
3. Delete database and restart: `rm ~/.heronix/hub/hub.db`

**Issue**: Login screen doesn't appear

**Solution**:
1. Check JavaFX is installed
2. Check FXML files exist in `src/main/resources/fxml/`
3. Check CSS file exists in `src/main/resources/css/`

**Issue**: "Invalid username or password"

**Solution**:
1. Verify default user exists in database
2. Check password hash in `data.sql` matches BCrypt for "admin123"
3. Try resetting database: delete `~/.heronix/hub/hub.db`

**Issue**: Token file not created

**Solution**:
1. Check write permissions on `~/.heronix/auth/` directory
2. Check logs for errors
3. Verify login was successful

**Issue**: Products won't launch

**Solution**:
1. Verify JAR file exists at path specified in database
2. Check Java is in system PATH
3. Check file permissions (execute permission on Linux/macOS)

### Database Issues

**Issue**: Database locked

**Solution**:
```bash
# Close all connections and delete lock file
rm ~/.heronix/hub/hub.db.lock
```

**Issue**: Corrupted database

**Solution**:
```bash
# Backup old database
mv ~/.heronix/hub/hub.db ~/.heronix/hub/hub.db.bak

# Restart application (will create new database)
java -jar heronix-hub-1.0.0.jar
```

### Performance Issues

**Issue**: Slow startup

**Solution**: Increase JVM heap size:
```bash
java -Xmx512m -jar heronix-hub-1.0.0.jar
```

**Issue**: High memory usage

**Solution**: Monitor with JVM flags:
```bash
java -XX:+UseG1GC -Xmx256m -jar heronix-hub-1.0.0.jar
```

---

## Support

For additional help:

1. Check the [README.md](README.md)
2. Check the [QUICKSTART.md](QUICKSTART.md)
3. Review logs at `~/.heronix/hub/logs/hub.log`
4. Contact Heronix Education Systems LLC

---

## Next Steps

After successful installation:

1. ✅ Test login with admin/admin123
2. ✅ Change default admin password
3. ✅ Add additional users (via database)
4. ✅ Register additional products
5. ✅ Test SSO with other Heronix products
6. ✅ Create native installers for distribution
7. ✅ Deploy to production environment

---

© 2025 Heronix Education Systems LLC
