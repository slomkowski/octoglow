use byteorder::{BigEndian, LittleEndian, ReadBytesExt};
use futures::prelude::*;
use i2cdev::core::*;
use i2cdev::linux::LinuxI2CDevice;
use image;
use num_traits;
use SETTINGS;
use std::cmp;
use std::io;
use std::mem::transmute;

const CLOCK_DISPLAY_ADDR: u16 = 0x10;
const GEIGER_ADDR: u16 = 0x12;
const FRONT_DISPLAY_ADDR: u16 = 0x14;
const BME280_ADDR: u16 = 0x76;

const CLOCK_DISPLAY_UPPER_DOT: u8 = 1 << (14 % 8);
const CLOCK_DISPLAY_LOWER_DOT: u8 = 1 << (13 % 8);

#[derive(Debug)]
pub struct InsideWeatherSensorReport {
    pub temperature: f32,
    pub humidity: f32,
    pub pressure: f32,
}

#[derive(Debug)]
pub struct OutsideWeatherSensorReport {
    pub temperature: f32,
    pub humidity: f32,
    pub battery_is_weak: bool,
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

pub struct Interface {
    clock_display_device: LinuxI2CDevice,
    front_display_device: LinuxI2CDevice,
    geiger_device: LinuxI2CDevice,
    bme280_device: LinuxI2CDevice,

    bme280_dig_t1: u16,
    bme280_dig_t2: i16,
    bme280_dig_t3: i16,
    bme280_dig_p1: u16,
    bme280_dig_p2: i16,
    bme280_dig_p3: i16,
    bme280_dig_p4: i16,
    bme280_dig_p5: i16,
    bme280_dig_p6: i16,
    bme280_dig_p7: i16,
    bme280_dig_p8: i16,
    bme280_dig_p9: i16,
    bme280_dig_h1: u8,
    bme280_dig_h2: i16,
    bme280_dig_h3: u8,
    bme280_dig_h4: i16,
    bme280_dig_h5: i16,
    bme280_dig_h6: i8,
}

impl Interface {
    pub fn new() -> Interface {
        let bus_path = SETTINGS.read().unwrap().get_str("i2c.bus-device-file").unwrap();

        let mut bme280_device = LinuxI2CDevice::new(&bus_path, BME280_ADDR).unwrap();

        bme280_device.write(&[0xe0, 0xb6]).unwrap();

        bme280_device.write(&[
            0xf2, 0b101,
            0xf5, 0b10110000,
            0xf4, 0b10110111
        ]).unwrap();

        let mut calibration1: [u8; 25] = [0; 25];
        bme280_device.write(&[0x88]).unwrap();
        bme280_device.read(&mut calibration1).unwrap();

        let mut calibration2: [u8; 8] = [0; 8];
        bme280_device.write(&[0xe1]).unwrap();
        bme280_device.read(&mut calibration2).unwrap();

        {
            let all_zeroes: [u8; 25] = [0; 25];
            let all_ones: [u8; 25] = [0xff; 25];
            assert_ne!(calibration1, all_ones);
            assert_ne!(calibration1, all_zeroes);
            assert_ne!(calibration2, all_ones[0..8]);
            assert_ne!(calibration2, all_zeroes[0..8]);
        }

        Interface {
            clock_display_device: LinuxI2CDevice::new(&bus_path, CLOCK_DISPLAY_ADDR).unwrap(),
            front_display_device: LinuxI2CDevice::new(&bus_path, FRONT_DISPLAY_ADDR).unwrap(),
            geiger_device: LinuxI2CDevice::new(&bus_path, GEIGER_ADDR).unwrap(),
            bme280_device,
            bme280_dig_t1: (&calibration1[0..2]).read_u16::<LittleEndian>().unwrap(),
            bme280_dig_t2: (&calibration1[2..4]).read_i16::<LittleEndian>().unwrap(),
            bme280_dig_t3: (&calibration1[4..6]).read_i16::<LittleEndian>().unwrap(),
            bme280_dig_p1: (&calibration1[6..8]).read_u16::<LittleEndian>().unwrap(),
            bme280_dig_p2: (&calibration1[8..10]).read_i16::<LittleEndian>().unwrap(),
            bme280_dig_p3: (&calibration1[10..12]).read_i16::<LittleEndian>().unwrap(),
            bme280_dig_p4: (&calibration1[12..14]).read_i16::<LittleEndian>().unwrap(),
            bme280_dig_p5: (&calibration1[14..16]).read_i16::<LittleEndian>().unwrap(),
            bme280_dig_p6: (&calibration1[16..18]).read_i16::<LittleEndian>().unwrap(),
            bme280_dig_p7: (&calibration1[18..20]).read_i16::<LittleEndian>().unwrap(),
            bme280_dig_p8: (&calibration1[20..22]).read_i16::<LittleEndian>().unwrap(),
            bme280_dig_p9: (&calibration1[22..24]).read_i16::<LittleEndian>().unwrap(),
            bme280_dig_h1: calibration1[24],
            bme280_dig_h2: (&calibration2[0..2]).read_i16::<LittleEndian>().unwrap(),
            bme280_dig_h3: calibration2[2],
            bme280_dig_h4: (calibration2[3] as i16) * 16 + (calibration2[4] & 0x0F) as i16,
            bme280_dig_h5: (calibration2[5] as i16) * 16 + (calibration2[4] >> 4) as i16,
            bme280_dig_h6: (&calibration2[7..8]).read_i8().unwrap(),
        }
    }

