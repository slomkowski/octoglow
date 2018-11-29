use chrono;
use database;
use error;
use futures::prelude::*;
use i2c;
use i2c::InsideWeatherSensorReport;
use std::cell::Cell;
use std::cmp::min;
use views::*;

const HISTORIC_VALUES_LENGTH: usize = 15;

#[derive(Debug, Clone, Copy)]
struct CurrentReport {
    current_report: InsideWeatherSensorReport,
    historic_temperature: [f64; HISTORIC_VALUES_LENGTH],
    historic_humidity: [f64; HISTORIC_VALUES_LENGTH],
    historic_pressure: [f64; HISTORIC_VALUES_LENGTH],
}

pub struct WeatherInsideView<'a> {
    i2c_interface: &'a i2c::Interface,
    database: &'a database::Database,
    current_report: Cell<Option<CurrentReport>>,
}

impl<'a> WeatherInsideView<'a> {
    pub fn new<'c>(i2c_interface: &'c i2c::Interface, database: &'c database::Database) -> WeatherInsideView<'c> {
        WeatherInsideView {
            i2c_interface,
            database,
            current_report: Cell::new(None),
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

impl<'a> View for WeatherInsideView<'a> {
    fn update_state(&self) -> Result<bool, error::Error> {
        let current_measurement_fut = self.i2c_interface.get_inside_weather_report();

        let current_report = current_measurement_fut.wait()?;
        let save_current_report_fut = self.database.save_inside_weather_report(&current_report);

        let historic_averages = self.database.get_last_inside_weather_reports_by_hour(HISTORIC_VALUES_LENGTH as u32).wait()?;

        let mut historic_temperature: [f64; HISTORIC_VALUES_LENGTH] = [0.0; HISTORIC_VALUES_LENGTH];
        let mut historic_humidity: [f64; HISTORIC_VALUES_LENGTH] = [0.0; HISTORIC_VALUES_LENGTH];
        let mut historic_pressure: [f64; HISTORIC_VALUES_LENGTH] = [0.0; HISTORIC_VALUES_LENGTH];

        for i in 0..HISTORIC_VALUES_LENGTH {
            match historic_averages.is_empty() {
                false => {
                    let r = min(i, historic_averages.len() - 1);
                    historic_temperature[i] = historic_averages[r].temperature;
                    historic_humidity[i] = historic_averages[r].humidity;
                    historic_pressure[i] = historic_averages[r].pressure;
                }
                true => {
                    historic_temperature[i] = current_report.temperature;
                    historic_humidity[i] = current_report.humidity;
                    historic_pressure[i] = current_report.pressure;
                }
            }
        }

        self.current_report.set(Some(CurrentReport {
            current_report,
            historic_temperature,
            historic_humidity,
            historic_pressure,
        }));

        save_current_report_fut.wait()?;

        Ok(true) // we can always get the measurement
    }

    fn draw_on_front_screen(&self, _: DrawViewOnScreenMode) -> Result<(), error::Error> {
        let current_report = self.current_report.get();

        self.i2c_interface.front_display_clear()
            .join(self.i2c_interface.front_display_static_text(38, "IN"))
            .wait()?;

        match current_report {
            Some(rep) => {
                self.i2c_interface.front_display_static_text(20, &WeatherInsideView::format_temperature(rep.current_report.temperature))
                    .join(self.i2c_interface.front_display_static_text(0, &WeatherInsideView::format_humidity(rep.current_report.humidity)))
                    .join(self.i2c_interface.front_display_static_text(11, &WeatherInsideView::format_pressure(rep.current_report.pressure)))
                    .wait()?;

                self.i2c_interface.front_display_diff_chart_1line(5 * 7, &rep.historic_humidity, 1.0)
                    .join(self.i2c_interface.front_display_diff_chart_1line(5 * 28, &rep.historic_temperature, 1.0))
                    .join(self.i2c_interface.front_display_diff_chart_1line(5 * 33, &rep.historic_pressure, 1.0))
                    .wait()?;
            }
            None => {
                self.i2c_interface.front_display_static_text(20, "---.-\u{00B0}C")
                    .join(self.i2c_interface.front_display_static_text(0, "H:---%"))
                    .join(self.i2c_interface.front_display_static_text(11, "----.-hPa"))
                    .wait()?;
            }
        }
        Ok(())
    }

    fn get_preferred_update_state_period(&self) -> chrono::Duration {
        chrono::Duration::minutes(1)
    }

    fn get_preferred_draw_on_front_screen_period(&self) -> Option<chrono::Duration> {
        None
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