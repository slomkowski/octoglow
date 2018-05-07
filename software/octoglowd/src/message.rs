use actix::{Actor, Addr, Arbiter, Context, Message, msgs, Syn, System};
use std::fmt;
use std::io;
use std::string;

#[derive(Message)]
pub struct SecondElapsedMessage;

pub struct GetWeatherSensorReport;

impl Message for GetWeatherSensorReport {
    type Result = Result<WeatherSensorReport, io::Error>;
}

pub struct WeatherSensorReport {
    pub temperature: f32,
    pub humidity: u8,
    pub battery_is_weak: bool,
}

impl fmt::Debug for WeatherSensorReport {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(f, "[Temp: {} \u{00B0C}, humidity: {}%, {}]", self.temperature, self.humidity, match self.battery_is_weak {
            true => "batt weak",
            false => "batt OK"
        })
    }
}

#[derive(Message)]
pub struct ClockDisplayContent {
    pub digits: [u8; 4],
    pub upper_dot: bool,
    pub lower_dot: bool,
}

impl ClockDisplayContent {
    pub fn new(hours: u8, minutes: u8, upper_dot: bool, lower_dot: bool) -> ClockDisplayContent {
        ClockDisplayContent {
            digits: [hours / 10, hours % 10, minutes / 10, minutes % 10],
            upper_dot,
            lower_dot,
        }
    }
}

impl fmt::Debug for ClockDisplayContent {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(f, "[{:?}{}{:?}]", &self.digits[0..2], ":", &self.digits[2..4])
    }
}

#[derive(Message)]
pub struct SetBrightness {
    pub brightness: u8
}

impl SetBrightness {
    pub fn new(brightness: u8) -> SetBrightness {
        assert!(brightness <= 5, "brightness has max value 5, {} provided", brightness);
        SetBrightness { brightness }
    }
}

#[derive(Message)]
pub struct FrontDisplayClear;

#[derive(Message)]
pub struct FrontDisplayStaticText {
    pub position: u8,
    pub max_length: u8,
    pub text: String,
}

impl FrontDisplayStaticText {
    pub fn new(position: u32, text: &str) -> FrontDisplayStaticText {
        let text_length = text.chars().count() as u32;
        assert!(position < 40, "position has to be between 0 and 39, {} provided", position);
        assert!(position + text_length < 40, "end of the string cannot exceed position 39");

        FrontDisplayStaticText { position:position as u8, max_length: text_length as u8, text: text.to_string() }
    }
}
