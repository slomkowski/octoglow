extern crate image;

use actix::prelude::*;
use image::*;
use num_traits;
use std::cmp;
use std::fmt;
use std::io;
use std::time;

#[derive(Debug, PartialEq, Eq, Hash, Copy, Clone)]
pub enum DrawViewOnScreenType {
    First,
    Update,
}

#[derive(Message)]
pub struct DrawViewOnScreen(pub DrawViewOnScreenType);

#[derive(Debug, PartialEq, Eq, Hash, Copy, Clone)]
pub enum TimerMessageType {
    UserInteractionPool,
    EverySecond,
    EveryMinute,
}

impl TimerMessageType {
    pub fn duration(&self) -> time::Duration {
        match *self {
            TimerMessageType::UserInteractionPool => time::Duration::from_millis(20),
            TimerMessageType::EverySecond => time::Duration::from_secs(1),
            TimerMessageType::EveryMinute => time::Duration::from_secs(60),
        }
    }
}

#[derive(Message)]
pub struct TimerMessage(pub TimerMessageType);

#[derive(Message)]
pub struct SubscribeForTimerMessage {
    pub msg_type: TimerMessageType,
    pub recipient: Recipient<Unsync, TimerMessage>,
}

pub struct ClockDisplayGetWeatherReport;

impl Message for ClockDisplayGetWeatherReport {
    type Result = Result<OutsideWeatherSensorReport, io::Error>;
}

#[derive(Message, Debug, Clone)]
pub struct OutsideWeatherSensorReport {
    pub temperature: f32,
    pub humidity: f32,
    pub battery_is_weak: bool,
}

pub struct FrontDisplayGetButtonState;

impl Message for FrontDisplayGetButtonState {
    type Result = Result<ButtonReport, io::Error>;
}

#[derive(Message, Debug, Clone)]
pub struct InsideWeatherSensorReport {
    pub temperature: f32,
    pub humidity: f32,
    pub pressure: f32,
}

pub struct GetInsideWeatherReport;

impl Message for GetInsideWeatherReport {
    type Result = Result<InsideWeatherSensorReport, io::Error>;
}

#[derive(Debug)]
pub enum ButtonState {
    NoChange,
    JustPressed,
    JustReleased,
}

#[derive(Debug)]
pub struct ButtonReport {
    pub button: ButtonState,
    pub encoder_value: i32,
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
        let last_position = position + text_length;
        assert!(position < 40, "position has to be between 0 and 39, {} provided", position);
        assert!(text_length > 0, "text length has to be at least 1");
        assert!(last_position < 40, "end of the string cannot exceed position 39, but has length {} and position {}, which sums to {}", text_length, position, last_position);

        FrontDisplayStaticText { position: position as u8, max_length: text_length as u8, text: text.to_string() }
    }
}

#[derive(Debug)]
pub enum ScrollingTextSlot {
    SLOT0,
    SLOT1,
    SLOT2,
}

impl ScrollingTextSlot {
    fn number(&self) -> u8 {
        match *self {
            ScrollingTextSlot::SLOT0 => 0,
            ScrollingTextSlot::SLOT1 => 1,
            ScrollingTextSlot::SLOT2 => 2
        }
    }

    fn capacity(&self) -> u8 {
        match *self {
            ScrollingTextSlot::SLOT0 => 150,
            ScrollingTextSlot::SLOT1 => 70,
            ScrollingTextSlot::SLOT2 => 30
        }
    }
}

#[derive(Message)]
pub struct FrontDisplayScrollingText {
    pub slot_number: u8,
    pub position: u8,
    pub length: u8,
    pub text: String,
}

impl FrontDisplayScrollingText {
    pub fn new(slot: ScrollingTextSlot, position: u32, length: u32, text: &str) -> FrontDisplayScrollingText {
        let text_length = text.len() as u32;
        let last_position = position + length;
        assert!(slot.capacity() as u32 > text_length, "UTF-8 text length ({} bytes) cannot exceed the capacity of the selected slot {:?}, which is {}", text_length, slot, slot.capacity());
        assert!(position < 40, "position has to be between 0 and 39, {} provided", position);
        assert!(text_length > 0, "text length has to be at least 1");
        assert!(last_position < 40, "end of the string cannot exceed position 39, but has length {} and position {}, which sums to {}", length, position, last_position);

        FrontDisplayScrollingText { slot_number: slot.number(), position: position as u8, length: length as u8, text: text.to_string() }
    }
}

#[derive(Message)]
pub struct FrontDisplayGraphics {
    pub position: u8,
    pub sum_with_existing_content: bool,
    pub image_bytes_line1: Vec<u8>,
    pub image_bytes_line2: Option<Vec<u8>>,
}

