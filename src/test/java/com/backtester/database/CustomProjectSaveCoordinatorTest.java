package com.backtester.database;

import com.backtester.workflow.CustomProject;
import com.backtester.workflow.DatabankManager;
import com.backtester.workflow.FilterCondition;
import com.backtester.workflow.WorkflowTask;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class CustomProjectSaveCoordinatorTest {

    @Test
    public void rapidRequestsAreDebouncedAndLatestMetadataWins() throws Exception {
        AtomicInteger writeCount = new AtomicInteger();
        AtomicReference<CustomProject> written = new AtomicReference<>();
        CountDownLatch writeFinished = new CountDownLatch(1);
        DatabankManager databanks = new DatabankManager();

        try (CustomProjectSaveCoordinator saver = new CustomProjectSaveCoordinator(
                100,
                project -> {
                    writeCount.incrementAndGet();
                    written.set(project);
                    writeFinished.countDown();
                    return true;
                },
                null)) {
            CustomProject project = projectWithTask("First");
            saver.requestSave(project, databanks);
            project.setName("Second");
            saver.requestSave(project, databanks);
            project.setName("Latest");
            saver.requestSave(project, databanks);

            assertTrue(writeFinished.await(2, TimeUnit.SECONDS));
            assertTrue(saver.flush(Duration.ofSeconds(2)));
            assertEquals(1, writeCount.get());
            assertEquals("Latest", written.get().getName());
        }
    }

    @Test
    public void callerDoesNotRunSlowWriterAndFlushWaitsForCompletion() throws Exception {
        Thread caller = Thread.currentThread();
        AtomicReference<Thread> writerThread = new AtomicReference<>();
        CountDownLatch writerStarted = new CountDownLatch(1);
        CountDownLatch allowWriterToFinish = new CountDownLatch(1);

        try (CustomProjectSaveCoordinator saver = new CustomProjectSaveCoordinator(
                0,
                project -> {
                    writerThread.set(Thread.currentThread());
                    writerStarted.countDown();
                    try {
                        return allowWriterToFinish.await(2, TimeUnit.SECONDS);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                },
                null)) {
            saver.requestSave(projectWithTask("Async"), new DatabankManager());

            assertTrue(writerStarted.await(1, TimeUnit.SECONDS));
            assertNotSame(caller, writerThread.get());
            allowWriterToFinish.countDown();
            assertTrue(saver.flush(Duration.ofSeconds(2)));
        }
    }

    @Test
    public void changeDuringActiveWriteIsPersistedAfterOlderSnapshot() throws Exception {
        AtomicInteger writeCount = new AtomicInteger();
        AtomicReference<String> lastWrittenName = new AtomicReference<>();
        CountDownLatch firstWriteStarted = new CountDownLatch(1);
        CountDownLatch allowFirstWriteToFinish = new CountDownLatch(1);

        try (CustomProjectSaveCoordinator saver = new CustomProjectSaveCoordinator(
                0,
                project -> {
                    int currentWrite = writeCount.incrementAndGet();
                    if (currentWrite == 1) {
                        firstWriteStarted.countDown();
                        try {
                            if (!allowFirstWriteToFinish.await(2, TimeUnit.SECONDS)) return false;
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            return false;
                        }
                    }
                    lastWrittenName.set(project.getName());
                    return true;
                },
                null)) {
            CustomProject project = projectWithTask("Old");
            DatabankManager databanks = new DatabankManager();
            saver.requestSave(project, databanks);
            assertTrue(firstWriteStarted.await(1, TimeUnit.SECONDS));

            project.setName("New");
            saver.requestSave(project, databanks);
            allowFirstWriteToFinish.countDown();

            assertTrue(saver.flush(Duration.ofSeconds(2)));
            assertEquals(2, writeCount.get());
            assertEquals("New", lastWrittenName.get());
        }
    }

    @Test
    public void persistenceSnapshotDetachesTaskAndFilterMetadata() {
        CustomProject project = projectWithTask("Snapshot");
        WorkflowTask originalTask = project.getTasks().get(0);
        CustomProject snapshot = project.copyMetadataForPersistence();

        originalTask.setName("Changed");
        originalTask.getFilterConditions().get(0).setValue(99.0);

        assertEquals("Task", snapshot.getTasks().get(0).getName());
        assertEquals(1.2, snapshot.getTasks().get(0).getFilterConditions().get(0).getValue(), 0.0);
        assertTrue(snapshot.getDatabanks().isEmpty());
    }

    private static CustomProject projectWithTask(String name) {
        CustomProject project = new CustomProject(name, "EA.ex5", "EURUSD", "H1");
        WorkflowTask task = new WorkflowTask("Task", WorkflowTask.TaskType.PRE_FILTER);
        task.setFilterConditions(List.of(new FilterCondition(
                FilterCondition.Metric.BT_PROFIT_FACTOR,
                FilterCondition.Operator.GREATER_EQUAL,
                1.2)));
        project.addTask(task);
        return project;
    }
}
