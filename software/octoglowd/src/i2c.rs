extern crate actix;
extern crate i2cdev;

use i2c::actix::prelude::*;
use i2c::i2cdev::core::*;
use i2c::i2cdev::linux::{LinuxI2CDevice, LinuxI2CError};
use message::*;
use std::io;
use std::thread;
use std::time::Duration;
use std::mem::transmute;

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

impl Default for I2CRunner {
    fn default() -> I2CRunner {
        let bus_path = "/dev/i2c-1";

        I2CRunner {
            clock_display_device: LinuxI2CDevice::new(bus_path, CLOCK_DISPLAY_ADDR).unwrap(),
            front_display_device: LinuxI2CDevice::new(bus_path, FRONT_DISPLAY_ADDR).unwrap(),
            geiger_device: LinuxI2CDevice::new(bus_path, GEIGER_ADDR).unwrap(),
        }
    }
}

impl I2CRunner {
    pub fn new() -> I2CRunner {
        return Default::default();
    }

    fn write_graphics(&mut self, position: u8, sum_with_existing_text: bool, content: &[u8]) {
        let mut cmd = vec![6, position, content.len() as u8, sum_with_existing_text as u8];
        cmd.extend(content.iter());
        self.front_display_device.write(&cmd);
    }
}

impl actix::Supervised for I2CRunner {}

impl Actor for I2CRunner {
    type Context = Context<Self>;
}

impl ArbiterService for I2CRunner {}

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

impl Handler<ClockDisplayGetWeatherReport> for I2CRunner {
    type Result = Result<WeatherSensorReport, io::Error>;

    fn handle(&mut self, _: ClockDisplayGetWeatherReport, ctx: &mut Context<Self>) -> <Self as Handler<ClockDisplayGetWeatherReport>>::Result {
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
        let mut cmd = vec![4, st.position, st.max_length];
        cmd.extend(st.text.as_bytes().iter());
        cmd.push(0);
        self.front_display_device.write(&cmd);
    }
}

impl Handler<FrontDisplayScrollingText> for I2CRunner {
    type Result = ();

    fn handle(&mut self, st: FrontDisplayScrollingText, ctx: &mut Context<Self>) {
        let mut cmd = vec![5, st.slot_number, st.position, st.length];
        cmd.extend(st.text.as_bytes().iter());
        cmd.push(0);
        self.front_display_device.write(&cmd);
    }
}

impl Handler<FrontDisplayGraphics> for I2CRunner {
    type Result = ();

    fn handle(&mut self, g: FrontDisplayGraphics, ctx: &mut Context<Self>) {
        self.write_graphics(g.position, g.sum_with_existing_content, g.image_bytes_line1.as_slice());
        if let Some(v) = g.image_bytes_line2 {
            self.write_graphics(g.position + 5 * 20, g.sum_with_existing_content, v.as_slice());
        }
    }
}


impl Handler<FrontDisplayUpperBar> for I2CRunner {
    type Result = ();

    fn handle(&mut self, ub: FrontDisplayUpperBar, ctx: &mut Context<Self>) {
        let mut cmd = vec![7];
        let c: u32 = ub.content.iter().enumerate().fold(0, |acc, (index, value)| { if *value { acc | 1 << index } else { acc } });
        let c_bytes: [u8; 4] = unsafe { transmute(c.to_le()) };

        cmd.extend(c_bytes.iter());
        self.front_display_device.write(&cmd);
    }
}

impl Handler<FrontDisplayGetButtonState> for I2CRunner {
    type Result = Result<ButtonReport, io::Error>;

    fn handle(&mut self, _: FrontDisplayGetButtonState, _: &mut Context<Self>) -> <Self as Handler<FrontDisplayGetButtonState>>::Result {
        let mut buf: [u8; 2] = [0; 2];
        self.front_display_device.write(&[1]);
        self.front_display_device.read(&mut buf);

        let button = match buf[1] {
            0 => ButtonState::NoChange,
            1 => ButtonState::JustPressed,
            255 => ButtonState::JustReleased,
            _ => panic!("Invalid button state value {}.", buf[1])
        };

        let encoder_value: i32 = unsafe { transmute::<u8, i8>(buf[0]) } as i32;

        let report = ButtonReport { button, encoder_value };

        Ok(report)
    }
}