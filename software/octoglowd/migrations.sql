-- this file is embedded within the executable and executed at each application start

CREATE TABLE IF NOT EXISTS inside_weather_report (
  id          INTEGER  NOT NULL PRIMARY KEY,
  timestamp   DATETIME NOT NULL UNIQUE,
  temperature FLOAT    NOT NULL,
  humidity    FLOAT    NOT NULL,
  pressure    FLOAT    NOT NULL
);

CREATE TABLE IF NOT EXISTS outside_weather_report (
  id           INTEGER  NOT NULL PRIMARY KEY,
  timestamp    DATETIME NOT NULL UNIQUE,
  temperature  FLOAT    NOT NULL,
  humidity     FLOAT    NOT NULL,
  weak_battery BOOLEAN  NOT NULL
);
