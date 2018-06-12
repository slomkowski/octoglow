use error;

#[derive(Debug, PartialEq)]
pub enum DrawViewOnScreenMode {
    First,
    Update,
}

pub trait View {
    fn update_state(&self) -> Result<(), error::Error>;

    fn draw_on_front_screen(&self, draw_mode: DrawViewOnScreenMode) -> Result<(), error::Error>;
}

pub mod weather_inside;