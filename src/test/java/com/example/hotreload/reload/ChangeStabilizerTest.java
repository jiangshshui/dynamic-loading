package com.example.hotreload.reload;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChangeStabilizerTest {

    private final ChangeStabilizer stabilizer = new ChangeStabilizer();
    private final Path file = Path.of("GreetingServiceImpl.java");

    @Test
    void waitsUntilTheFileStopsChanging() {
        ChangeStabilizer.Fingerprint first = new ChangeStabilizer.Fingerprint(1, 10);
        ChangeStabilizer.Fingerprint second = new ChangeStabilizer.Fingerprint(2, 11);
        ChangeStabilizer.Fingerprint third = new ChangeStabilizer.Fingerprint(3, 12);
        stabilizer.baseline(Map.of(file, first));

        ChangeStabilizer.Result unchanged = stabilizer.observe(Map.of(file, first), 1_000, 500);
        assertThat(unchanged.startedWaiting()).isEmpty();
        assertThat(unchanged.ready()).isEmpty();

        ChangeStabilizer.Result started = stabilizer.observe(Map.of(file, second), 2_000, 500);
        assertThat(started.startedWaiting()).containsExactly(file);
        assertThat(started.ready()).isEmpty();

        ChangeStabilizer.Result stillWriting = stabilizer.observe(Map.of(file, third), 2_400, 500);
        assertThat(stillWriting.startedWaiting()).containsExactly(file);
        assertThat(stillWriting.ready()).isEmpty();

        ChangeStabilizer.Result waiting = stabilizer.observe(Map.of(file, third), 2_899, 500);
        assertThat(waiting.startedWaiting()).isEmpty();
        assertThat(waiting.ready()).isEmpty();

        ChangeStabilizer.Result ready = stabilizer.observe(Map.of(file, third), 2_900, 500);
        assertThat(ready.startedWaiting()).isEmpty();
        assertThat(ready.ready()).containsExactly(file);

        ChangeStabilizer.Result afterReload = stabilizer.observe(Map.of(file, third), 4_000, 500);
        assertThat(afterReload.startedWaiting()).isEmpty();
        assertThat(afterReload.ready()).isEmpty();
    }

    @Test
    void newFileAfterStartupAlsoWaitsForAQuietPeriod() {
        ChangeStabilizer.Fingerprint created = new ChangeStabilizer.Fingerprint(5, 20);
        stabilizer.baseline(Map.of());

        ChangeStabilizer.Result started = stabilizer.observe(Map.of(file, created), 100, 500);
        assertThat(started.startedWaiting()).containsExactly(file);
        assertThat(started.ready()).isEmpty();

        ChangeStabilizer.Result ready = stabilizer.observe(Map.of(file, created), 600, 500);
        assertThat(ready.ready()).containsExactly(file);
    }

    @Test
    void deletedFileIsNotReloaded() {
        stabilizer.baseline(Map.of(file, new ChangeStabilizer.Fingerprint(1, 1)));

        ChangeStabilizer.Result deleted = stabilizer.observe(Map.of(), 1_000, 500);
        assertThat(deleted.startedWaiting()).isEmpty();
        assertThat(deleted.ready()).isEmpty();
    }
}
