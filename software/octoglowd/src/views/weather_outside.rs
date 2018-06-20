use chrono::*;
use database;
use error;
use futures::prelude::*;
use i2c;
use i2c::OutsideWeatherSensorReport;
use std::cell::Cell;
use std::cmp::min;
use views::*;

const HISTORIC_VALUES_LENGTH: usize = 15;

#[derive(Debug, Clone, Copy)]
struct CurrentReport {
    current_report: OutsideWeatherSensorReport,
    last_measurement_timestamp: NaiveDateTime,
    historic_temperature: [f64; HISTORIC_VALUES_LENGTH],
    historic_humidity: [f64; HISTORIC_VALUES_LENGTH],
}

pub struct WeatherOutsideView<'a> {
    i2c_interface: &'a i2c::Interface,
    database: &'a database::Database,
    current_report: Cell<Option<CurrentReport>>,
}

impl<'a> WeatherOutsideView<'a> {
    pub fn new<'c>(i2c_interface: &'c i2c::Interface, database: &'c database::Database) -> WeatherOutsideView<'c> {
        WeatherOutsideView {
            i2c_interface,
            database,
            current_report: Cell::new(None),
        }
    }

    fn format_temperature(t: f64) -> String {
        format!("{:>+width$.prec$}\u{00B0}C", t, width = 5, prec = 1)
    }

    fn format_humidity(h: f64) -> String {
        format!("{value:>align$.prec$}%", align = 3, prec = 0, value = h)
    }
}

impl<'a> View for WeatherOutsideView<'a> {
    fn get_preferred_pool_period(&self) -> chrono::Duration {
        chrono::Duration::seconds(10)
    }

    fn update_state(&self) -> Result<bool, error::Error> {
        let current_report = self.i2c_interface.get_outside_weather_report().wait()?;

        if current_report.already_read {
            return Ok(false);
        }

        let save_current_report_fut = self.database.save_outside_weather_report(&current_report);

        let historic_averages = self.database.get_last_outside_weather_reports_by_hour(HISTORIC_VALUES_LENGTH as u32).wait()?;

        let mut historic_temperature: [f64; HISTORIC_VALUES_LENGTH] = [0.0; HISTORIC_VALUES_LENGTH];
        let mut historic_humidity: [f64; HISTORIC_VALUES_LENGTH] = [0.0; HISTORIC_VALUES_LENGTH];

        for i in 0..HISTORIC_VALUES_LENGTH {
            match historic_averages.is_empty() {
                false => {
                    let r = min(i, historic_averages.len() - 1);
                    historic_temperature[i] = historic_averages[r].temperature;
                    historic_humidity[i] = historic_averages[r].humidity;
                }
                true => {
                    historic_temperature[i] = current_report.temperature;
                    historic_humidity[i] = current_report.humidity;
                }
            }
        }

        self.current_report.set(Some(CurrentReport {
            current_report,
            historic_temperature,
            historic_humidity,
            last_measurement_timestamp: Local::now().naive_local(),
        }));

        save_current_report_fut.wait()?;

        Ok(true) // we can always get the measurement
    }

    fn draw_on_front_screen(&self, draw_mode: DrawViewOnScreenMode) -> Result<(), error::Error> {
        let current_report = self.current_report.get();

        if draw_mode == DrawViewOnScreenMode::First {
            self.i2c_interface.front_display_clear()
                .join(self.i2c_interface.front_display_static_text(2, "Weather  outside"))
                .wait()?;
        }

        match current_report {
            Some(rep) => {
                let seconds_elapsed: u32 = Local::now().naive_local().signed_duration_since(rep.last_measurement_timestamp).num_seconds() as u32;
                let active_positions: Vec<u32> = (0..20u32).filter(|v| *v <= seconds_elapsed / 5).collect();

                self.i2c_interface.front_display_static_text(20, &WeatherOutsideView::format_temperature(rep.current_report.temperature))
                    .join(self.i2c_interface.front_display_static_text(32, &WeatherOutsideView::format_humidity(rep.current_report.humidity)))
                    .join(self.i2c_interface.front_display_upper_bar_active_positions(&active_positions, false))
                    .wait()?;

                self.i2c_interface.front_display_diff_chart_1line(5 * 37, &rep.historic_humidity, 1.0)
                    .join(self.i2c_interface.front_display_diff_chart_1line(5 * 28, &rep.historic_temperature, 1.0))
                    .wait()?;
            }
            None => {
                self.i2c_interface.front_display_static_text(20, "---.-\u{00B0}C")
                    .join(self.i2c_interface.front_display_static_text(32, "---%"))
                    .join(self.i2c_interface.front_display_upper_bar_active_positions(&[], true))
                    .wait()?;
            }
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_format_humidity() {
        assert_eq!("H: 100%", WeatherOutsideView::format_humidity(100.0));
        assert_eq!("H:   0%", WeatherOutsideView::format_humidity(0.0));
        assert_eq!("H:  24%", WeatherOutsideView::format_humidity(24.1001));
        assert_eq!("H:  39%", WeatherOutsideView::format_humidity(38.8903));
    }

    #[test]
    fn test_format_temperature() {
        assert_eq!("+24.4\u{00B0}C", WeatherOutsideView::format_temperature(24.434));
        assert_eq!("-12.0\u{00B0}C", WeatherOutsideView::format_temperature(-12.0));
        assert_eq!(" +3.2\u{00B0}C", WeatherOutsideView::format_temperature(3.21343));
        assert_eq!("-12.7\u{00B0}C", WeatherOutsideView::format_temperature(-12.693423));
        assert_eq!(" +0.0\u{00B0}C", WeatherOutsideView::format_temperature(0.0));
    }
}