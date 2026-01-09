# Heronix-Hub Quick Reference

## 🚀 Running the Application

### Option 1: Maven JavaFX Plugin (Development)
```bash
mvn javafx:run
```

### Option 2: Spring Boot Plugin
```bash
mvn spring-boot:run
```

### Option 3: Executable JAR
```bash
mvn clean package -DskipTests
java -jar target/heronix-hub-1.0.0.jar
```

---

## 🔑 Default Login Credentials

```
Username: admin
Password: admin123
```

⚠️ **Change this password immediately after first login!**

---

## 📁 Important File Locations

### User Home Directory: `~/.heronix/`

```
~/.heronix/
├── auth/
│   └── token.jwt          ← SSO token (auto-generated on login)
├── config/
│   └── secret.key         ← JWT signing key (auto-generated)
└── hub/
    ├── hub.mv.db          ← H2 database file
    └── logs/
        └── hub.log        ← Application logs
```

### Windows Paths
```
%USERPROFILE%\.heronix\auth\token.jwt
%USERPROFILE%\.heronix\config\secret.key
%USERPROFILE%\.heronix\hub\hub.mv.db
%USERPROFILE%\.heronix\hub\logs\hub.log
```

### macOS/Linux Paths
```
~/.heronix/auth/token.jwt
~/.heronix/config/secret.key
~/.heronix/hub/hub.mv.db
~/.heronix/hub/logs/hub.log
```

---

## 🗄️ Database Access

### H2 Console (Web Interface)

1. **Start application**
2. **Open browser**: http://localhost:8080/h2-console
3. **Connection settings**:
   - **JDBC URL**: `jdbc:h2:file:~/.heronix/hub/hub`
   - **Username**: `sa`
   - **Password**: (leave empty)
4. **Click Connect**

### Useful SQL Queries

**View all users:**
```sql
SELECT * FROM users;
```

**View all products:**
```sql
SELECT * FROM products;
```

**Add a new user:**
```sql
-- Note: Use the application's DataInitializer for proper password hashing
-- This is just for reference
INSERT INTO users (username, password_hash, full_name, role, is_active)
VALUES ('john', '$2a$10$...hash...', 'John Doe', 'TEACHER', TRUE);
```

**Update product path:**
```sql
UPDATE products
SET executable_path = '/full/path/to/heronix-sis.jar'
WHERE product_code = 'SIS';
```

---

## 🔄 Fresh Start (Reset Everything)

```bash
# Stop application

# Delete all data
rm -rf ~/.heronix/

# Rebuild
cd /path/to/heronix-hub
mvn clean package -DskipTests

# Start fresh
java -jar target/heronix-hub-1.0.0.jar
```

**Windows:**
```cmd
rmdir /s /q %USERPROFILE%\.heronix
```

---

## 🐛 Quick Troubleshooting

### Login Not Working

**Delete database and restart:**
```bash
rm -rf ~/.heronix/hub/
mvn javafx:run
```

### Application Won't Start

**Check Java version:**
```bash
java -version
# Must be 17 or higher
```

### Port 8080 In Use

**Change port in application.yml:**
```yaml
server:
  port: 8081
```

### View Logs

```bash
# Windows
type %USERPROFILE%\.heronix\hub\logs\hub.log

# macOS/Linux
tail -f ~/.heronix/hub/logs/hub.log
```

---

## 📦 Building Native Installer

### Windows (.exe)
```bash
jpackage --input target \
  --name HeronixHub \
  --main-jar heronix-hub-1.0.0.jar \
  --main-class com.heronixedu.hub.HubApplication \
  --type exe \
  --app-version 1.0.0 \
  --vendor "Heronix Education Systems LLC"
```

### macOS (.dmg)
```bash
jpackage --input target \
  --name HeronixHub \
  --main-jar heronix-hub-1.0.0.jar \
  --main-class com.heronixedu.hub.HubApplication \
  --type dmg \
  --app-version 1.0.0 \
  --vendor "Heronix Education Systems LLC"
```

### Linux (.deb)
```bash
jpackage --input target \
  --name heronix-hub \
  --main-jar heronix-hub-1.0.0.jar \
  --main-class com.heronixedu.hub.HubApplication \
  --type deb \
  --app-version 1.0.0 \
  --vendor "Heronix Education Systems LLC"
```

---

## 🔧 Development Commands

**Clean build:**
```bash
mvn clean
```

**Compile only:**
```bash
mvn compile
```

**Package JAR:**
```bash
mvn package -DskipTests
```

**Run tests:**
```bash
mvn test
```

**Run with debug:**
```bash
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug"
```

---

## 📖 Documentation Links

- **README.md** - Complete project documentation
- **QUICKSTART.md** - 5-minute quick start guide
- **INSTALLATION.md** - Detailed installation instructions
- **TROUBLESHOOTING.md** - Common issues and solutions
- **CHANGELOG.md** - Version history and changes
- **PROJECT_SUMMARY.md** - Project overview
- **STRUCTURE.txt** - File structure reference

---

## 🎯 Success Checklist

After starting the application:

- [ ] Login screen appears
- [ ] Can login with admin/admin123
- [ ] Dashboard shows 4 product tiles
- [ ] Token file created: `~/.heronix/auth/token.jwt`
- [ ] Secret key created: `~/.heronix/config/secret.key`
- [ ] Database created: `~/.heronix/hub/hub.mv.db`
- [ ] Logs written to: `~/.heronix/hub/logs/hub.log`
- [ ] Logout deletes token file
- [ ] Relaunch with token = auto-login
- [ ] H2 console accessible at http://localhost:8080/h2-console

---

## 💡 Tips

1. **Always use Maven commands from the project root directory**
2. **Delete database if login issues occur**
3. **Check logs first when troubleshooting**
4. **Use H2 console to inspect database**
5. **BCrypt hashes are generated at runtime by DataInitializer**
6. **Token expires after 8 hours (configurable)**

---

## 🆘 Getting Help

1. Check **TROUBLESHOOTING.md** first
2. Review logs at `~/.heronix/hub/logs/hub.log`
3. Try fresh start (delete ~/.heronix/)
4. Check Java version is 17+
5. Verify Maven is installed

---

© 2025 Heronix Education Systems LLC
