package vn.edu.taskmind.productivity.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.taskmind.productivity.domain.JournalEntry;
import vn.edu.taskmind.productivity.repository.JournalEntryRepository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

@RestController
@Profile("dev")
@RequestMapping("/api/journal-entries")
public class JournalController {
    private final JournalEntryRepository entries;
    private final CurrentUser user;

    public JournalController(JournalEntryRepository entries, CurrentUser user) {
        this.entries = entries;
        this.user = user;
    }

    public record Input(@NotBlank @Size(max=200) String title, @NotBlank String content,
                        @NotNull LocalDateTime entryDate, Boolean pinned) { }

    @GetMapping
    public List<JournalEntry> all() {
        return entries.findByUserId(user.id()).stream()
                .sorted(java.util.Comparator.comparing(JournalEntry::getEntryDate).reversed()).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JournalEntry create(@Valid @RequestBody Input body) { return save(null, body); }

    @PutMapping("/{id}")
    public JournalEntry update(@PathVariable long id, @Valid @RequestBody Input body) { return save(id, body); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) { entries.delete(owned(id)); }

    private JournalEntry save(Long id, Input body) {
        if (body.entryDate().isAfter(LocalDateTime.now(ZoneOffset.UTC)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thời gian nhật ký không được ở tương lai");
        JournalEntry entry = id == null ? new JournalEntry() : owned(id);
        if (id == null) entry.setUserId(user.id());
        entry.setTitle(body.title().trim());
        entry.setContent(body.content());
        entry.setEntryDate(body.entryDate());
        entry.setPinned(Boolean.TRUE.equals(body.pinned()));
        return entries.saveAndFlush(entry);
    }

    private JournalEntry owned(long id) {
        return entries.findById(id).filter(entry -> Objects.equals(entry.getUserId(), user.id()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy nhật ký"));
    }
}
