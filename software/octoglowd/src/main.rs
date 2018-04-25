extern crate i2cdev;

use i2cdev::core::*;
use i2cdev::linux::{LinuxI2CDevice, LinuxI2CError};
use std::fmt;
use std::thread;
use std::time::Duration;

struct WeatherSensorReport {
    temperature: f32,
    humidity: u8,
    battery_is_weak: bool,
}

impl fmt::Display for WeatherSensorReport {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(f, "[Temp: {} \u{00B0C}, humidity: {}%, {}]", self.temperature, self.humidity, match self.battery_is_weak {
            true => "batt weak",
            false => "batt OK"
        })
    }
}

const CLOCK_DISPLAY_ADDR: u16 = 0x10;

fn i2cfun() -> Result<WeatherSensorReport, LinuxI2CError> {
    let mut dev = LinuxI2CDevice::new("/dev/i2c-1", CLOCK_DISPLAY_ADDR)?;

    let mut buf: [u8; 5] = [0; 5];
    dev.smbus_write_byte(0x04).unwrap();
    //thread::sleep(Duration::from_millis(10));
    dev.read(&mut buf).unwrap();

    println!("{:?}", buf);

    return Ok(WeatherSensorReport {
        temperature: (256.0 * buf[2] as f32 + buf[1] as f32) / 10.0,
        humidity: buf[3],
        battery_is_weak: (buf[4] == 1),
    });
}

fn main() {
    println!("Hello, world!");
    println!("End {}", i2cfun().unwrap());
}