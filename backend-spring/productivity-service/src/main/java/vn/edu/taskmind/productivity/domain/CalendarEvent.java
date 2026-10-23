package vn.edu.taskmind.productivity.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "calendarevents")
public class CalendarEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "EventID", nullable = false)
    private Integer eventId;

    @Column(name = "UserID", nullable = false)
    private Integer userId;

    @Column(name = "Title", nullable = false, length = 200)
    private String title;

    @Column(name = "StartTime", nullable = false, columnDefinition = "datetime")
    private LocalDateTime startTime;

    @Column(name = "EndTime", nullable = false, columnDefinition = "datetime")
    private LocalDateTime endTime;

    @Column(name = "Location", nullable = true, length = 200)
    private String location;

    public CalendarEvent() { }

    public Integer getEventId() { return eventId; }
    public void setEventId(Integer eventId) { this.eventId = eventId; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
}
