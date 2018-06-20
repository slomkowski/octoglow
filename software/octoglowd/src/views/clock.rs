use chrono::*;
use error;
use futures::prelude::*;
use i2c;
use views::*;

pub struct ClockView<'a> {
    i2c_interface: &'a i2c::Interface,
}

impl<'a> ClockView<'a> {
    pub fn new<'c>(i2c_interface: &'c i2c::Interface) -> ClockView<'c> {
        ClockView {
            i2c_interface
        }
    }
}

impl<'a> View for ClockView<'a> {
    fn get_preferred_pool_period(&self) -> chrono::Duration {
        chrono::Duration::seconds(1)
    }

    fn update_state(&self) -> Result<bool, error::Error> {
        Ok(false)
    }

    fn draw_on_front_screen(&self, draw_mode: DrawViewOnScreenMode) -> Result<(), error::Error> {
        let now: DateTime<Local> = Local::now();
        let upper_dot = now.second() >= 20;
        let lower_dot = now.second() < 20 || now.second() > 40;

        self.i2c_interface.set_clock_display_content(now.hour() as u8, now.minute() as u8, upper_dot, lower_dot).wait()?;

        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
}