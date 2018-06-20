use chrono::{Duration, Local, NaiveDateTime};
use futures::prelude::*;
use i2c::{InsideWeatherSensorReport, OutsideWeatherSensorReport};
use rusqlite;
use error;

struct InsideWeatherReportModel {
    id: i64,
    timestamp: NaiveDateTime,
    temperature: f64,
    humidity: f64,
    pressure: f64,
}

struct OutsideWeatherReportModel {
    id: i64,
    timestamp: NaiveDateTime,
    temperature: f64,
    humidity: f64,
    weak_battery: bool,
}

pub struct Database {
    connection: rusqlite::Connection,
}

impl Database {
    pub fn new(database_file: &str) -> Database {
        let conn = rusqlite::Connection::open(database_file).expect(&format!("cannot open database file {}", database_file));

        Database::apply_migrations(&conn);

        Database { connection: conn }
    }

    /// Crude migration mechanism. Loads the SQL file and runs each SQL command.
    fn apply_migrations(conn: &rusqlite::Connection) {
        let migrations_file_content = include_str!("../migrations.sql");

        for cmd in migrations_file_content.split(";").map(|s| s.trim()).filter(|s| !s.is_empty()) {
            debug!("Executing SQL: {}", cmd);
            conn.execute(cmd, &[]).expect(&format!("cannot execute query {}", cmd));
        }
    }

    pub fn save_inside_weather_report<'a>(&'a self, report: &'a InsideWeatherSensorReport) -> impl Future<Item=(), Error=error::Error> + 'a {
        async_block! {
            self.connection.execute("INSERT INTO inside_weather_report (timestamp, temperature, humidity, pressure) VALUES(?1, ?2, ?3, ?4)",
                                &[&Local::now(), &(report.temperature as f64), &(report.humidity as f64), &(report.pressure as f64)])?;
            Ok(())
        }
    }

    pub fn save_outside_weather_report<'a>(&'a self, report: &'a OutsideWeatherSensorReport) -> impl Future<Item=(), Error=error::Error> + 'a {
        async_block! {
            self.connection.execute("INSERT INTO outside_weather_report (timestamp, temperature, humidity, weak_battery) VALUES (?1, ?2, ?3, ?4)",
                                &[&Local::now(), &(report.temperature as f64), &(report.humidity as f64), &(report.battery_is_weak)])?;
            Ok(())
        }
    }

    pub fn get_last_inside_weather_reports_by_hour<'a>(&'a self, num_of_last_hours: u32) -> impl Future<Item=Vec<InsideWeatherSensorReport>, Error=error::Error> + 'a {
        let sql = Database::create_sql_query_for_averaged_hours("inside_weather_report", &["temperature", "humidity", "pressure"],
                                                                &Local::now().naive_local(), num_of_last_hours);
        async_block! {
            let mut prepared_stmt = self.connection.prepare(&sql)?;
            let mapped_rows = prepared_stmt.query_map(&[], |row| InsideWeatherSensorReport {
                temperature: row.get(0),
                humidity: row.get(1),
                pressure: row.get(2)
            })?;
            let res = mapped_rows.into_iter().map(|r| r.unwrap()).collect();
            Ok(res)
        }
    }

    pub fn get_last_outside_weather_reports_by_hour<'a>(&'a self, num_of_last_hours: u32) -> impl Future<Item=Vec<OutsideWeatherSensorReport>, Error=error::Error> + 'a {
        let sql = Database::create_sql_query_for_averaged_hours("outside_weather_report", &["temperature", "humidity"],
                                                                &Local::now().naive_local(), num_of_last_hours);
        async_block! {
            let mut prepared_stmt = self.connection.prepare(&sql)?;
            let mapped_rows = prepared_stmt.query_map(&[], |row| OutsideWeatherSensorReport {
                temperature: row.get(0),
                humidity: row.get(1),
                battery_is_weak : false, // dummy value, we don't care
                already_read: false // dummy value
            })?;
            let res = mapped_rows.into_iter().map(|r| r.unwrap()).collect();
            Ok(res)
        }
    }

    fn create_sql_query_for_averaged_hours(table_name: &str, fields: &[&str], current_time: &NaiveDateTime, number_of_past_hours: u32) -> String {
        let per_hour_timestamps = (0..number_of_past_hours)
            .map(|h| current_time.checked_sub_signed(Duration::hours(h as i64)).unwrap())
            .map(|ts| ts.format("%Y-%m-%dT%H").to_string())
            .map(|s| format!("'{}'", s))
            .collect::<Vec<String>>()
            .join(", ");

        let groupby_expr = "strftime('%Y-%m-%dT%H', timestamp)";

        let field_expressions = fields.iter().map(|f| {
            let f_trimmed = f.trim();
            debug_assert!(!f_trimmed.is_empty());
            debug_assert!(f_trimmed.is_ascii());
            f_trimmed
        }).map(|f| format!("avg({}) as {}", f, f))
            .collect::<Vec<String>>()
            .join(", ");

        format!("SELECT {} FROM {} WHERE {} IN ({}) GROUP BY {} ORDER BY timestamp DESC LIMIT {}", field_expressions, table_name,
                groupby_expr, per_hour_timestamps, groupby_expr, number_of_past_hours)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use chrono::NaiveDate;

    #[test]
    fn test_create_sql_query_for_averaged_hours() {
        let ts = NaiveDate::from_ymd(2018, 5, 26).and_hms(20, 10, 11);

        assert_eq!("SELECT avg(temperature) as temperature, avg(humidity) as humidity, avg(pressure) as pressure FROM inside_weather_report WHERE strftime('%Y-%m-%dT%H', timestamp) IN ('2018-05-26T20', '2018-05-26T19', '2018-05-26T18', '2018-05-26T17', '2018-05-26T16', '2018-05-26T15', '2018-05-26T14') GROUP BY strftime('%Y-%m-%dT%H', timestamp) ORDER BY timestamp DESC LIMIT 7", Database::create_sql_query_for_averaged_hours("inside_weather_report", &["temperature", "humidity", "pressure"], &ts, 7));
    }
}