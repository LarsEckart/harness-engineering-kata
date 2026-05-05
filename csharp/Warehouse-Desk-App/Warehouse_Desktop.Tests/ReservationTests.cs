using Warehouse_Desktop;
using VerifyNUnit;

namespace Warehouse_Desktop.Tests;

public class ReservationTests
{
    private static readonly DateTime Now = new(2026, 4, 1, 13, 17, 0);

    [Test]
    public Task StockReservationScenarios()
    {
        string output = string.Join("\n\n", new[]
        {
            Scenario(
                "reserve available stock",
                new[]
                {
                    Step("RESERVE;alice;STAPLER;2;10", Now),
                    Step("COUNT;STAPLER", Now)
                }
            ),
            Scenario(
                "reject reservation when stock is unavailable",
                new[]
                {
                    Step("RESERVE;bob;STAPLER;5;10", Now)
                }
            ),
            Scenario(
                "confirm reservation ships an order",
                new[]
                {
                    Step("RESERVE;alice;STAPLER;2;10", Now),
                    Step("CONFIRM;R1001", Now),
                    Step("COUNT;STAPLER", Now)
                }
            ),
            Scenario(
                "release reservation returns stock to availability",
                new[]
                {
                    Step("RESERVE;alice;STAPLER;2;10", Now),
                    Step("RELEASE;R1001", Now),
                    Step("COUNT;STAPLER", Now)
                }
            ),
            Scenario(
                "reservation expires before the next command",
                new[]
                {
                    Step("RESERVE;alice;STAPLER;2;10", Now),
                    Step("COUNT;STAPLER", Now.AddMinutes(11))
                }
            ),
            Scenario(
                "expired reservation cannot be confirmed",
                new[]
                {
                    Step("RESERVE;alice;STAPLER;2;10", Now),
                    Step("CONFIRM;R1001", Now.AddMinutes(11))
                }
            )
        });

        return Verifier.Verify(output);
    }

    private static ScenarioStep Step(string command, DateTime currentTime)
    {
        return new ScenarioStep(command, currentTime);
    }

    private static string Scenario(string name, IEnumerable<ScenarioStep> steps)
    {
        WarehouseDeskApp app = SeededApp();
        foreach (ScenarioStep step in steps)
        {
            app.ProcessLine(step.Command, step.CurrentTime);
        }

        return string.Join("\n", new[] { "## " + name }.Concat(app.EventLog));
    }

    private static WarehouseDeskApp SeededApp()
    {
        WarehouseDeskApp app = new WarehouseDeskApp();
        app.SeedData(
            new List<WarehouseDeskApp.SeedItem>
            {
                new("PEN-BLACK", 1.5, 40),
                new("PEN-BLUE", 1.6, 25),
                new("NOTE-A5", 4.0, 15),
                new("STAPLER", 12.0, 4)
            },
            startingCash: 300.0,
            startingOrderNumber: 1001
        );
        return app;
    }

    private record ScenarioStep(string Command, DateTime CurrentTime);
}
