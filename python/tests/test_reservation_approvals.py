from datetime import datetime, timedelta

from approvaltests import verify
from approvaltests.reporters.testing_reporter import ReporterForTesting

from src.warehouse.warehouse_desk_app import SeedItem, WarehouseDeskApp

NOW = datetime(2026, 4, 1, 13, 17)


def test_stock_reservation_scenarios():
    verify(
        "\n\n".join(
            [
                scenario(
                    "reserve available stock",
                    [
                        ("RESERVE;alice;STAPLER;2;10", NOW),
                        ("COUNT;STAPLER", NOW),
                    ],
                ),
                scenario(
                    "reject reservation when stock is unavailable",
                    [("RESERVE;bob;STAPLER;5;10", NOW)],
                ),
                scenario(
                    "confirm reservation ships an order",
                    [
                        ("RESERVE;alice;STAPLER;2;10", NOW),
                        ("CONFIRM;R1001", NOW),
                        ("COUNT;STAPLER", NOW),
                    ],
                ),
                scenario(
                    "release reservation returns stock to availability",
                    [
                        ("RESERVE;alice;STAPLER;2;10", NOW),
                        ("RELEASE;R1001", NOW),
                        ("COUNT;STAPLER", NOW),
                    ],
                ),
                scenario(
                    "reservation expires before the next command",
                    [
                        ("RESERVE;alice;STAPLER;2;10", NOW),
                        ("COUNT;STAPLER", NOW + timedelta(minutes=11)),
                    ],
                ),
                scenario(
                    "expired reservation cannot be confirmed",
                    [
                        ("RESERVE;alice;STAPLER;2;10", NOW),
                        ("CONFIRM;R1001", NOW + timedelta(minutes=11)),
                    ],
                ),
            ]
        ),
        reporter=ReporterForTesting(),
    )


def scenario(name: str, steps: list[tuple[str, datetime]]) -> str:
    app = seeded_app()
    for command, current_time in steps:
        app.process_line(command, current_time)

    return "\n".join([f"## {name}", *app.event_log])


def seeded_app() -> WarehouseDeskApp:
    app = WarehouseDeskApp()
    app.seed_data(
        [
            SeedItem("PEN-BLACK", 1.5, 40),
            SeedItem("PEN-BLUE", 1.6, 25),
            SeedItem("NOTE-A5", 4.0, 15),
            SeedItem("STAPLER", 12.0, 4),
        ],
        starting_cash=300.0,
        starting_order_number=1001,
    )
    return app
