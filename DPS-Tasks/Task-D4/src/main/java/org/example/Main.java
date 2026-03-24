package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class Main {

    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/demo";
        String user = "postgres";
        String password = "123";

        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            connection.setAutoCommit(false);

            createRestoredFlightPricesTable(connection);
            createPricingRuleTable(connection);
            createUpcomingFlightPriceTable(connection);

            connection.commit();

            System.out.println("Task completed successfully.");
            System.out.println("Created/updated tables:");
            System.out.println(" - restored_flight_prices");
            System.out.println(" - pricing_rules");
            System.out.println(" - upcoming_flight_prices");
        } catch (SQLException ex) {
            System.err.println("Task failed: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private static void createRestoredFlightPricesTable(Connection connection) throws SQLException {
        String sql = """
                DROP TABLE IF EXISTS public.restored_flight_prices;

                CREATE TABLE public.restored_flight_prices AS
                WITH historical AS (
                    SELECT
                        f.flight_id,
                        f.route_no,
                        f.scheduled_departure,
                        s.fare_conditions,
                        s.price AS amount
                    FROM bookings.flights f
                    JOIN bookings.segments s ON s.flight_id = f.flight_id
                    WHERE f.actual_departure IS NOT NULL
                      AND f.status = 'Arrived'
                )
                SELECT
                    h.flight_id,
                    h.route_no,
                    h.scheduled_departure,
                    h.fare_conditions,
                    ROUND(AVG(h.amount)::numeric, 2) AS avg_amount,
                    ROUND(MIN(h.amount)::numeric, 2) AS min_amount,
                    ROUND(MAX(h.amount)::numeric, 2) AS max_amount,
                    ROUND((PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY h.amount))::numeric, 2) AS median_amount,
                    COUNT(*) AS booked_seats
                FROM historical h
                GROUP BY
                    h.flight_id,
                    h.route_no,
                    h.scheduled_departure,
                    h.fare_conditions;

                CREATE INDEX IF NOT EXISTS idx_restored_flight_prices_flight_id
                    ON public.restored_flight_prices (flight_id);
                """;

        executeSql(connection, sql);
    }

    private static void createPricingRuleTable(Connection connection) throws SQLException {
        String sql = """
                DROP TABLE IF EXISTS public.pricing_rules;

                CREATE TABLE public.pricing_rules AS
                WITH route_stats AS (
                    SELECT
                        r.route_no,
                        r.fare_conditions,
                        EXTRACT(ISODOW FROM r.scheduled_departure)::int AS day_of_week,
                        ROUND(AVG(r.median_amount)::numeric, 2) AS base_price,
                        ROUND(STDDEV_POP(r.median_amount)::numeric, 2) AS volatility,
                        COUNT(*) AS historical_flights
                    FROM public.restored_flight_prices r
                    GROUP BY
                        r.route_no,
                        r.fare_conditions,
                        EXTRACT(ISODOW FROM r.scheduled_departure)::int
                )
                SELECT
                    rs.route_no,
                    rs.fare_conditions,
                    rs.day_of_week,
                    rs.base_price,
                    COALESCE(rs.volatility, 0)::numeric(10,2) AS volatility,
                    rs.historical_flights,
                    CASE
                        WHEN rs.historical_flights >= 20 THEN 1.00::numeric(5,2)
                        WHEN rs.historical_flights >= 10 THEN 1.03::numeric(5,2)
                        ELSE 1.08::numeric(5,2)
                    END AS confidence_multiplier
                FROM route_stats rs;

                CREATE INDEX IF NOT EXISTS idx_pricing_rules_route
                    ON public.pricing_rules (route_no, fare_conditions, day_of_week);
                """;

        executeSql(connection, sql);
    }

    private static void createUpcomingFlightPriceTable(Connection connection) throws SQLException {
        String sql = """
                DROP TABLE IF EXISTS public.upcoming_flight_prices;

                CREATE TABLE public.upcoming_flight_prices AS
                WITH future_flights AS (
                    SELECT
                        f.flight_id,
                        f.route_no,
                        f.scheduled_departure,
                        EXTRACT(ISODOW FROM f.scheduled_departure)::int AS day_of_week
                    FROM bookings.flights f
                    WHERE f.actual_departure IS NULL
                      AND f.scheduled_departure >= NOW()
                ),
                all_fare_conditions AS (
                    SELECT DISTINCT fare_conditions
                    FROM bookings.segments
                ),
                priced AS (
                    SELECT
                        ff.flight_id,
                        ff.route_no,
                        ff.scheduled_departure,
                        afc.fare_conditions,
                        pr.base_price,
                        pr.confidence_multiplier
                    FROM future_flights ff
                    CROSS JOIN all_fare_conditions afc
                    LEFT JOIN public.pricing_rules pr
                        ON pr.route_no = ff.route_no
                       AND pr.fare_conditions = afc.fare_conditions
                       AND pr.day_of_week = ff.day_of_week
                ),
                fallback AS (
                    SELECT
                        fare_conditions,
                        ROUND(AVG(median_amount)::numeric, 2) AS global_price
                    FROM public.restored_flight_prices
                    GROUP BY fare_conditions
                )
                SELECT
                    p.flight_id,
                    p.route_no,
                    p.scheduled_departure,
                    p.fare_conditions,
                    ROUND((
                        COALESCE(
                            (p.base_price * p.confidence_multiplier),
                            fb.global_price
                        )
                    )::numeric, 2) AS predicted_price
                FROM priced p
                LEFT JOIN fallback fb
                    ON fb.fare_conditions = p.fare_conditions;

                CREATE INDEX IF NOT EXISTS idx_upcoming_flight_prices_flight_id
                    ON public.upcoming_flight_prices (flight_id, fare_conditions);
                """;

        executeSql(connection, sql);
    }

    private static void executeSql(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}