    pub fn set_brightness<'a>(&'a mut self, brightness: u32) -> impl Future<Item=(), Error=io::Error> + 'a {
        assert!(brightness <= 5, "brightness has max value 5, {} provided", brightness);

        async_block! {
            self.clock_display_device.write(&[3, brightness as u8])?;
            self.front_display_device.write(&[3, brightness as u8])?;
            Ok(())
        }
    }

    pub fn set_front_display_upper_bar_content<'a>(&'a mut self, content: &[bool; 20]) -> impl Future<Item=(), Error=io::Error> + 'a {
        let mut cmd = vec![7];
        let c: u32 = content.iter().enumerate().fold(0, |acc, (index, value)| { if *value { acc | 1 << index } else { acc } });
        let c_bytes: [u8; 4] = unsafe { transmute(c.to_le()) };

        cmd.extend(c_bytes.iter());

        async_block! {
            self.front_display_device.write(&cmd)?;
            Ok(())
        }
    }

    pub fn set_front_display_upper_bar_active_positions<'a>(&'a mut self, active_positions: &[u32]) -> impl Future<Item=(), Error=io::Error> + 'a {
        let mut content: [bool; 20] = [false; 20];

        for pos in 0..20 {
            if active_positions.contains(&pos) {
                content[pos as usize] = true;
            }
        }
        self.set_front_display_upper_bar_content(&content)
    }

    pub fn front_display_clear<'a>(&'a mut self) -> impl Future<Item=(), Error=io::Error> + 'a {
        async_block! {
            self.front_display_device.write(&[2])?;
            Ok(())
        }
    }

    pub fn front_display_static_text<'a>(&'a mut self, position: u32, text: &str) -> impl Future<Item=(), Error=io::Error> + 'a {
        let text_length = text.chars().count() as u32;
        let last_position = position + text_length;
        assert!(position < 40, "position has to be between 0 and 39, {} provided", position);
        assert!(text_length > 0, "text length has to be at least 1");
        assert!(last_position < 40, "end of the string cannot exceed position 39, but has length {} and position {}, which sums to {}", text_length, position, last_position);

        let mut cmd = vec![4, position as u8, text_length as u8];
        cmd.extend(text.as_bytes().iter());
        cmd.push(0);

        async_block! {
            self.front_display_device.write(&cmd)?;
            Ok(())
        }
    }

    pub fn front_display_scrolling_text<'a>(&'a mut self, slot: ScrollingTextSlot, position: u32, length: u32, text: &str) -> impl Future<Item=(), Error=io::Error> + 'a {
        let text_length = text.len() as u32;
        let last_position = position + length;
        assert!(slot.capacity() as u32 > text_length, "UTF-8 text length ({} bytes) cannot exceed the capacity of the selected slot {:?}, which is {}", text_length, slot, slot.capacity());
        assert!(position < 40, "position has to be between 0 and 39, {} provided", position);
        assert!(text_length > 0, "text length has to be at least 1");
        assert!(last_position < 40, "end of the string cannot exceed position 39, but has length {} and position {}, which sums to {}", length, position, last_position);

        let mut cmd = vec![5, slot.number(), position as u8, length as u8];
        cmd.extend(text.as_bytes().iter());
        cmd.push(0);

        async_block! {
            self.front_display_device.write(&cmd)?;
            Ok(())
        }
    }

    pub fn front_display_image<'a>(&'a mut self, position: u32, sum_with_existing_content: bool, img: &'a image::GrayImage, invert_colors: bool)
                                   -> impl Future<Item=(), Error=io::Error> + 'a {
        let (line1, line2) = match img.height() {
            7 => { // single line
                assert!(position < 5 * 40, "position cannot exceed {}", 5 * 40 - 1);
                (Interface::image_to_vec(img, 0, invert_colors), None)
            }
            14 => { // two line
                assert!(position < 5 * 20, "position cannot exceed {}", 5 * 20 - 1);
                (Interface::image_to_vec(img, 0, invert_colors), Some(Interface::image_to_vec(img, 7, invert_colors)))
            }
            _ => panic!("image has to have height of 7 or 14")
        };

        async_block! {
            self.write_graphics_low_level(position as u8, sum_with_existing_content, &line1)?;
            if let Some(l2) = line2 {
                self.write_graphics_low_level((5 * 20 + position) as u8, sum_with_existing_content, &l2)?;
            }
            Ok(())
        }
    }

    pub fn front_display_diff_chart_1line<'a, T: num_traits::Num + num_traits::ToPrimitive + Copy>(&'a mut self, position: u32, values: &[T], unit: T)
                                                                                                   -> impl Future<Item=(), Error=io::Error> + 'a {
        // we assume last value as pivot
        const MAX_VALUES: usize = 5 * 20;
        assert!(values.len() < MAX_VALUES, "number of values cannot exceed {}", MAX_VALUES);
        assert!(values.len() > 0, "there has to be at least one value");
        let max_position: u32 = 5 * 40 - values.len() as u32;
        assert!(position < max_position, "position cannot exceed {}", max_position);

        let img_vec: Vec<u8> = values.iter().map(|v| ((*v - *values.last().unwrap()).to_f32().unwrap() / unit.to_f32().unwrap()).round() as i32)
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

        async_block! {
            self.write_graphics_low_level(position as u8, false, &img_vec)?;
            Ok(())
        }
    }

    pub fn front_display_diff_chart_2lines<'a, T: num_traits::Num + num_traits::ToPrimitive + Copy>(&'a mut self, position: u32, values: &[T], unit: T)
                                                                                                    -> impl Future<Item=(), Error=io::Error> + 'a {
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

        async_block! {
            self.write_graphics_low_level(position as u8, false, &upper_vec)?;
            self.write_graphics_low_level((5 * 20 + position) as u8, false, &lower_vec)?;
            Ok(())
        }
    }

    pub fn get_button_report(&mut self) -> Result<ButtonReport, io::Error> {
        let mut buf: [u8; 2] = [0; 2];
        self.front_display_device.write(&[1])?;
        self.front_display_device.read(&mut buf)?;

        let button = match buf[1] {
            0 => ButtonState::NoChange,
            1 => ButtonState::JustPressed,
            255 => ButtonState::JustReleased,
            _ => panic!("Invalid button state value {}.", buf[1])
        };

        let encoder_value: i32 = unsafe { transmute::<u8, i8>(buf[0]) } as i32;

        let report = ButtonReport { button, encoder_value };

        trace!("Received {:?}.", report);

        Ok(report)
    }

    pub fn get_inside_weather_report(&mut self) -> Result<InsideWeatherSensorReport, io::Error> {
        let mut buf: [u8; 8] = [0; 8];
        self.bme280_device.write(&[0xf7])?;
        self.bme280_device.read(&mut buf)?;

        if buf == [128, 0, 0, 128, 0, 0, 128, 0] {
            return Err(io::Error::new(io::ErrorKind::Other, "sensor is not initialized"));
        }

        let adc_p: u32 = (&buf[0..4]).read_u32::<BigEndian>().unwrap() >> 12;
        let adc_t: u32 = (&buf[3..7]).read_u32::<BigEndian>().unwrap() >> 12;
        let adc_h: u32 = (&buf[6..8]).read_u16::<BigEndian>().unwrap() as u32;

        let t_fine = self.bme280_calculate_t_fine(adc_t);

        let report = InsideWeatherSensorReport {
            humidity: self.bme280_calculate_humidity(adc_h, t_fine),
            temperature: self.bme280_calculate_temperature(t_fine),
            pressure: self.bme280_calculate_pressure(adc_p, t_fine),
        };

        debug!("Received {:?}.", report);

        Ok(report)
    }

    pub fn get_outside_weather_report(&mut self) -> Result<OutsideWeatherSensorReport, io::Error> {
        let mut buf: [u8; 5] = [0; 5];
        self.clock_display_device.write(&[0x04])?;
        self.clock_display_device.read(&mut buf)?;

        let report = OutsideWeatherSensorReport {
            temperature: (256.0 * buf[2] as f32 + buf[1] as f32) / 10.0,
            humidity: buf[3] as f32,
            battery_is_weak: (buf[4] == 1),
        };

        debug!("Received {:?}.", report);

        Ok(report)
    }

    pub fn set_clock_display_content<'a>(&'a mut self, hours: u8, minutes: u8, upper_dot: bool, lower_dot: bool) -> impl Future<Item=(), Error=io::Error> + 'a {
        let digits = [hours / 10, hours % 10, minutes / 10, minutes % 10];
        let dots = (if upper_dot { CLOCK_DISPLAY_UPPER_DOT } else { 0 }) | (if lower_dot { CLOCK_DISPLAY_LOWER_DOT } else { 0 });
        let buf: [u8; 6] = [0x1, digits[0] + 0x30, digits[1] + 0x30, digits[2] + 0x30, digits[3] + 0x30, dots];

        async_block! {
            self.clock_display_device.write(&buf)?;
            Ok(())
        }
    }

    /// Calculation is based on sample code from BME280 datasheet.
    fn bme280_calculate_temperature(&self, t_fine: i32) -> f32 {
        const TEMPERATURE_MIN: i32 = -4000;
        const TEMPERATURE_MAX: i32 = 8500;

        let temperature_unbound: i32 = (t_fine * 5 + 128) / 256;

        let temperature = if temperature_unbound < TEMPERATURE_MIN {
            TEMPERATURE_MIN
        } else if temperature_unbound > TEMPERATURE_MAX {
            TEMPERATURE_MAX
        } else {
            temperature_unbound
        };

        return temperature as f32 / 100.0;
    }

    /// Temperature is used in humidity calculation
    fn bme280_calculate_t_fine(&self, t_adc: u32) -> i32 {
        let mut var1: i32;
        let mut var2: i32;

        var1 = (t_adc / 8) as i32 - (self.bme280_dig_t1 as i32 * 2);

        var1 = var1 * (self.bme280_dig_t2 as i32) / 2048;
        var2 = (t_adc / 16) as i32 - (self.bme280_dig_t1 as i32);
        var2 = ((var2 * var2 / 4096) * (self.bme280_dig_t3 as i32)) / 16384;

        var1 + var2
    }

    fn bme280_calculate_humidity(&self, adc_h: u32, t_fine: i32) -> f32 {
        let mut var2: i32;
        let mut var3: i32;
        let mut var4: i32;
        let mut var5: i32;
        let mut humidity: u32;
        const HUMIDITY_MAX: u32 = 102400;

        let var1: i32 = t_fine - (76800);
        var2 = adc_h as i32 * 16384;
        var3 = (self.bme280_dig_h4 as i32) * 1048576;
        var4 = (self.bme280_dig_h5 as i32) * var1;
        var5 = (var2 - var3 - var4 + 16384) / 32768;
        var2 = (var1 * (self.bme280_dig_h6 as i32)) / 1024;
        var3 = (var1 * (self.bme280_dig_h3 as i32)) / 2048;
        var4 = ((var2 * (var3 + 32768)) / 1024) + 2097152;
        var2 = ((var4 * (self.bme280_dig_h2 as i32)) + 8192) / 16384;
        var3 = var5 * var2;
        var4 = ((var3 / 32768) * (var3 / 32768)) / 128;
        var5 = var3 - ((var4 * (self.bme280_dig_h1 as i32)) / 16);
        var5 = if var5 < 0 { 0 } else { var5 };
        var5 = if var5 > 419430400 { 419430400 } else { var5 };
        humidity = (var5 / 4096) as u32;

        if humidity > HUMIDITY_MAX {
            humidity = HUMIDITY_MAX;
        }

        return humidity as f32 / 1024.0;
    }

    fn bme280_calculate_pressure(&self, adc_p: u32, t_fine: i32) -> f32 {
        let mut var1: i64;
        let mut var2: i64;
        let mut var4: i64;
        let mut pressure: u32;
        const PRESSURE_MIN: u32 = 3000000;
        const PRESSURE_MAX: u32 = 11000000;

        var1 = (t_fine as i64) - 128000;
        var2 = var1 * var1 * self.bme280_dig_p6 as i64;
        var2 = var2 + ((var1 * self.bme280_dig_p5 as i64) * 131072);
        var2 = var2 + ((self.bme280_dig_p4 as i64) * 34359738368);
        var1 = ((var1 * var1 * self.bme280_dig_p3 as i64) / 256) + (var1 * self.bme280_dig_p2 as i64 * 4096);
        let var3: i64 = 140737488355328;
        var1 = (var3 + var1) * (self.bme280_dig_p1 as i64) / 8589934592;

        /* To avoid divide by zero exception */
        if var1 != 0 {
            var4 = 1048576 - adc_p as i64;
            var4 = (((var4 * 2147483648) - var2) * 3125) / var1;
            var1 = ((self.bme280_dig_p9 as i64) * (var4 / 8192) * (var4 / 8192)) / 33554432;
            var2 = ((self.bme280_dig_p8 as i64) * var4) / 524288;
            var4 = ((var4 + var1 + var2) / 256) + ((self.bme280_dig_p7 as i64) * 16);
            pressure = (((var4 / 2) * 100) / 128) as u32;

            if pressure < PRESSURE_MIN {
                pressure = PRESSURE_MIN;
            } else if pressure > PRESSURE_MAX {
                pressure = PRESSURE_MAX;
            }
        } else {
            pressure = PRESSURE_MIN;
        }

        return pressure as f32 / 100.0 / 100.0; // result in hPa
    }

    fn write_graphics_low_level(&mut self, position: u8, sum_with_existing_text: bool, content: &[u8]) -> Result<(), io::Error> {
        let mut cmd = vec![6, position, content.len() as u8, sum_with_existing_text as u8];
        cmd.extend(content.iter());
        self.front_display_device.write(&cmd)?;
        Ok(())
    }

    fn image_to_vec(img: &image::GrayImage, offset: u32, invert_colors: bool) -> Vec<u8> {
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
