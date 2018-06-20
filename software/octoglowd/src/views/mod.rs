use chrono;
use error;

#[derive(Debug, PartialEq)]
pub enum DrawViewOnScreenMode {
    First,
    Update,
}

pub trait View {
    fn get_preferred_pool_period(&self) -> chrono::Duration;

    fn update_state(&self) -> Result<bool, error::Error>;

    fn draw_on_front_screen(&self, draw_mode: DrawViewOnScreenMode) -> Result<(), error::Error>;
}

pub mod weather_inside;
pub mod weather_outside;
pub mod clock;
