#![feature(proc_macro, proc_macro_non_items, generators)]
extern crate futures_await as futures;
extern crate byteorder;
extern crate chrono;
extern crate config;
extern crate i2cdev;
extern crate image;
#[macro_use]
extern crate lazy_static;
#[macro_use]
extern crate log;
extern crate simplelog;
//#[macro_use]
//extern crate diesel;
extern crate dotenv;
extern crate num_traits;

use futures::prelude::*;
use std::sync::RwLock;


mod i2c;

lazy_static! {
	static ref SETTINGS: RwLock<config::Config> = RwLock::new({
	    let mut s = config::Config::default();
	    s.merge(config::File::with_name("config")).unwrap();
        s.merge(config::Environment::with_prefix("APP")).unwrap();
        s
        });
}

fn main() {
    simplelog::CombinedLogger::init(vec![simplelog::TermLogger::new(simplelog::LevelFilter::Info,
                                                                    simplelog::Config::default()).unwrap()]).unwrap();

    let mut interface = i2c::Interface::new();
    interface.front_display_clear().wait().unwrap();
    interface.set_brightness(1).wait().unwrap();
    interface.front_display_upper_bar_active_positions(&[1, 3, 5, 15]).wait().unwrap();

}