impl FrontDisplayGraphics {
    pub fn new(position: u32, sum_with_existing_content: bool, img: &GrayImage, invert_colors: bool) -> FrontDisplayGraphics {
        match img.height() {
            7 => { // single line
                assert!(position < 5 * 40, "position cannot exceed {}", 5 * 40 - 1);
                FrontDisplayGraphics {
                    position: position as u8,
                    sum_with_existing_content,
                    image_bytes_line1: FrontDisplayGraphics::image_to_vec(img, 0, invert_colors),
                    image_bytes_line2: None,
                }
            }
            14 => { // two line
                assert!(position < 5 * 20, "position cannot exceed {}", 5 * 20 - 1);
                FrontDisplayGraphics {
                    position: position as u8,
                    sum_with_existing_content,
                    image_bytes_line1: FrontDisplayGraphics::image_to_vec(img, 0, invert_colors),
                    image_bytes_line2: Some(FrontDisplayGraphics::image_to_vec(img, 7, invert_colors)),
                }
            }
            _ => panic!("image has to have height of 7 or 14")
        }
    }

    pub fn from_diff_chart_1line<T: num_traits::Num + num_traits::ToPrimitive + Copy>(position: u32, values: &[T], unit: T) -> FrontDisplayGraphics {
        // we assume last value as pivot
        const MAX_VALUES: usize = 5 * 20;
        assert!(values.len() < MAX_VALUES, "number of values cannot exceed {}", MAX_VALUES);
        assert!(values.len() > 0, "there has to be at least one value");
        let max_position: u32 = 5 * 40 - values.len() as u32;
        assert!(position < max_position, "position cannot exceed {}", max_position);

        let img_vec = values.into_iter()
            .map(|v| ((*v - *values.last().unwrap()).to_f32().unwrap() / unit.to_f32().unwrap()).round() as i32)
            .map(|v| cmp::min(3, cmp::max(-3, v)))
            .map(|v| match v {
                -3 => 0b1111000,
                -2 => 0b0111000,
                -1 => 0b0011000,
                0 => 0b0001000,
                1 => 0b1100,
                2 => 0b1110,
                3 => 0b1111,
                _ => panic!("value outside range")
            })
            .collect();

        FrontDisplayGraphics {
            position: position as u8,
            sum_with_existing_content: false,
            image_bytes_line1: img_vec,
            image_bytes_line2: None,
        }
    }

    pub fn from_diff_chart_2lines<T: num_traits::Num + num_traits::ToPrimitive + Copy>(position: u32, values: &[T], unit: T) -> FrontDisplayGraphics {
        // we assume last value as pivot
        const MAX_VALUES: usize = 5 * 20;
        assert!(values.len() < MAX_VALUES, "number of values cannot exceed {}", MAX_VALUES);
        assert!(values.len() > 0, "there has to be at least one value");
        let max_position: u32 = 5 * 20 - values.len() as u32;
        assert!(position < max_position, "position cannot exceed {}", max_position);

        let (current, past_vals) = values.split_last().unwrap();

        let (mut upper_vec, mut lower_vec): (Vec<u8>, Vec<u8>) = past_vals.into_iter()
            .map(|v| ((*v - *current).to_f32().unwrap() / unit.to_f32().unwrap()).round() as i32)
            .map(|v| if v == 0 { (0, 0) } else if v > 0 { (cmp::min(7, v), 0) } else { (0, cmp::max(-7, v)) })
            .map(|(upper_v, lower_v)| {
                let upper_c: u8 = (0..upper_v).fold(0, |column_byte, y| (column_byte | (0b1000000 >> y)));
                let lower_c: u8 = (0..(-lower_v)).fold(0, |column_byte, y| (column_byte | (1 << y)));
                (upper_c, lower_c)
            })
            .unzip();

        lower_vec.push(0b1);
        upper_vec.push(0b1000000);

        FrontDisplayGraphics {
            position: position as u8,
            sum_with_existing_content: false,
            image_bytes_line1: upper_vec,
            image_bytes_line2: Some(lower_vec),
        }
    }

    fn image_to_vec(img: &GrayImage, offset: u32, invert_colors: bool) -> Vec<u8> {
        let mut columns: Vec<u8> = Vec::new();
        for x in 0..(img.width()) {
            let c: u8 = (0..7).fold(0, |column_byte, y| {
                column_byte | (if img.get_pixel(x, y + offset).data[0] > 0 { 1 } else { 0 } << y)
            });
            columns.push(match invert_colors {
                true => !c,
                _ => c
            })
        }
        columns
    }
}

#[derive(Message)]
pub struct FrontDisplayUpperBar {
    pub content: [bool; 20]
}

impl FrontDisplayUpperBar {
    pub fn new(content: [bool; 20]) -> FrontDisplayUpperBar {
        FrontDisplayUpperBar { content }
    }

    pub fn enabled_positions(enabled_positions: &[i32]) -> FrontDisplayUpperBar {
        let mut content: [bool; 20] = [false; 20];

        for pos in 0..20 {
            if enabled_positions.contains(&pos) {
                content[pos as usize] = true;
            }
        }

        FrontDisplayUpperBar { content }
    }
}
