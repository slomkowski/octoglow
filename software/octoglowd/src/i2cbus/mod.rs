use std::io;

pub trait Device {
    /// Read data from the device to fill the provided slice
    fn read(&self, data: &mut [u8]) -> Result<(), io::Error>;

    /// Write the provided buffer to the device
    fn write(&self, data: &[u8]) -> Result<(), io::Error>;
}

pub trait Bus {
    fn create_device(&self, address: u8) -> Result<Box<Device>, io::Error>;
}

pub mod buspirate;
pub mod system;