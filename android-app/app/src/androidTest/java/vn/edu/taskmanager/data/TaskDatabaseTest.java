package vn.edu.taskmanager.data;

import android.content.Context;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TaskDatabaseTest {
    @Test public void tasksSurviveReopeningAndSupportUpdatesAndDeletion() {
        Context context = ApplicationProvider.getApplicationContext();
        String name = "task-persistence-test.db";
        context.deleteDatabase(name);
        TaskDatabase db = Room.databaseBuilder(context, TaskDatabase.class, name).build();
        try {
            long id = db.taskDao().insert(new Task("Học Android"));
            db.close();
            db = Room.databaseBuilder(context, TaskDatabase.class, name).build();
            assertEquals("Học Android", db.taskDao().findById(id).title);
            assertFalse(db.taskDao().findById(id).completed);
            db.taskDao().rename(id, "Ôn Room");
            db.taskDao().setCompleted(id, true);
            assertEquals("Ôn Room", db.taskDao().findById(id).title);
            assertTrue(db.taskDao().findById(id).completed);
            db.taskDao().setCompleted(id, false);
            assertFalse(db.taskDao().findById(id).completed);
            db.taskDao().delete(id);
            assertNull(db.taskDao().findById(id));
        } finally {
            db.close();
            context.deleteDatabase(name);
        }
    }
}
