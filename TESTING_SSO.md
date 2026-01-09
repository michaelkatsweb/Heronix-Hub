# Testing SSO with Placeholder Applications

This guide shows you how to create simple placeholder applications to test the Heronix-Hub SSO functionality.

## Quick Test: Update Database Paths

The easiest way to test is to update the database to point to the Hub JAR itself as a test:

### Option 1: Use H2 Console (Easiest)

1. **Start Heronix-Hub**:
   ```bash
   cd H:\Heronix\Heronix-Hub
   mvn javafx:run
   ```

2. **Open H2 Console** in browser: http://localhost:8080/h2-console

3. **Connect**:
   - JDBC URL: `jdbc:h2:file:~/.heronix/hub/hub`
   - Username: `sa`
   - Password: (leave empty)

4. **Update product paths** to point to Hub JAR for testing:
   ```sql
   UPDATE products
   SET executable_path = 'H:\Heronix\Heronix-Hub\target\heronix-hub-1.0.0.jar',
       is_installed = TRUE
   WHERE product_code IN ('SIS', 'SCHEDULER', 'TIME', 'POS');
   ```

5. **Verify**:
   ```sql
   SELECT * FROM products;
   ```

6. **Test**: Click any product tile in the dashboard - it will launch another instance of the Hub!

---

## Option 2: Create Real Placeholder Apps

If you want to create actual placeholder applications that demonstrate SSO:

### Step 1: Create Directory Structure

```bash
cd H:\Heronix
mkdir -p heronix-sis/src/main/java/com/heronixedu/sis
mkdir -p heronix-sis/src/main/java/com/heronixedu/util
mkdir -p heronix-sis/src/main/java/com/heronixedu/model
```

### Step 2: Copy Shared Files

Copy from Hub to SIS:
```bash
# TokenReader
copy Heronix-Hub\src\main\java\com\heronixedu\hub\util\TokenReader.java heronix-sis\src\main\java\com\heronixedu\util\

# User model
copy Heronix-Hub\src\main\java\com\heronixedu\hub\model\User.java heronix-sis\src\main\java\com\heronixedu\model\
```

### Step 3: Update Package Names

Edit the copied files:

**TokenReader.java** - Change:
```java
package com.heronixedu.hub.util;
// to
package com.heronixedu.util;

// And update imports:
import com.heronixedu.model.User;
```

**User.java** - Change:
```java
package com.heronixedu.hub.model;
// to
package com.heronixedu.model;

// Remove JPA annotations (not needed for placeholder):
// Remove @Entity, @Table, @Id, @GeneratedValue, @Column
// Remove import jakarta.persistence.*;
// Keep only the fields and basic getters/setters
```

### Step 4: Create pom.xml

