#![feature(proc_macro, proc_macro_non_items, generators)]
extern crate byteorder;
extern crate chrono;
extern crate config;
extern crate futures_await as futures;
extern crate i2cdev;
extern crate image;
#[macro_use]
extern crate log;
extern crate num_traits;
extern crate rusqlite;
extern crate simplelog;

use futures::prelude::*;
use std::{thread, time};
use views::View;

mod error;
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

    let views: Vec<Box<View>> = vec![
        Box::new(views::weather_inside::WeatherInsideView::new(&interface, &database)),
        Box::new(views::weather_outside::WeatherOutsideView::new(&interface, &database)),
    ];

    let clock_view = views::clock::ClockView::new(&interface);

    let mut current_view: &Box<View> = &views[0];

    loop {
        let button_report = interface.get_button_report();

        current_view.update_state().unwrap();
        current_view.draw_on_front_screen(views::DrawViewOnScreenMode::First).unwrap();

        for _ in 0..6 {
            current_view.draw_on_front_screen(views::DrawViewOnScreenMode::Update).unwrap();
            clock_view.draw_on_front_screen(views::DrawViewOnScreenMode::First).unwrap();
            thread::sleep(time::Duration::from_secs(10));
        }
    }
}