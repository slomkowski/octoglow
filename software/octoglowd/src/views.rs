use std::io;
use std::cell::Cell;

use i2c::InsideWeatherSensorReport;
use i2c;
use database;


use futures::prelude::*;

pub enum DrawViewOnScreenMode {
    First,
    Update,
}

trait View {
    fn draw_on_front_screen(&self, i2c_interface: &mut i2c::Interface, draw_mode: DrawViewOnScreenMode);
}

pub struct WeatherInsideView<'a> {
    i2c_interface: &'a i2c::Interface,
    database: &'a database::Database,
    current_report: Cell<Option<InsideWeatherSensorReport>>,
}

impl<'a> WeatherInsideView<'a> {
    pub fn new<'c>(i2c_interface: &'c i2c::Interface, database: &'c database::Database) -> WeatherInsideView<'c> {
        WeatherInsideView {
            i2c_interface,
            database,
            current_report: Cell::new(None),
        }
    }

    pub fn update_state(&'a self) -> impl Future<Item=(), Error=io::Error> + 'a {
        async_block! {
            let current_measurement_fut = self.i2c_interface.get_inside_weather_report();
            let previous_averages_fut = self.database.get_last_inside_weather_reports_by_hour(15);

            self.current_report.set(Some(await!(current_measurement_fut)?));
            Ok(())
        }
    }

    pub fn draw_on_front_screen(&'a self, draw_mode: DrawViewOnScreenMode) -> impl Future<Item=(), Error=io::Error> + 'a {
        let current_report = self.current_report.get();
        async_block! {
            match current_report {
                Some(rep) => {
                    await!(self.i2c_interface.front_display_static_text(20, &WeatherInsideView::format_temperature(rep.temperature))
                        .join(self.i2c_interface.front_display_static_text(0, &WeatherInsideView::format_humidity(rep.humidity)))
                        .join(self.i2c_interface.front_display_static_text(11, &WeatherInsideView::format_pressure(rep.pressure))))?;
                }
                None => {
                    await!(self.i2c_interface.front_display_static_text(20, "---.-\u{00B0}C")
                        .join(self.i2c_interface.front_display_static_text(0, "H:---%"))
                        .join(self.i2c_interface.front_display_static_text(11, "----.-hPa")))?;
                }
            }
            Ok(())
        }
    }

    fn format_temperature(t: f64) -> String {
        format!("{:>+width$.prec$}\u{00B0}C", t, width = 5, prec = 1)
    }

    fn format_humidity(h: f64) -> String {
        format!("H:{value:>align$.prec$}%", align = 3, prec = 0, value = h)
    }

    fn format_pressure(p: f64) -> String {
        format!("{:>6.1}hPa", p)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_format_humidity() {
        assert_eq!("H:100%", WeatherInsideView::format_humidity(100.0));
        assert_eq!("H:  0%", WeatherInsideView::format_humidity(0.0));
        assert_eq!("H: 24%", WeatherInsideView::format_humidity(24.1001));
        assert_eq!("H: 39%", WeatherInsideView::format_humidity(38.8903));
    }

    #[test]
    fn test_format_temperature() {
        assert_eq!("+24.4\u{00B0}C", WeatherInsideView::format_temperature(24.434));
        assert_eq!("-12.0\u{00B0}C", WeatherInsideView::format_temperature(-12.0));
        assert_eq!(" +3.2\u{00B0}C", WeatherInsideView::format_temperature(3.21343));
        assert_eq!("-12.7\u{00B0}C", WeatherInsideView::format_temperature(-12.693423));
        assert_eq!(" +0.0\u{00B0}C", WeatherInsideView::format_temperature(0.0));
    }

    #[test]
    fn test_format_pressure() {
        assert_eq!("1001.2hPa", WeatherInsideView::format_pressure(1001.2133));
        assert_eq!(" 981.4hPa", WeatherInsideView::format_pressure(981.3990));
        assert_eq!("1024.0hPa", WeatherInsideView::format_pressure(1024.02));
    }
}
