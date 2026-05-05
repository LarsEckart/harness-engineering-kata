package org.sammancoaching.warehouse;

import org.approvaltests.Approvals;
import org.approvaltests.reporters.QuietReporter;
import org.approvaltests.reporters.UseReporter;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@UseReporter(QuietReporter.class)
class ReservationApprovalTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 4, 1, 13, 17);

    @Test
    void stockReservationScenarios() {
        Approvals.verify(String.join("\n\n", List.of(
            scenario("reserve available stock", List.of(
                step("RESERVE;alice;STAPLER;2;10", NOW),
                step("COUNT;STAPLER", NOW)
            )),
            scenario("reject reservation when stock is unavailable", List.of(
                step("RESERVE;bob;STAPLER;5;10", NOW)
            )),
            scenario("confirm reservation ships an order", List.of(
                step("RESERVE;alice;STAPLER;2;10", NOW),
                step("CONFIRM;R1001", NOW),
                step("COUNT;STAPLER", NOW)
            )),
            scenario("release reservation returns stock to availability", List.of(
                step("RESERVE;alice;STAPLER;2;10", NOW),
                step("RELEASE;R1001", NOW),
                step("COUNT;STAPLER", NOW)
            )),
            scenario("reservation expires before the next command", List.of(
                step("RESERVE;alice;STAPLER;2;10", NOW),
                step("COUNT;STAPLER", NOW.plusMinutes(11))
            )),
            scenario("expired reservation cannot be confirmed", List.of(
                step("RESERVE;alice;STAPLER;2;10", NOW),
                step("CONFIRM;R1001", NOW.plusMinutes(11))
            ))
        )));
    }

    private static ScenarioStep step(String command, LocalDateTime currentTime) {
        return new ScenarioStep(command, currentTime);
    }

    private static String scenario(String name, List<ScenarioStep> steps) {
        WarehouseDeskApp app = seededApp();
        for (ScenarioStep step : steps) {
            app.processLine(step.command(), step.currentTime());
        }

        List<String> lines = new ArrayList<>();
        lines.add("## " + name);
        lines.addAll(app.getEventLog());
        return String.join("\n", lines);
    }

    private static WarehouseDeskApp seededApp() {
        WarehouseDeskApp app = new WarehouseDeskApp();
        app.seedData(
            List.of(
                new WarehouseDeskApp.SeedItem("PEN-BLACK", 1.5, 40),
                new WarehouseDeskApp.SeedItem("PEN-BLUE", 1.6, 25),
                new WarehouseDeskApp.SeedItem("NOTE-A5", 4.0, 15),
                new WarehouseDeskApp.SeedItem("STAPLER", 12.0, 4)
            ),
            300.0,
            1001
        );
        return app;
    }

    private record ScenarioStep(String command, LocalDateTime currentTime) {}
}
