## BIOWIN BIOTERM Sensor - temperature & moisture

Sensor sends data using is OOK modulation at 433.92 MHz.

* start bit ~9 ms
* `1` data bit ~4 ms
* `0` data bit ~2 ms

Each packet consist of:
* start bit
* 36 data bits
* stop bit - valued `0`

| No. | Length | Function                                                                |
|-----|--------|-------------------------------------------------------------------------|
|0-3  |4       |Always value `9`.                                                        |
|4-11 |8       |Random number generated at power-on.                                     |
|12   |1       |`0` - battery OK, `1` - weak battery.                                    |
|13   |1       |`1` - transmission forced by button, `0` - automatic transmission.       |
|14-15|2       |Address selected by switch: `00` - 1, `01` - 2, `10` - 3.                |
|16-27|12      |Temperature in 0.1 deg C. Signed, two's complement.                      |
|28-35|8       |Humidity in %.                                                           |



