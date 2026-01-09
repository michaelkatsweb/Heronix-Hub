# Heronix-Hub Troubleshooting Guide

## Common Issues and Solutions

### Issue: Login Fails with "Invalid username or password"

**Symptoms:**
- Using `admin` / `admin123` but login fails
- Error in logs: "Invalid username or password"

**Cause:**
Database was created before the data.sql file was properly configured, so the admin user doesn't exist.

**Solution:**

**Option 1: Delete database and restart (Recommended)**

```bash
# Windows (PowerShell)
Remove-Item -Recurse -Force $env:USERPROFILE\.heronix\hub\

# Windows (Command Prompt)
rmdir /s /q %USERPROFILE%\.heronix\hub\

# macOS/Linux
rm -rf ~/.heronix/hub/

# Then restart the application
java -jar target/heronix-hub-1.0.0.jar
```

**Option 2: Add admin user manually via H2 Console**

1. Keep application running
2. Open browser: http://localhost:8080/h2-console
3. Connect with:
   - JDBC URL: `jdbc:h2:file:~/.heronix/hub/hub` (or use full path from your home directory)
   - Username: `sa`
   - Password: (leave empty)
4. Run this SQL:

```sql
INSERT INTO users (username, password_hash, full_name, role, is_active)
VALUES ('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Administrator', 'ADMIN', TRUE);
```

5. Try logging in again

---

### Issue: CSS Infinite Loop / StackOverflowError

**Symptoms:**
```
at javafx.scene.CssStyleHelper.resolveLookups(...)
at javafx.scene.CssStyleHelper.resolveLookups(...)
[repeating thousands of times]
```

**Cause:**
Old version of hub-style.css with CSS custom properties (variables) that JavaFX doesn't support.

**Solution:**
This was fixed in the latest version. If you're seeing this:

1. Rebuild the project:
```bash
mvn clean package -DskipTests
```

2. The CSS file should NOT contain `.root { -fx-primary-color: ... }` variable definitions
3. All colors should be hardcoded hex values like `#2196F3`

---

### Issue: JWT Build Errors

**Symptoms:**
```
cannot find symbol: method parserBuilder()
```

**Cause:**
Using old JWT API methods that were deprecated.

**Solution:**
This was fixed in the latest version. Ensure you're using:
- `Jwts.parser()` instead of `Jwts.parserBuilder()`
- `verifyWith()` instead of `setSigningKey()`
- `parseSignedClaims()` instead of `parseClaimsJws()`
- `getPayload()` instead of `getBody()`

---

### Issue: Database Won't Initialize

**Symptoms:**
- Tables don't exist
- Empty database
- SQL errors on startup

**Cause:**
Spring Boot not configured to run data.sql

**Solution:**
Check `application.yml` has:

```yaml
spring:
  jpa:
    defer-datasource-initialization: true
  sql:
    init:
      mode: always
      data-locations: classpath:data.sql
```

---

### Issue: Token File Not Found

**Symptoms:**
- Auto-login doesn't work
- Products can't read token

**Cause:**
Token file wasn't created or was deleted.

**Solution:**

1. Login to Heronix-Hub first
2. Check token exists:

```bash
# Windows
type %USERPROFILE%\.heronix\auth\token.jwt

# macOS/Linux
cat ~/.heronix/auth/token.jwt
```

3. If missing, logout and login again
4. Check file permissions (should be readable)

---

### Issue: Products Won't Launch

**Symptoms:**
- Click product tile but nothing happens
- Error: "Product not installed"

**Cause:**
JAR file doesn't exist at the specified path.

**Solution:**

1. Check the database for the product path:
```sql
SELECT product_code, executable_path FROM products;
```

2. Verify JAR file exists at that location
3. Update path if needed:
```sql
UPDATE products
SET executable_path = '/full/path/to/heronix-sis.jar'
WHERE product_code = 'SIS';
```

---

### Issue: Port 8080 Already in Use

**Symptoms:**
```
Port 8080 was already in use
```

**Cause:**
Another application is using port 8080.

**Solution:**

**Option 1: Stop other application**

**Option 2: Change port in `application.yml`**
```yaml
server:
  port: 8081
```

Then rebuild:
```bash
mvn clean package -DskipTests
```

---

### Issue: Application Won't Start

**Symptoms:**
- Crashes immediately
- No window appears
- Java errors

**Diagnostic Steps:**

1. **Check Java version**:
```bash
java -version
# Should show 17 or higher
```

2. **Check logs**:
```bash
# Windows
type %USERPROFILE%\.heronix\hub\logs\hub.log

# macOS/Linux
cat ~/.heronix/hub/logs/hub.log
```

3. **Run with verbose output**:
```bash
java -jar target/heronix-hub-1.0.0.jar --debug
```

4. **Check JavaFX is available**:
JavaFX should be included in the JAR. If not:
```bash
mvn clean package -DskipTests
```

---

### Issue: H2 Console Won't Open

**Symptoms:**
- Can't access http://localhost:8080/h2-console
- 404 error

**Cause:**
H2 console is only available when application is running.

**Solution:**

1. Make sure application is running
2. Try full URL: http://localhost:8080/h2-console
3. Check application.yml has:
```yaml
spring:
  h2:
    console:
      enabled: true
      path: /h2-console
```

---

### Issue: Token Expired

**Symptoms:**
- Auto-login stops working after 8 hours
- Have to login again

**Cause:**
Tokens expire after 8 hours (by design).

**Solution:**

This is expected behavior. To change expiration time, edit `application.yml`:

```yaml
heronix:
  jwt:
    expiration-hours: 24  # Change to desired hours
```

Then rebuild and restart.

---

### Issue: Hibernate Dialect Warning

**Symptoms:**
```
HHH90000025: H2Dialect does not need to be specified explicitly
```

**Cause:**
Hibernate auto-detects H2, explicit dialect not needed.

**Solution:**
This is just a warning and can be ignored. To remove it, delete this from `application.yml`:
```yaml
dialect: org.hibernate.dialect.H2Dialect
```

---

## Getting Help

If none of these solutions work:

1. **Check logs**: `~/.heronix/hub/logs/hub.log`
2. **Check documentation**: README.md, QUICKSTART.md, INSTALLATION.md
3. **Clean rebuild**:
```bash
mvn clean
rm -rf ~/.heronix/
mvn package -DskipTests
java -jar target/heronix-hub-1.0.0.jar
```

4. **Report issue** with:
   - Full error message
   - Log file contents
   - Java version (`java -version`)
   - Operating system

---

## Quick Reset (Nuclear Option)

If everything is broken:

```bash
# Stop application
# Delete everything
rm -rf ~/.heronix/
cd /path/to/heronix-hub
mvn clean
mvn package -DskipTests

# Start fresh
java -jar target/heronix-hub-1.0.0.jar
```

Login: `admin` / `admin123`

---

© 2025 Heronix Education Systems LLC
