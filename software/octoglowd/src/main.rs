#![feature(proc_macro, proc_macro_non_items, generators)]
extern crate futures_await as futures;
extern crate byteorder;
extern crate chrono;
extern crate config;
extern crate i2cdev;
extern crate image;
#[macro_use]
extern crate log;
extern crate simplelog;

extern crate rusqlite;
extern crate num_traits;

use futures::prelude::*;
use std::{thread, time};

mod database;
mod i2c;
mod views;


fn main() {
    simplelog::CombinedLogger::init(vec![simplelog::TermLogger::new(simplelog::LevelFilter::Debug,
                                                                    simplelog::Config::default()).unwrap()]).unwrap();
    let config = {
        let mut s = config::Config::default();
        s.merge(config::File::with_name("config")).expect("cannot read config file");
        s.merge(config::Environment::with_prefix("APP")).expect("cannot read config from environment variables");
        s
    };

    let database = database::Database::new(&config.get_str("database.file").unwrap());
    let interface = i2c::Interface::new(&config.get_str("i2c.bus-device-file").unwrap());

    thread::sleep(time::Duration::from_secs(2));

    interface.front_display_clear().wait().unwrap();
    interface.set_brightness(1).wait().unwrap();
    interface.front_display_upper_bar_active_positions(&[1, 3, 5, 15]).wait().unwrap();

    database.save_inside_weather_report(&i2c::InsideWeatherSensorReport {
        temperature: 33.0,
        humidity: 15.2,
        pressure: 1001.3,
    }).wait().unwrap();

    let i = views::WeatherInsideView::new(&interface, &database);

    i.update_state().wait().unwrap();
    i.draw_on_front_screen(views::DrawViewOnScreenMode::First).wait().unwrap();

    database.get_last_inside_weather_reports_by_hour(4).wait().unwrap();
    database.get_last_outside_weather_reports_by_hour(4).wait().unwrap();
}