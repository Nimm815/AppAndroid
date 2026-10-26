package vn.edu.taskmind.backend.user;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "UserID") private Integer userId;
    @Column(name = "FullName", nullable = false, length = 100) private String fullName;
    @Column(name = "Email", nullable = false, unique = true, length = 255) private String email;
    @Column(name = "PasswordHash", nullable = false, length = 255) private String passwordHash;
    @Column(name = "Role", nullable = false, length = 10) private String role = "USER";
    @Column(name = "IsLocked", nullable = false) private boolean locked;
    @Column(name = "IsDeleted", nullable = false) private boolean deleted;
    @Column(name = "CreatedAt", insertable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "UpdatedAt", insertable = false, updatable = false) private LocalDateTime updatedAt;

    public Integer getUserId() { return userId; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getRole() { return role; }
    public boolean isLocked() { return locked; }
    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
}
