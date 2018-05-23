use actix::prelude::*;
use byteorder::{BigEndian, LittleEndian, ReadBytesExt};
use i2cdev::core::*;
use i2cdev::linux::LinuxI2CDevice;
use message::*;
use SETTINGS;
use std::io;
use std::mem::transmute;

const CLOCK_DISPLAY_ADDR: u16 = 0x10;
const GEIGER_ADDR: u16 = 0x12;
const FRONT_DISPLAY_ADDR: u16 = 0x14;
const BME280_ADDR: u16 = 0x76;

const CLOCK_DISPLAY_UPPER_DOT: u8 = 1 << (14 % 8);
const CLOCK_DISPLAY_LOWER_DOT: u8 = 1 << (13 % 8);

pub struct I2CActor {
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

impl Default for I2CActor {
    fn default() -> I2CActor {
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

        I2CActor {
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
}

impl I2CActor {
    fn write_graphics(&mut self, position: u8, sum_with_existing_text: bool, content: &[u8]) {
        let mut cmd = vec![6, position, content.len() as u8, sum_with_existing_text as u8];
        cmd.extend(content.iter());
        self.front_display_device.write(&cmd).unwrap();
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
}

impl actix::Supervised for I2CActor {}

impl Actor for I2CActor {
    type Context = Context<Self>;
}

impl ArbiterService for I2CActor {}

impl Handler<ClockDisplayContent> for I2CActor {
    type Result = ();

    fn handle(&mut self, dc: ClockDisplayContent, _: &mut Context<Self>) {
        let dots = (if dc.upper_dot { CLOCK_DISPLAY_UPPER_DOT } else { 0 }) | (if dc.lower_dot { CLOCK_DISPLAY_LOWER_DOT } else { 0 });
        let buf: [u8; 6] = [0x1, dc.digits[0] + 0x30, dc.digits[1] + 0x30, dc.digits[2] + 0x30, dc.digits[3] + 0x30, dots];

        self.clock_display_device.write(&buf).unwrap();
    }
}

impl Handler<SetBrightness> for I2CActor {
    type Result = ();

    fn handle(&mut self, br: SetBrightness, _: &mut Context<Self>) {
        self.clock_display_device.write(&[3, br.brightness]).unwrap();
        self.front_display_device.write(&[3, br.brightness]).unwrap();

        // todo make command using saved state
    }
}

impl Handler<ClockDisplayGetWeatherReport> for I2CActor {
    type Result = Result<OutsideWeatherSensorReport, io::Error>;

    fn handle(&mut self, _: ClockDisplayGetWeatherReport, _: &mut Context<Self>) -> Self::Result {
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
}

impl Handler<FrontDisplayClear> for I2CActor {
    type Result = ();

    fn handle(&mut self, _: FrontDisplayClear, _: &mut Context<Self>) {
        self.front_display_device.write(&[2]).unwrap();
    }
}

impl Handler<FrontDisplayStaticText> for I2CActor {
    type Result = ();

    fn handle(&mut self, st: FrontDisplayStaticText, _: &mut Context<Self>) {
        let mut cmd = vec![4, st.position, st.max_length];
        cmd.extend(st.text.as_bytes().iter());
        cmd.push(0);
        self.front_display_device.write(&cmd).unwrap();
    }
}

impl Handler<FrontDisplayScrollingText> for I2CActor {
    type Result = ();

    fn handle(&mut self, st: FrontDisplayScrollingText, _: &mut Context<Self>) {
        let mut cmd = vec![5, st.slot_number, st.position, st.length];
        cmd.extend(st.text.as_bytes().iter());
        cmd.push(0);
        self.front_display_device.write(&cmd).unwrap();
    }
}

impl Handler<FrontDisplayGraphics> for I2CActor {
    type Result = ();

    fn handle(&mut self, g: FrontDisplayGraphics, _: &mut Context<Self>) {
        self.write_graphics(g.position, g.sum_with_existing_content, g.image_bytes_line1.as_slice());
        if let Some(v) = g.image_bytes_line2 {
            self.write_graphics(g.position + 5 * 20, g.sum_with_existing_content, v.as_slice());
        }
    }
}


impl Handler<FrontDisplayUpperBar> for I2CActor {
    type Result = ();

    fn handle(&mut self, ub: FrontDisplayUpperBar, _: &mut Context<Self>) {
        let mut cmd = vec![7];
        let c: u32 = ub.content.iter().enumerate().fold(0, |acc, (index, value)| { if *value { acc | 1 << index } else { acc } });
        let c_bytes: [u8; 4] = unsafe { transmute(c.to_le()) };

        cmd.extend(c_bytes.iter());
        self.front_display_device.write(&cmd).unwrap();
    }
}

impl Handler<FrontDisplayGetButtonState> for I2CActor {
    type Result = Result<ButtonReport, io::Error>;

    fn handle(&mut self, _: FrontDisplayGetButtonState, _: &mut Context<Self>) -> <Self as Handler<FrontDisplayGetButtonState>>::Result {
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
}

impl Handler<GetInsideWeatherReport> for I2CActor {
    type Result = Result<InsideWeatherSensorReport, io::Error>;

    fn handle(&mut self, _: GetInsideWeatherReport, _: &mut Context<Self>) -> Result<InsideWeatherSensorReport, io::Error> {
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
}


#[cfg(test)]
mod tests {
    use actix::prelude::*;
    use actix::{Arbiter, System, msgs};
    use std::time::Duration;
    use std::path::PathBuf;
    use image;
    use super::*;

    struct I2CTestActor;

    impl Actor for I2CTestActor {
        type Context = Context<Self>;

        fn started(&mut self, ctx: &mut <Self as Actor>::Context) {

            // sensor module should be in reset state, read should return Err
            let i2c_addr_i = Arbiter::registry().get::<I2CActor>();

            i2c_addr_i.send(GetInsideWeatherReport).into_actor(self)
                .then(|res, _, _| {
                    match res {
                        Ok(result) => {
                            match result {
                                Err(msg) => assert_eq!("sensor is not initialized", msg.to_string()),
                                _ => panic!("response should be an error since sensor is not initialized yet")
                            }
                        }
                        _ => panic!("error when receiving message")
                    }
                    actix::fut::ok(())
                }).wait(ctx);

            ctx.run_later(Duration::from_secs(1), |_, _| {
                let i2c_addr = Arbiter::registry().get::<I2CActor>();
                i2c_addr.do_send(FrontDisplayClear);
                i2c_addr.do_send(FrontDisplayUpperBar::enabled_positions(&[0, 1, 19]));
                i2c_addr.do_send(FrontDisplayScrollingText::new(ScrollingTextSlot::SLOT0, 34, 5, "The quick brown fox jumps over the lazy dog. Dość gróźb fuzją, klnę, pych i małżeństw!"));
                i2c_addr.do_send(FrontDisplayStaticText::new(1, "Znajdź pchły, wróżko! Film \"Teść\"."));
            });

            ctx.run_later(Duration::from_secs(4), move |act, ctx2| {
                let i2c_addr = Arbiter::registry().get::<I2CActor>();
                i2c_addr.do_send(FrontDisplayClear);
                i2c_addr.do_send(FrontDisplayScrollingText::new(ScrollingTextSlot::SLOT0, 0, 18, "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut eere"));
                i2c_addr.do_send(FrontDisplayScrollingText::new(ScrollingTextSlot::SLOT1, 19, 7, "!@#$%^&*()_+-={}[];:\",<>.?"));
                i2c_addr.do_send(FrontDisplayScrollingText::new(ScrollingTextSlot::SLOT2, 34, 5, "Strząść puch nimfy w łój"));

                i2c_addr.do_send(FrontDisplayUpperBar::new([
                    true, true, false, true, false,
                    false, false, false, false, false,
                    false, false, true, false, false,
                    false, false, false, false, true]));

                i2c_addr.send(GetInsideWeatherReport).into_actor(act)
                    .then(|res, _, _| {
                        match res {
                            Ok(Ok(report)) => {
                                println!("Sensor report: {:?}", report);
                                assert!(report.temperature > 10.0);
                                assert!(report.temperature < 50.0);
                                assert!(report.humidity > 5.0);
                                assert!(report.humidity < 95.0);
                                assert!(report.pressure > 950.0);
                                assert!(report.pressure < 1070.0);
                            }
                            _ => panic!("error when receiving message or not Ok")
                        }
                        actix::fut::ok(())
                    }).wait(ctx2);
            });

            ctx.run_later(Duration::from_secs(8), |_, _| {
                let test_data_dir = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("resources/test");

                let i2c_addr = Arbiter::registry().get::<I2CActor>();
                i2c_addr.do_send(FrontDisplayClear);

                i2c_addr.do_send(FrontDisplayStaticText::new(0, "Foo bar text."));

                let img14 = image::open(test_data_dir.join("img14.png")).unwrap();
                i2c_addr.do_send(FrontDisplayGraphics::new(5 * 1, true, img14.grayscale().as_luma8().unwrap(), true));

                let img7 = image::open(test_data_dir.join("img7.png")).unwrap();
                i2c_addr.do_send(FrontDisplayGraphics::new(5 * 28, false, img7.grayscale().as_luma8().unwrap(), false));

                i2c_addr.do_send(FrontDisplayGraphics::from_diff_chart_1line(5 * 23, &[0, 1, 2, 3, 4, 5, 6, 7, 6, 5, 4], 1));

                i2c_addr.do_send(FrontDisplayGraphics::from_diff_chart_1line(5 * 26, &[16.0, 21.0, 84.3, 152.0, 79.6, 61.3, 68.2], 31.5));

                i2c_addr.do_send(FrontDisplayGraphics::from_diff_chart_2lines(5 * 13, &[0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 26, 24, 22, 20, 18, 16, 14, 14], 2));
            });

            ctx.run_later(Duration::from_secs(10), |_, _| {
                let i2c_addr = Arbiter::registry().get::<I2CActor>();
                i2c_addr.do_send(FrontDisplayClear);
            });

            ctx.run_later(Duration::from_secs(11), |_, _| {
                Arbiter::system().do_send(msgs::SystemExit(0));
            });
        }
    }

    #[test]
    fn test_all_i2c_commands() {
        let system = System::new("octoglowd");

        let _: Addr<Unsync, _> = I2CTestActor.start();

        system.run();
    }
}