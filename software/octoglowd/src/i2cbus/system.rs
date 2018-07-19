use i2cbus::{Bus, Device};
use i2cdev::core::*;
use i2cdev::linux::LinuxI2CDevice;
use std::cell::RefCell;
use std::io;

pub struct SystemI2CBus {
    bus_device_file: String
}

impl SystemI2CBus {
    pub fn new(bus_device_file: &str) -> Result<SystemI2CBus, io::Error> {
        Ok(SystemI2CBus { bus_device_file: String::from(bus_device_file) })
    }
}

impl Bus for SystemI2CBus {
    fn create_device(&self, address: u8) -> Result<Box<Device>, io::Error> {
        assert!(address <= 127);
        let dev = LinuxI2CDevice::new(&self.bus_device_file, address as u16)?;

        Ok(Box::new(SystemI2CDevice {
            device: RefCell::new(dev)
        }))
    }
}

pub struct SystemI2CDevice {
    device: RefCell<LinuxI2CDevice>
}

impl Device for SystemI2CDevice {
    fn read(&self, data: &mut [u8]) -> Result<(), io::Error> {
        match self.device.borrow_mut().read(data) {
            Ok(_) => Ok(()),
            Err(err) => Err(io::Error::new(io::ErrorKind::Other, err.to_string()))
        }
    }

    fn write(&self, data: &[u8]) -> Result<(), io::Error> {
        match self.device.borrow_mut().write(data) {
            Ok(_) => Ok(()),
            Err(err) => Err(io::Error::new(io::ErrorKind::Other, err.to_string()))
        }
    }
}
