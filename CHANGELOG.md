# Heronix-Hub Changelog

All notable changes to this project will be documented in this file.

## [1.0.0] - 2025-12-22

### Initial MVP Release

#### Added
- Complete Single Sign-On (SSO) launcher system
- Login authentication with BCrypt password hashing
- Dashboard with product tiles
- JWT token-based SSO mechanism
- File-based token storage at `~/.heronix/auth/token.jwt`
- Product launcher service
- Auto-login functionality
- Professional JavaFX UI with CSS styling
- H2 embedded database
- Spring Boot backend
- TokenReader utility for other products
- Comprehensive documentation (README, QUICKSTART, INSTALLATION)
- Build scripts for Windows and Unix/Linux
- Default admin user (username: admin, password: admin123)
- 4 pre-registered products (SIS, Scheduler, Time, POS)

#### Technical Details
- **Framework**: Spring Boot 3.2.0
- **UI**: JavaFX 21
- **Database**: H2 Embedded
- **Authentication**: JWT (jjwt 0.12.3)
- **Build**: Maven
- **Java Version**: 17

#### Fixed (Build-time)
- Updated JWT API calls from deprecated `parserBuilder()` to new `parser()` API
- Updated JWT token generation from `setSubject()` to `subject()`
- Updated JWT token generation from `setIssuedAt()` to `issuedAt()`
- Updated JWT token generation from `setExpiration()` to `expiration()`
- Updated JWT parsing from `parseClaimsJws()` to `parseSignedClaims()`
- Updated JWT parsing from `getBody()` to `getPayload()`
- Updated JWT parsing from `setSigningKey()` to `verifyWith()`
- Updated secret key generation from `Keys.secretKeyFor(SignatureAlgorithm.HS256)` to `Jwts.SIG.HS256.key().build()`
- Removed deprecated `SignatureAlgorithm` import

#### Fixed (Runtime)
- Fixed CSS infinite loop error caused by CSS variable references
- Replaced CSS variables with direct color values (JavaFX doesn't support CSS custom properties)
- Removed circular CSS lookup references that caused StackOverflowError
- Fixed database initialization by updating data.sql to use H2-compatible MERGE syntax instead of PostgreSQL ON CONFLICT
- Added Spring Boot configuration to enable automatic data.sql execution
- Configured `defer-datasource-initialization` to ensure JPA entities are created before data insertion

#### Security
- BCrypt password hashing with strength 10
- JWT tokens with 8-hour expiration
- Secure file permissions (600) on Unix/Linux for token and key files
- Automatic secret key generation and storage

#### Known Limitations (MVP)
- Single admin user (manual user addition via database)
- No user management UI
- No password change UI
- Manual product installation required
- No license checking
- No product auto-discovery
- H2 database (not for large multi-user deployments)

## Future Releases

### [2.0.0] - Planned
- User management UI
- Product auto-discovery
- Settings panel with password change
- License management
- Update checker
- System health monitoring
- Dark mode support
- Multi-language support (i18n)

### [3.0.0] - Planned
- PostgreSQL support
- Multi-tenant support
- Advanced role-based access control (RBAC)
- Activity logging and audit trail
- Performance optimizations
- Auto-update mechanism
- Backup/restore functionality

---

© 2025 Heronix Education Systems LLC
