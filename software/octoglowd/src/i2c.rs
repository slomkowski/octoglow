extern crate actix;
extern crate i2cdev;

use i2c::actix::{Actor, Addr, Arbiter, Context, Handler, msgs, Syn, System};
use i2c::i2cdev::core::*;
use i2c::i2cdev::linux::{LinuxI2CDevice, LinuxI2CError};
use message::*;
use std::io;
use std::thread;
use std::time::Duration;

const CLOCK_DISPLAY_ADDR: u16 = 0x10;
const GEIGER_ADDR: u16 = 0x12;
const FRONT_DISPLAY_ADDR: u16 = 0x14;

const CLOCK_DISPLAY_UPPER_DOT: u8 = 1 << (14 % 8);
const CLOCK_DISPLAY_LOWER_DOT: u8 = 1 << (13 % 8);

pub struct I2CRunner {
    clock_display_device: LinuxI2CDevice,
    front_display_device: LinuxI2CDevice,
    geiger_device: LinuxI2CDevice,
}

impl I2CRunner {
    pub fn new() -> I2CRunner {
        let bus_path = "/dev/i2c-1";

        I2CRunner {
            clock_display_device: LinuxI2CDevice::new(bus_path, CLOCK_DISPLAY_ADDR).unwrap(),
            front_display_device: LinuxI2CDevice::new(bus_path, FRONT_DISPLAY_ADDR).unwrap(),
            geiger_device: LinuxI2CDevice::new(bus_path, GEIGER_ADDR).unwrap(),
        }
    }
}

impl Actor for I2CRunner {
    type Context = Context<Self>;
}

impl Handler<ClockDisplayContent> for I2CRunner {
    type Result = ();

    fn handle(&mut self, dc: ClockDisplayContent, ctx: &mut Context<Self>) {
        let dots = (if dc.upper_dot { CLOCK_DISPLAY_UPPER_DOT } else { 0 }) | (if dc.lower_dot { CLOCK_DISPLAY_LOWER_DOT } else { 0 });
        let buf: [u8; 6] = [0x1, dc.digits[0] + 0x30, dc.digits[1] + 0x30, dc.digits[2] + 0x30, dc.digits[3] + 0x30, dots];

        self.clock_display_device.write(&buf);
    }
}

impl Handler<SetBrightness> for I2CRunner {
    type Result = ();

    fn handle(&mut self, br: SetBrightness, ctx: &mut Context<Self>) {
        self.clock_display_device.write(&[3, br.brightness]);
        self.front_display_device.write(&[3, br.brightness]);

        // todo make command using saved state
    }
}

impl Handler<GetWeatherSensorReport> for I2CRunner {
    type Result = Result<WeatherSensorReport, io::Error>;

    fn handle(&mut self, _: GetWeatherSensorReport, ctx: &mut Context<Self>) -> <Self as Handler<GetWeatherSensorReport>>::Result {
        let mut buf: [u8; 5] = [0; 5];
        self.clock_display_device.write(&[0x04]);
        self.clock_display_device.read(&mut buf);

        let report = WeatherSensorReport {
            temperature: (256.0 * buf[2] as f32 + buf[1] as f32) / 10.0,
            humidity: buf[3],
            battery_is_weak: (buf[4] == 1),
        };

        Ok(report)
    }
}

impl Handler<FrontDisplayClear> for I2CRunner {
    type Result = ();

    fn handle(&mut self, _: FrontDisplayClear, ctx: &mut Context<Self>) {
        self.front_display_device.write(&[2]);
    }
}

impl Handler<FrontDisplayStaticText> for I2CRunner {
    type Result = ();

    fn handle(&mut self, st: FrontDisplayStaticText, ctx: &mut Context<Self>) {
        let cmd = vec![4, st.position, st.max_length];
        self.front_display_device.write(&cmd);
    }
}

