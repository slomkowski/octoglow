use chrono;
use error;

#[derive(Debug, PartialEq)]
pub enum DrawViewOnScreenMode {
    /// Used in the first call of draw_on_front_screen in given cycle.
    First,
    /// Used in each subsequent call
    Update,
}

/// Represents the single screen displayed on the front display.
pub trait View {
    ///  Updates internal state of the view ie. queries the sensors, saves it to database etc. If returned false, state was not updated.
    fn update_state(&self) -> Result<bool, error::Error>;

    /// Should use the state of the view to display it on the front display.
    fn draw_on_front_screen(&self, draw_mode: DrawViewOnScreenMode) -> Result<(), error::Error>;

    fn get_preferred_update_state_period(&self) -> chrono::Duration;

    fn get_preferred_draw_on_front_screen_period(&self) -> Option<chrono::Duration>;
}

pub mod weather_inside;
pub mod weather_outside;
pub mod clock;
