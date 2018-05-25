use actix::prelude::*;
use chrono::prelude::*;
use dotenv::dotenv;
use std::env;
use message::*;
use chrono;

use schema::*;
use diesel::prelude::*;
use diesel;
use diesel::expression::sql_literal::sql;

#[derive(Queryable)]
struct InsideWeatherReportModel {
    id: i64,
    timestamp: chrono::NaiveDateTime,
    temperature: f32,
    humidity: f32,
    pressure: f32,
}

#[derive(Queryable)]
struct BareInsideWeatherReportModel {
    temperature: f32,
    humidity: f32,
    pressure: f32,
}

#[derive(Insertable)]
#[table_name = "inside_weather_report"]
struct NewInsideWeatherReportModel {
    timestamp: chrono::NaiveDateTime,
    temperature: f32,
    humidity: f32,
    pressure: f32,
}

impl NewInsideWeatherReportModel {
    fn new(dt: &chrono::DateTime<chrono::Local>, r: &InsideWeatherSensorReport) -> NewInsideWeatherReportModel {
        NewInsideWeatherReportModel {
            timestamp: dt.naive_local(),
            temperature: r.temperature,
            humidity: r.humidity,
            pressure: r.pressure,
        }
    }
}

#[derive(Queryable)]
struct OutsideWeatherReportModel {
    id: i64,
    timestamp: chrono::NaiveDateTime,
    temperature: f32,
    humidity: f32,
    weak_battery: bool,
}

#[derive(Insertable)]
#[table_name = "outside_weather_report"]
struct NewOutsideWeatherReportModel {
    timestamp: chrono::NaiveDateTime,
    temperature: f32,
    humidity: f32,
    weak_battery: bool,
}

impl NewOutsideWeatherReportModel {
    fn new(dt: &chrono::DateTime<chrono::Local>, r: &OutsideWeatherSensorReport) -> NewOutsideWeatherReportModel {
        NewOutsideWeatherReportModel {
            timestamp: dt.naive_local(),
            temperature: r.temperature,
            humidity: r.humidity,
            weak_battery: r.battery_is_weak,
        }
    }
}

pub struct DatabaseActor {
    connection: SqliteConnection,
}

impl Default for DatabaseActor {
    fn default() -> DatabaseActor {
        dotenv().ok();

        let database_url = env::var("DATABASE_URL").expect("DATABASE_URL must be set");
        let connection = SqliteConnection::establish(&database_url).expect(&format!("Error connecting to {}", database_url));

        DatabaseActor { connection }
    }
}

impl DatabaseActor {}

impl actix::Supervised for DatabaseActor {}

impl Actor for DatabaseActor {
    type Context = Context<Self>;
}

impl ArbiterService for DatabaseActor {}

impl Handler<InsideWeatherSensorReport> for DatabaseActor {
    type Result = ();

    fn handle(&mut self, report: InsideWeatherSensorReport, ctx: &mut Context<Self>) {
        use diesel::dsl::avg;

        let model = NewInsideWeatherReportModel::new(&Local::now(), &report);
        diesel::insert_into(inside_weather_report::table)
            .values(&model)
            .execute(&self.connection)
            .expect("Cannot save inside weather report to DB");


        let query = sql(r#"
SELECT
  avg(temperature) AS temperature,
  avg(humidity)    AS humidity,
  avg(pressure)    AS pressure
FROM inside_weather_report
GROUP BY strftime('%Y-%m-%dT%H', timestamp)
ORDER BY timestamp
  DESC
LIMIT 5;"#);

        let results = query.get_results::<BareInsideWeatherReportModel>(&self.connection)
            .expect("cannot retrieve previous inside weather reports");

    }
}

impl Handler<OutsideWeatherSensorReport> for DatabaseActor {
    type Result = ();

    fn handle(&mut self, report: OutsideWeatherSensorReport, ctx: &mut Context<Self>) {
        let model = NewOutsideWeatherReportModel::new(&Local::now(), &report);
        diesel::insert_into(outside_weather_report::table)
            .values(&model)
            .execute(&self.connection)
            .expect("Cannot save outside weather report to DB");
    }
}