**heronix-sis/pom.xml**:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.heronixedu</groupId>
    <artifactId>heronix-sis</artifactId>
    <version>1.0.0</version>

    <properties>
        <java.version>21</java.version>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <javafx.version>21</javafx.version>
        <jjwt.version>0.12.3</jjwt.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.openjfx</groupId>
            <artifactId>javafx-controls</artifactId>
            <version>${javafx.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jjwt.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.5.1</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals>
                            <goal>shade</goal>
                        </goals>
                        <configuration>
                            <transformers>
                                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                    <mainClass>com.heronixedu.sis.SISApplication</mainClass>
                                </transformer>
                            </transformers>
                            <finalName>heronix-sis</finalName>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

### Step 5: Create Main Application

**heronix-sis/src/main/java/com/heronixedu/sis/SISApplication.java**:
```java
package com.heronixedu.sis;

import com.heronixedu.model.User;
import com.heronixedu.util.TokenReader;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

public class SISApplication extends Application {

    @Override
    public void start(Stage stage) {
        // CHECK FOR SSO TOKEN
        if (TokenReader.hasValidToken()) {
            User user = TokenReader.getUserFromToken();
            showMainScreen(stage, user);
        } else {
            showLoginMessage(stage);
        }
    }

    private void showMainScreen(Stage stage, User user) {
        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));
        root.setStyle("-fx-background-color: linear-gradient(to bottom, #E3F2FD, #FFFFFF);");

        Label title = new Label("HERONIX SIS");
        title.setFont(Font.font("System", FontWeight.BOLD, 32));

        Label subtitle = new Label("Student Information System");
        subtitle.setFont(Font.font(16));

        Label welcomeLabel = new Label("Welcome, " + user.getFullName() + "!");
        welcomeLabel.setFont(Font.font("System", FontWeight.BOLD, 24));
        welcomeLabel.setStyle("-fx-text-fill: #2196F3;");

        Label roleLabel = new Label("Role: " + user.getRole());
        roleLabel.setFont(Font.font(14));

        Label ssoLabel = new Label("✓ Logged in via SSO");
        ssoLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-size: 14px;");

        Label infoLabel = new Label(
            "This is a placeholder application demonstrating SSO.\n" +
            "You were automatically logged in using your Heronix-Hub token!"
        );
        infoLabel.setStyle("-fx-text-align: center;");
        infoLabel.setWrapText(true);
        infoLabel.setMaxWidth(400);

        Button closeButton = new Button("Close");
        closeButton.setStyle(
            "-fx-background-color: #2196F3; " +
            "-fx-text-fill: white; " +
            "-fx-font-size: 14px; " +
            "-fx-padding: 10px 30px;"
        );
        closeButton.setOnAction(e -> stage.close());

        root.getChildren().addAll(
            title, subtitle, welcomeLabel, roleLabel,
            ssoLabel, infoLabel, closeButton
        );

        Scene scene = new Scene(root, 600, 500);
        stage.setScene(scene);
        stage.setTitle("Heronix SIS");
        stage.show();
    }

    private void showLoginMessage(Stage stage) {
        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));

        Label title = new Label("HERONIX SIS");
        title.setFont(Font.font("System", FontWeight.BOLD, 32));

        Label message = new Label(
            "No SSO token found.\n" +
            "Please login through Heronix-Hub first."
        );
        message.setStyle("-fx-text-align: center; -fx-font-size: 14px;");

        Button closeButton = new Button("Close");
        closeButton.setOnAction(e -> stage.close());

        root.getChildren().addAll(title, message, closeButton);

        Scene scene = new Scene(root, 500, 300);
        stage.setScene(scene);
        stage.setTitle("Heronix SIS");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
```

### Step 6: Simplify User Model

**heronix-sis/src/main/java/com/heronixedu/model/User.java**:
```java
package com.heronixedu.model;

public class User {
    private Long id;
    private String username;
    private String fullName;
    private String role;

    public User() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
```

### Step 7: Build

```bash
cd heronix-sis
mvn clean package
```

This creates: `target/heronix-sis.jar`

### Step 8: Update Database

```sql
UPDATE products
SET executable_path = 'H:\Heronix\heronix-sis\target\heronix-sis.jar',
    is_installed = TRUE
WHERE product_code = 'SIS';
```

### Step 9: Test!

1. Login to Heronix-Hub
2. Click "Heronix-SIS" tile
3. SIS should launch and show "Welcome, [Your Name]!" - **auto-logged in via SSO!**

---

## Testing Checklist

- [ ] Hub starts successfully
- [ ] Can login with admin/admin123
- [ ] Dashboard shows 4 product tiles
- [ ] Products show correct status (installed/not installed)
- [ ] Clicking tile launches product
- [ ] Product auto-logs in (no login screen)
- [ ] Product displays user info from token
- [ ] Can launch multiple products
- [ ] All products use same token (SSO)
- [ ] Logout from Hub deletes token
- [ ] Products can't launch after logout

---

## Complete Example: All 4 Apps

Repeat steps 1-9 for each app, changing:

- **Heronix-SIS**: `sis` package, "Student Information System"
- **Heronix-Scheduler**: `scheduler` package, "Class Scheduler"
- **Heronix-Time**: `time` package, "Time Tracking System"
- **Heronix-POS**: `pos` package, "Point of Sale System"

---

## Quick Reference

**Token Location**: `~/.heronix/auth/token.jwt`

**Check token exists**:
```bash
# Windows
type %USERPROFILE%\.heronix\auth\token.jwt

# Linux/Mac
cat ~/.heronix/auth/token.jwt
```

**View token contents** (in H2 Console):
```sql
SELECT * FROM users WHERE username = 'admin';
```

---

© 2025 Heronix Education Systems LLC
