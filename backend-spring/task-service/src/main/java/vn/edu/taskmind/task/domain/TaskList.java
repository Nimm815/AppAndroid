package vn.edu.taskmind.task.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "tasklists")
public class TaskList {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ListID", nullable = false)
    private Integer listId;

    @Column(name = "UserID", nullable = false)
    private Integer userId;

    @Column(name = "ListName", nullable = false, length = 100)
    private String listName;

    @Column(name = "Color", nullable = true, length = 20)
    private String color;

    @Column(name = "CreatedAt", nullable = false, columnDefinition = "datetime")
    private LocalDateTime createdAt;

    public TaskList() { }

    public Integer getListId() { return listId; }
    public void setListId(Integer listId) { this.listId = listId; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public String getListName() { return listName; }
    public void setListName(String listName) { this.listName = listName; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @PrePersist
    private void initializeTimestamps() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (createdAt == null) createdAt = now;
    }
}
