package vn.edu.taskmind.auth.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Maps only columns in the existing taskmind_auth_db.users table. */
@Entity
@Table(name = "users")
public class UserAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "UserID", nullable = false)
    private Integer userId;
    @Column(name = "FullName", nullable = false, length = 100)
    private String fullName;
    @Column(name = "Email", nullable = false, length = 255, unique = true)
    private String email;
    @Column(name = "PasswordHash", nullable = false, length = 255)
    private String passwordHash;
    @Column(name = "Role", nullable = false, length = 120)
    private String role;
    @Column(name = "IsLocked", nullable = false, columnDefinition = "tinyint(1)")
    private Boolean isLocked = false;
    @Column(name = "IsDeleted", nullable = false, columnDefinition = "tinyint(1)")
    private Boolean isDeleted = false;
    @Column(name = "UpdatedAt", nullable = false, columnDefinition = "datetime")
    private LocalDateTime updatedAt;
    @Column(name = "CreatedAt", nullable = false, columnDefinition = "datetime")
    private LocalDateTime createdAt;

    public Integer getUserId() { return userId; }
    public void setUserId(Integer value) { userId = value; }
    public String getFullName() { return fullName; }
    public void setFullName(String value) { fullName = value; }
    public String getEmail() { return email; }
    public void setEmail(String value) { email = value; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String value) { passwordHash = value; }
    public String getRole() { return role; }
    public void setRole(String value) { role = value; }
    public Boolean getIsLocked() { return isLocked; }
    public void setIsLocked(Boolean value) { isLocked = value; }
    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean value) { isDeleted = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { updatedAt = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }

    @PrePersist
    private void initializeTimestamps() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    private void updateTimestamp() { updatedAt = LocalDateTime.now(ZoneOffset.UTC); }
}
