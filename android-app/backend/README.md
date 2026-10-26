# TaskMind account API (Spring Boot + MySQL)

This backend implements only account registration, login, logout (client-side JWT discard), and account deletion/anonymization.

## 1. Prerequisites
- Java 17+
- Maven 3.9+
- MySQL running with the `task_management_app` schema and the existing `users` table

Import your SQL dump into MySQL first if you have not done so. The `users` table must contain `UserID`, `FullName`, `Email`, `PasswordHash`, `Role`, `IsLocked`, and `IsDeleted` as in your supplied schema.

## 2. Configure database credentials
Set environment variables before starting:

**Windows PowerShell**
```powershell
$env:DB_USERNAME="root"
$env:DB_PASSWORD="YOUR_MYSQL_PASSWORD"
$env:JWT_SECRET="ReplaceWithYourOwnRandomSecretAtLeast32BytesLong!"
```

Or edit `src/main/resources/application.properties` for a local coursework setup. Do not publish real database passwords or JWT secrets.

## 3. Run API
From this folder:
```powershell
mvn spring-boot:run
```
API base URL: `http://localhost:8080/api/auth`

### Endpoints
- `POST /register` JSON: `{"fullName":"Nguyen Van A","email":"a@example.com","password":"Password123"}`
- `POST /login` JSON: `{"email":"a@example.com","password":"Password123"}`; response includes a signed JWT token.
- `POST /logout`: stateless API acknowledgement. The Android app must delete its local token.
- `POST /delete-account` with `Authorization: Bearer <token>` and JSON `{"password":"Password123"}`.

Passwords are stored using BCrypt hashes. Deletion anonymizes the user record and invalidates its password rather than physically deleting the row, so existing task/habit foreign-key references remain intact. The email is changed to a generated deleted-account address, freeing the original email for future registration.

## 4. Android emulator connection
The Android app uses `http://10.0.2.2:8080/api/auth`, which routes from the standard Android Emulator to the host computer. If running on a physical phone, change `AuthApi.BASE_URL` to the computer's LAN IPv4 address (both devices must be on the same network). Cleartext HTTP is for local development only; use HTTPS in production.
