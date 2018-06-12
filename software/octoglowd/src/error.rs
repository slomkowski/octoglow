use rusqlite;
use std::io;

#[derive(Debug)]
pub enum Error {
    DatabaseError(rusqlite::Error),
    IoError(io::Error),
}

impl From<rusqlite::Error> for Error {
    fn from(error: rusqlite::Error) -> Self {
        Error::DatabaseError(error)
    }
}

impl From<io::Error> for Error {
    fn from(error: io::Error) -> Self {
        Error::IoError(error)
    }
}
