CREATE TABLE inside_weather_report (
  id          BIGINT PRIMARY KEY,
  timestamp   DATETIME NOT NULL UNIQUE,
  temperature FLOAT    NOT NULL,
  humidity    FLOAT    NOT NULL,
  pressure    FLOAT    NOT NULL
);

CREATE TABLE outside_weather_report (
  id           BIGINT PRIMARY KEY,
  timestamp    DATETIME NOT NULL UNIQUE,
  temperature  FLOAT    NOT NULL,
  humidity     FLOAT    NOT NULL,
  weak_battery BOOLEAN  NOT NULL
);
