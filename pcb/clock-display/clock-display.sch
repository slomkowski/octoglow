EESchema Schematic File Version 4
LIBS:clock-display-cache
EELAYER 26 0
EELAYER END
$Descr A4 11693 8268
encoding utf-8
Sheet 1 1
Title "VFD secondary display module"
Date "2017-01-12"
Rev "1"
Comp ""
Comment1 ""
Comment2 ""
Comment3 "433 MHz receiver module and 1WIRE bus conn."
Comment4 "Board contains 4-digit VFD display, two relays with drivers,"
$EndDescr
$Comp
L futaba-vfd:5-LT-02M SV1
U 1 1 584EE278
P 10650 2850
F 0 "SV1" H 10250 4700 60  0000 C CNN
F 1 "5-LT-02M" H 10400 900 60  0000 C CNN
F 2 "futaba-vfd:futaba-vfd-5-LT-02" H 11850 3950 60  0001 C CNN
F 3 "" H 11850 3950 60  0001 C CNN
	1    10650 2850
	1    0    0    -1  
$EndComp
$Comp
L TD62C950RF:TD62C950RF IC2
U 1 1 584EE2F9
P 7150 2900
F 0 "IC2" H 7050 4900 45  0000 L BNN
F 1 "TD62C950RF" H 7050 500 45  0000 L BNN
F 2 "TD62C950RF:TD62C950RF-SSOP60-P-0.65A" H 7180 3050 20  0001 C CNN
F 3 "" H 7150 2900 60  0001 C CNN
	1    7150 2900
	1    0    0    -1  
$EndComp
$Comp
L power:+5V #PWR01
U 1 1 584F33ED
P 6100 4550
F 0 "#PWR01" H 6100 4400 50  0001 C CNN
F 1 "+5V" H 6100 4690 50  0000 C CNN
F 2 "" H 6100 4550 50  0000 C CNN
F 3 "" H 6100 4550 50  0000 C CNN
	1    6100 4550
	1    0    0    -1  
$EndComp
$Comp
L power:GND #PWR02
U 1 1 584F3413
P 9950 6300
F 0 "#PWR02" H 9950 6050 50  0001 C CNN
F 1 "GND" H 9950 6150 50  0000 C CNN
F 2 "" H 9950 6300 50  0000 C CNN
F 3 "" H 9950 6300 50  0000 C CNN
	1    9950 6300
	1    0    0    -1  
$EndComp
$Comp
L power:GND #PWR03
U 1 1 584F356F
P 6800 4200
F 0 "#PWR03" H 6800 3950 50  0001 C CNN
F 1 "GND" H 6800 4050 50  0000 C CNN
F 2 "" H 6800 4200 50  0000 C CNN
F 3 "" H 6800 4200 50  0000 C CNN
	1    6800 4200
	1    0    0    -1  
$EndComp
$Comp
L clock-display-rescue:C_Small C4
U 1 1 584F35CE
P 6100 5100
F 0 "C4" H 6110 5170 50  0000 L CNN
F 1 "100n" H 6110 5020 50  0000 L CNN
F 2 "Capacitors_SMD:C_1206_HandSoldering" H 6100 5100 50  0001 C CNN
F 3 "" H 6100 5100 50  0000 C CNN
	1    6100 5100
	1    0    0    -1  
$EndComp
$Comp
L clock-display-rescue:C_Small C5
U 1 1 584F3761
P 6450 5100
F 0 "C5" H 6460 5170 50  0000 L CNN
F 1 "100n" H 6460 5020 50  0000 L CNN
F 2 "Capacitors_SMD:C_1206_HandSoldering" H 6450 5100 50  0001 C CNN
F 3 "" H 6450 5100 50  0000 C CNN
	1    6450 5100
	1    0    0    -1  
$EndComp
$Comp
L power:+36V #PWR04
U 1 1 584F379B
P 6450 4550
F 0 "#PWR04" H 6450 4400 50  0001 C CNN
F 1 "+36V" H 6450 4690 50  0000 C CNN
F 2 "" H 6450 4550 50  0000 C CNN
F 3 "" H 6450 4550 50  0000 C CNN
	1    6450 4550
	1    0    0    -1  
$EndComp
$Comp
L power:GND #PWR05
U 1 1 584F38E6
P 6450 5350
F 0 "#PWR05" H 6450 5100 50  0001 C CNN
F 1 "GND" H 6450 5200 50  0000 C CNN
F 2 "" H 6450 5350 50  0000 C CNN
F 3 "" H 6450 5350 50  0000 C CNN
	1    6450 5350
	1    0    0    -1  
$EndComp
$Comp
L power:GND #PWR06
U 1 1 584F391A
P 6100 5350
F 0 "#PWR06" H 6100 5100 50  0001 C CNN
F 1 "GND" H 6100 5200 50  0000 C CNN
F 2 "" H 6100 5350 50  0000 C CNN
F 3 "" H 6100 5350 50  0000 C CNN
	1    6100 5350
	1    0    0    -1  
$EndComp
Entry Wire Line
	9250 1100 9350 1200
Entry Wire Line
	9250 1200 9350 1300
Entry Wire Line
	9250 1300 9350 1400
Entry Wire Line
	9250 1400 9350 1500
Entry Wire Line
	9250 1500 9350 1600
Entry Wire Line
	9250 1650 9350 1750
Entry Wire Line
	9250 1750 9350 1850
Entry Wire Line
	9250 1850 9350 1950
Entry Wire Line
	9250 1950 9350 2050
Entry Wire Line
	9250 2050 9350 2150
Entry Wire Line
	9250 2150 9350 2250
Entry Wire Line
	9250 2250 9350 2350
Entry Wire Line
	9250 2400 9350 2500
Entry Wire Line
	9250 2500 9350 2600
Entry Wire Line
	9250 2600 9350 2700
Entry Wire Line
	9250 2700 9350 2800
Entry Wire Line
	9250 2800 9350 2900
Entry Wire Line
	9250 2900 9350 3000
Entry Wire Line
	9250 3050 9350 3150
Entry Wire Line
	9250 3150 9350 3250
Entry Wire Line
	9250 3250 9350 3350
Entry Wire Line
	9250 3350 9350 3450
Entry Wire Line
	9250 3450 9350 3550
Entry Wire Line
	9250 3550 9350 3650
Entry Wire Line
	9250 3650 9350 3750
Entry Wire Line
	9250 3800 9350 3900
Entry Wire Line
	9250 3900 9350 4000
Entry Wire Line
	9250 4000 9350 4100
Entry Wire Line
	9250 4100 9350 4200
Entry Wire Line
	9250 4250 9350 4350
Text Label 9400 1200 0    60   ~ 0
1segAED
Text Label 9400 1300 0    60   ~ 0
1segB
Text Label 9400 1400 0    60   ~ 0
1segC
Text Label 9400 1500 0    60   ~ 0
1segF
Text Label 9400 1600 0    60   ~ 0
1segG
Text Label 9400 1750 0    60   ~ 0
2segA
Text Label 9400 1850 0    60   ~ 0
2segB
Text Label 9400 1950 0    60   ~ 0
2segC
Text Label 9400 2050 0    60   ~ 0
2segD
Text Label 9400 2150 0    60   ~ 0
2segE
Text Label 9400 2250 0    60   ~ 0
2segF
Text Label 9400 2350 0    60   ~ 0
2segG
Text Label 9400 2500 0    60   ~ 0
3segAD
Text Label 9400 2600 0    60   ~ 0
3segB
Text Label 9400 2700 0    60   ~ 0
3segC
Text Label 9400 2800 0    60   ~ 0
3segE
Text Label 9400 2900 0    60   ~ 0
3segF
Text Label 9400 3000 0    60   ~ 0
3segG
Text Label 9400 3150 0    60   ~ 0
4segA
Text Label 9400 3250 0    60   ~ 0
4segB
Text Label 9400 3350 0    60   ~ 0
4segC
Text Label 9400 3450 0    60   ~ 0
4segD
Text Label 9400 3550 0    60   ~ 0
4segE
Text Label 9400 3650 0    60   ~ 0
4segF
Text Label 9400 3750 0    60   ~ 0
4segG
Text Label 9400 3900 0    60   ~ 0
dotUP
Text Label 9400 4000 0    60   ~ 0
dotDOWN
Text Label 9400 4100 0    60   ~ 0
dotAM
Text Label 9400 4200 0    60   ~ 0
dotPM
Text Label 9400 4350 0    60   ~ 0
GRID
$Comp
L clock-display-rescue:ATTINY26-S IC1
U 1 1 58507F6D
P 2550 1750
F 0 "IC1" H 1950 2700 50  0000 C CNN
F 1 "ATTINY26-S" H 3000 800 50  0000 C CNN
F 2 "soic:SOIC20" H 2550 1750 50  0000 C CIN
F 3 "" H 2550 1750 50  0000 C CNN
	1    2550 1750
	1    0    0    -1  
$EndComp
$Comp
L clock-display-rescue:R R8
U 1 1 5856DEA5
P 9550 4850
F 0 "R8" V 9630 4850 50  0000 C CNN
F 1 "33" V 9550 4850 50  0000 C CNN
F 2 "Resistors_ThroughHole:Resistor_Horizontal_RM15mm" V 9480 4850 50  0001 C CNN
F 3 "" H 9550 4850 50  0000 C CNN
	1    9550 4850
	1    0    0    -1  
$EndComp
$Comp
L clock-display-rescue:R R10
U 1 1 5856DF59
P 9800 4850
F 0 "R10" V 9880 4850 50  0000 C CNN
F 1 "33" V 9800 4850 50  0000 C CNN
F 2 "Resistors_ThroughHole:Resistor_Horizontal_RM15mm" V 9730 4850 50  0001 C CNN
F 3 "" H 9800 4850 50  0000 C CNN
	1    9800 4850
	1    0    0    -1  
$EndComp
$Comp
L power:GND #PWR07
U 1 1 5856E1DC
P 1650 2650
F 0 "#PWR07" H 1650 2400 50  0001 C CNN
F 1 "GND" H 1650 2500 50  0000 C CNN
F 2 "" H 1650 2650 50  0000 C CNN
F 3 "" H 1650 2650 50  0000 C CNN
	1    1650 2650
	1    0    0    -1  
$EndComp
$Comp
L clock-display-rescue:C_Small C3
U 1 1 5856E355
P 1500 1450
F 0 "C3" H 1510 1520 50  0000 L CNN
F 1 "100n" H 1510 1370 50  0000 L CNN
F 2 "Capacitors_SMD:C_1206_HandSoldering" H 1500 1450 50  0001 C CNN
F 3 "" H 1500 1450 50  0000 C CNN
	1    1500 1450
	1    0    0    -1  
$EndComp
$Comp
L clock-display-rescue:C_Small C2
U 1 1 5856E48F
P 1200 1450
F 0 "C2" H 1210 1520 50  0000 L CNN
F 1 "100n" H 1210 1370 50  0000 L CNN
F 2 "Capacitors_SMD:C_1206_HandSoldering" H 1200 1450 50  0001 C CNN
F 3 "" H 1200 1450 50  0000 C CNN
	1    1200 1450
	1    0    0    -1  
$EndComp
$Comp
L clock-display-rescue:CP C1
U 1 1 5856E4F8
P 900 1450
F 0 "C1" H 925 1550 50  0000 L CNN
F 1 "47u" H 925 1350 50  0000 L CNN
F 2 "Capacitors_SMD:c_elec_8x10" H 938 1300 50  0001 C CNN
F 3 "" H 900 1450 50  0000 C CNN
	1    900  1450
	1    0    0    -1  
$EndComp
$Comp
L power:+5V #PWR08
U 1 1 5856E7E9
P 900 800
F 0 "#PWR08" H 900 650 50  0001 C CNN
F 1 "+5V" H 900 940 50  0000 C CNN
F 2 "" H 900 800 50  0000 C CNN
F 3 "" H 900 800 50  0000 C CNN
	1    900  800 
	1    0    0    -1  
$EndComp
$Comp
L power:GND #PWR09
U 1 1 5856E97F
P 900 1700
F 0 "#PWR09" H 900 1450 50  0001 C CNN
F 1 "GND" H 900 1550 50  0000 C CNN
F 2 "" H 900 1700 50  0000 C CNN
F 3 "" H 900 1700 50  0000 C CNN
	1    900  1700
	1    0    0    -1  
$EndComp
$Comp
L power:GND #PWR010
U 1 1 5856EA2A
P 1200 1700
F 0 "#PWR010" H 1200 1450 50  0001 C CNN
F 1 "GND" H 1200 1550 50  0000 C CNN
F 2 "" H 1200 1700 50  0000 C CNN
F 3 "" H 1200 1700 50  0000 C CNN
	1    1200 1700
	1    0    0    -1  
$EndComp
$Comp
L power:GND #PWR011
U 1 1 5856EA53
P 1500 1700
F 0 "#PWR011" H 1500 1450 50  0001 C CNN
F 1 "GND" H 1500 1550 50  0000 C CNN
F 2 "" H 1500 1700 50  0000 C CNN
F 3 "" H 1500 1700 50  0000 C CNN
	1    1500 1700
	1    0    0    -1  
$EndComp
$Comp
L clock-display-rescue:AVR-ISP-10 CON1
U 1 1 5856ED1B
P 2100 3800
F 0 "CON1" H 1930 4130 50  0000 C CNN
F 1 "AVR-ISP-10" H 1760 3470 50  0000 L BNN
F 2 "Pin_Headers:Pin_Header_Straight_2x05_Pitch2.54mm" V 1350 3850 50  0001 C CNN
F 3 "" H 2100 3800 50  0000 C CNN
	1    2100 3800
	-1   0    0    -1  
$EndComp
$Comp
L clock-display-rescue:CONN_02X05 P1
U 1 1 5856ED8F
P 2150 5150
F 0 "P1" H 2150 5450 50  0000 C CNN
F 1 "CONN_02X05" H 2150 4850 50  0000 C CNN
F 2 "Pin_Headers:Pin_Header_Straight_2x05_Pitch2.54mm" H 2150 3950 50  0001 C CNN
F 3 "" H 2150 3950 50  0000 C CNN
	1    2150 5150
	-1   0    0    1   
$EndComp
$Comp
L power:+5V #PWR012
U 1 1 5856EF82
P 1650 3200
F 0 "#PWR012" H 1650 3050 50  0001 C CNN
F 1 "+5V" H 1650 3340 50  0000 C CNN
F 2 "" H 1650 3200 50  0000 C CNN
F 3 "" H 1650 3200 50  0000 C CNN
	1    1650 3200
	1    0    0    -1  
$EndComp
$Comp
L power:+5V #PWR013
U 1 1 5856F065
P 1650 4650
F 0 "#PWR013" H 1650 4500 50  0001 C CNN
F 1 "+5V" H 1650 4790 50  0000 C CNN
F 2 "" H 1650 4650 50  0000 C CNN
F 3 "" H 1650 4650 50  0000 C CNN
	1    1650 4650
	1    0    0    -1  
$EndComp
$Comp
L power:GND #PWR014
U 1 1 5856F1F2
P 1650 4150
F 0 "#PWR014" H 1650 3900 50  0001 C CNN
F 1 "GND" H 1650 4000 50  0000 C CNN
F 2 "" H 1650 4150 50  0000 C CNN
F 3 "" H 1650 4150 50  0000 C CNN
	1    1650 4150
	1    0    0    -1  
$EndComp
$Comp
L clock-display-rescue:R R3
U 1 1 5856F45E
P 3100 4950
F 0 "R3" V 3180 4950 50  0000 C CNN
F 1 "220" V 3100 4950 50  0000 C CNN
F 2 "Resistors_SMD:R_1206_HandSoldering" V 3030 4950 50  0001 C CNN
F 3 "" H 3100 4950 50  0000 C CNN
	1    3100 4950
	0    -1   -1   0   
$EndComp
$Comp
L clock-display-rescue:R R4
U 1 1 5856F568
P 3100 5250
F 0 "R4" V 3180 5250 50  0000 C CNN
F 1 "220" V 3100 5250 50  0000 C CNN
F 2 "Resistors_SMD:R_1206_HandSoldering" V 3030 5250 50  0001 C CNN
F 3 "" H 3100 5250 50  0000 C CNN
	1    3100 5250
	0    -1   -1   0   
$EndComp
$Comp
L power:GND #PWR015
U 1 1 5856F8F8
P 2550 5600
F 0 "#PWR015" H 2550 5350 50  0001 C CNN
F 1 "GND" H 2550 5450 50  0000 C CNN
F 2 "" H 2550 5600 50  0000 C CNN
F 3 "" H 2550 5600 50  0000 C CNN
	1    2550 5600
	1    0    0    -1  
$EndComp
Entry Wire Line
	3800 4950 3900 5050
Entry Wire Line
	3800 5250 3900 5350
Entry Wire Line
	3800 3600 3900 3700
Entry Wire Line
	3800 3800 3900 3900
Entry Wire Line
	3800 3900 3900 4000
Entry Wire Line
	3800 4000 3900 4100
$Comp
L clock-display-rescue:R R1
U 1 1 5856FE71
P 2000 3350
F 0 "R1" V 2080 3350 50  0000 C CNN
F 1 "10k" V 2000 3350 50  0000 C CNN
F 2 "Resistors_SMD:R_1206_HandSoldering" V 1930 3350 50  0001 C CNN
F 3 "" H 2000 3350 50  0000 C CNN
	1    2000 3350
	0    -1   -1   0   
$EndComp
Text Label 3400 3600 0    60   ~ 0
MOSI_SDA
Text Label 3400 3800 0    60   ~ 0
RST
Text Label 3400 3900 0    60   ~ 0
SCK_SCL
Text Label 3400 4000 0    60   ~ 0
MISO_GAUGE
Text Label 3350 4950 0    60   ~ 0
SCK_SCL
Text Label 3350 5250 0    60   ~ 0
MOSI_SDA
Entry Wire Line
	3800 1850 3900 1950
Entry Wire Line
	3800 1950 3900 2050
Entry Wire Line
	3800 2550 3900 2650
Entry Wire Line
	3800 2050 3900 2150
Text Label 3400 1850 0    60   ~ 0
MOSI_SDA
Text Label 3400 2050 0    60   ~ 0
SCK_SCL
Text Label 3400 1950 0    60   ~ 0
MISO_GAUGE
$Comp
L V23079-monostable:V23079 K1
U 1 1 5869A95A
P 5400 6300
F 0 "K1" H 5350 6700 50  0000 C CNN
F 1 "V23079" H 5550 5800 50  0000 C CNN
F 2 "V23079:V23098-SMT-long" H 5400 6300 50  0001 C CNN
F 3 "" H 5400 6300 50  0000 C CNN
	1    5400 6300
	1    0    0    -1  
$EndComp
$Comp
L clock-display-rescue:R R7
U 1 1 586AADDE
P 9350 5800
F 0 "R7" V 9430 5800 50  0000 C CNN
F 1 "4k7" V 9350 5800 50  0000 C CNN
F 2 "Resistors_SMD:R_1206_HandSoldering" V 9280 5800 50  0001 C CNN
F 3 "" H 9350 5800 50  0000 C CNN
	1    9350 5800
	0    -1   -1   0   
$EndComp
$Comp
L clock-display-rescue:R R9
U 1 1 586AAE91
P 9700 5800
F 0 "R9" V 9780 5800 50  0000 C CNN
F 1 "4k7" V 9700 5800 50  0000 C CNN
F 2 "Resistors_SMD:R_1206_HandSoldering" V 9630 5800 50  0001 C CNN
F 3 "" H 9700 5800 50  0000 C CNN
	1    9700 5800
	0    -1   -1   0   
$EndComp
$Comp
L clock-display-rescue:CONN_01X04 P7
U 1 1 586D9DBE
P 10850 5750
F 0 "P7" H 10850 6000 50  0000 C CNN
F 1 "CONN_01X04" V 10950 5750 50  0000 C CNN
F 2 "micro-MaTch:0-188275-4_SMD_4-pin_female" H 10850 5750 50  0001 C CNN
F 3 "" H 10850 5750 50  0000 C CNN
	1    10850 5750
	1    0    0    1   
$EndComp
$Comp
L power:+36V #PWR016
U 1 1 586F3633
P 9950 5650
F 0 "#PWR016" H 9950 5500 50  0001 C CNN
F 1 "+36V" H 9950 5790 50  0000 C CNN
F 2 "" H 9950 5650 50  0000 C CNN
F 3 "" H 9950 5650 50  0000 C CNN
	1    9950 5650
	1    0    0    -1  
$EndComp
$Comp
L clock-display-rescue:CP C6
U 1 1 586F38CB
P 9950 6150
F 0 "C6" H 9975 6250 50  0000 L CNN
F 1 "220u" H 9975 6050 50  0000 L CNN
F 2 "Capacitors_SMD:c_elec_10x10" H 9988 6000 50  0001 C CNN
F 3 "" H 9950 6150 50  0000 C CNN
	1    9950 6150
	1    0    0    -1  
$EndComp
$Comp
L clock-display-rescue:BC849 Q1
U 1 1 586F5FC8
P 4900 7100
F 0 "Q1" H 5100 7175 50  0000 L CNN
F 1 "BC849" H 5100 7100 50  0000 L CNN
F 2 "TO_SOT_Packages_SMD:SOT-23_Handsoldering" H 5100 7025 50  0000 L CIN
F 3 "" H 4900 7100 50  0000 L CNN
	1    4900 7100
	1    0    0    -1  
$EndComp
$Comp
L clock-display-rescue:D D1
U 1 1 586F68E4
P 4800 6800
F 0 "D1" H 4800 6900 50  0000 C CNN
F 1 "D" H 4800 6700 50  0000 C CNN
F 2 "Diodes_SMD:SMA_Handsoldering" H 4800 6800 50  0001 C CNN
F 3 "" H 4800 6800 50  0000 C CNN
	1    4800 6800
	1    0    0    -1  
$EndComp
$Comp
L clock-display-rescue:R R5
U 1 1 586F6A91
P 4450 7100
F 0 "R5" V 4530 7100 50  0000 C CNN
F 1 "1k" V 4450 7100 50  0000 C CNN
F 2 "Resistors_SMD:R_1206_HandSoldering" V 4380 7100 50  0001 C CNN
F 3 "" H 4450 7100 50  0000 C CNN
	1    4450 7100
	0    -1   -1   0   
$EndComp
$Comp
L power:GND #PWR017
U 1 1 586F6BAA
P 5000 7400
F 0 "#PWR017" H 5000 7150 50  0001 C CNN
F 1 "GND" H 5000 7250 50  0000 C CNN
F 2 "" H 5000 7400 50  0000 C CNN
F 3 "" H 5000 7400 50  0000 C CNN
	1    5000 7400
	1    0    0    -1  
$EndComp
$Comp
L power:+5V #PWR018
U 1 1 586F6D21
P 4600 5850
F 0 "#PWR018" H 4600 5700 50  0001 C CNN
F 1 "+5V" H 4600 5990 50  0000 C CNN
F 2 "" H 4600 5850 50  0000 C CNN
F 3 "" H 4600 5850 50  0000 C CNN
	1    4600 5850
	1    0    0    -1  
$EndComp
$Comp
L clock-display-rescue:CONN_01X04 P3
U 1 1 586F7C39
P 6400 6000
F 0 "P3" H 6400 6250 50  0000 C CNN
F 1 "CONN_01X04" V 6500 6000 50  0000 C CNN
F 2 "micro-MaTch:0-188275-4_SMD_4-pin_female" H 6400 6000 50  0001 C CNN
F 3 "" H 6400 6000 50  0000 C CNN
	1    6400 6000
	1    0    0    -1  
$EndComp
$Comp
L clock-display-rescue:CONN_01X04 P4
U 1 1 586F9894
P 6400 6600
F 0 "P4" H 6400 6850 50  0000 C CNN
F 1 "CONN_01X04" V 6500 6600 50  0000 C CNN
F 2 "micro-MaTch:0-188275-4_SMD_4-pin_female" H 6400 6600 50  0001 C CNN
F 3 "" H 6400 6600 50  0000 C CNN
	1    6400 6600
	1    0    0    -1  
$EndComp
Entry Wire Line
	3900 7200 4000 7100
Text Label 4000 7100 0    60   ~ 0
REL1
$Comp
L V23079-monostable:V23079 K2
U 1 1 586FABEA
P 5450 3400
F 0 "K2" H 5400 3800 50  0000 C CNN
F 1 "V23079" H 5600 2900 50  0000 C CNN
F 2 "V23079:V23098-SMT-long" H 5450 3400 50  0001 C CNN
F 3 "" H 5450 3400 50  0000 C CNN
	1    5450 3400
	1    0    0    -1  
$EndComp
$Comp
L clock-display-rescue:BC849 Q2
U 1 1 586FABF0
P 4950 4200
F 0 "Q2" H 5150 4275 50  0000 L CNN
F 1 "BC849" H 5150 4200 50  0000 L CNN
F 2 "TO_SOT_Packages_SMD:SOT-23_Handsoldering" H 5150 4125 50  0000 L CIN
F 3 "" H 4950 4200 50  0000 L CNN
	1    4950 4200
	1    0    0    -1  
$EndComp
$Comp
L clock-display-rescue:D D2
U 1 1 586FABF6
P 4850 3900
F 0 "D2" H 4850 4000 50  0000 C CNN
F 1 "D" H 4850 3800 50  0000 C CNN
F 2 "Diodes_SMD:SMA_Handsoldering" H 4850 3900 50  0001 C CNN
F 3 "" H 4850 3900 50  0000 C CNN
	1    4850 3900
	1    0    0    -1  
$EndComp
$Comp
L clock-display-rescue:R R6
U 1 1 586FABFC
P 4500 4200
F 0 "R6" V 4580 4200 50  0000 C CNN
F 1 "1k" V 4500 4200 50  0000 C CNN
F 2 "Resistors_SMD:R_1206_HandSoldering" V 4430 4200 50  0001 C CNN
F 3 "" H 4500 4200 50  0000 C CNN
	1    4500 4200
	0    -1   -1   0   
$EndComp
$Comp
L power:GND #PWR019
U 1 1 586FAC02
P 5050 4500
F 0 "#PWR019" H 5050 4250 50  0001 C CNN
F 1 "GND" H 5050 4350 50  0000 C CNN
F 2 "" H 5050 4500 50  0000 C CNN
F 3 "" H 5050 4500 50  0000 C CNN
	1    5050 4500
	1    0    0    -1  
$EndComp
$Comp
L power:+5V #PWR020
U 1 1 586FAC08
P 4650 2950
F 0 "#PWR020" H 4650 2800 50  0001 C CNN
F 1 "+5V" H 4650 3090 50  0000 C CNN
F 2 "" H 4650 2950 50  0000 C CNN
F 3 "" H 4650 2950 50  0000 C CNN
	1    4650 2950
	1    0    0    -1  
$EndComp
$Comp
L clock-display-rescue:CONN_01X04 P5
U 1 1 586FAC17
P 6450 3100
F 0 "P5" H 6450 3350 50  0000 C CNN
F 1 "CONN_01X04" V 6550 3100 50  0000 C CNN
F 2 "micro-MaTch:0-215464-4_TH_4-pin_male" H 6450 3100 50  0001 C CNN
F 3 "" H 6450 3100 50  0000 C CNN
	1    6450 3100
	1    0    0    -1  
$EndComp
$Comp
L clock-display-rescue:CONN_01X04 P6
U 1 1 586FAC26
P 6450 3650
F 0 "P6" H 6450 3900 50  0000 C CNN
F 1 "CONN_01X04" V 6550 3650 50  0000 C CNN
F 2 "micro-MaTch:0-215464-4_TH_4-pin_male" H 6450 3650 50  0001 C CNN
F 3 "" H 6450 3650 50  0000 C CNN
	1    6450 3650
	1    0    0    -1  
$EndComp
Entry Wire Line
	3900 4300 4000 4200
Text Label 4000 4200 0    60   ~ 0
REL2
Entry Bus Bus
	3900 750  4000 650 
Entry Bus Bus
	8500 750  8600 650 
Entry Bus Bus
	9250 750  9350 650 
Wire Wire Line
	6850 3900 6800 3900
Wire Wire Line
	6800 3900 6800 4100
Wire Wire Line
	6850 4100 6800 4100
Connection ~ 6800 4100
Wire Wire Line
	6100 4800 6850 4800
Wire Wire Line
	6850 4600 6450 4600
Wire Wire Line
	6450 4550 6450 4600
Connection ~ 6450 4600
Wire Wire Line
	6100 4550 6100 4800
Connection ~ 6100 4800
Wire Wire Line
	6100 5200 6100 5350
Wire Wire Line
	6450 5200 6450 5350
Wire Wire Line
	9350 1200 9950 1200
Wire Wire Line
	9350 1300 9950 1300
Wire Wire Line
	9350 1400 9950 1400
Wire Wire Line
	9350 1500 9950 1500
Wire Wire Line
	9350 1600 9950 1600
Wire Wire Line
	9350 1750 9950 1750
Wire Wire Line
	9350 1850 9950 1850
Wire Wire Line
	9350 1950 9950 1950
Wire Wire Line
	9350 2050 9950 2050
Wire Wire Line
	9350 2150 9950 2150
Wire Wire Line
	9350 2250 9950 2250
Wire Wire Line
	9350 2350 9950 2350
Wire Wire Line
	9350 2500 9950 2500
Wire Wire Line
	9350 2600 9950 2600
Wire Wire Line
	9350 2700 9950 2700
Wire Wire Line
	9350 2800 9950 2800
Wire Wire Line
	9350 2900 9950 2900
Wire Wire Line
	9950 3000 9350 3000
Wire Wire Line
	9350 3150 9950 3150
Wire Wire Line
	9950 3250 9350 3250
Wire Wire Line
	9350 3350 9950 3350
Wire Wire Line
	9950 3450 9350 3450
Wire Wire Line
	9350 3550 9950 3550
Wire Wire Line
	9950 3650 9350 3650
Wire Wire Line
	9350 3750 9950 3750
Wire Wire Line
	9350 3900 9950 3900
Wire Wire Line
	9950 4000 9350 4000
Wire Wire Line
	9350 4100 9950 4100
Wire Wire Line
	9950 4200 9350 4200
Wire Wire Line
	9350 4350 9950 4350
Wire Wire Line
	9950 4500 9550 4500
Wire Wire Line
	9550 4500 9550 4700
Wire Wire Line
	9950 4600 9800 4600
Wire Wire Line
	9800 4600 9800 4700
Wire Wire Line
	1650 2450 1650 2550
Wire Wire Line
	1650 2450 1750 2450
Wire Wire Line
	1750 2550 1650 2550
Connection ~ 1650 2550
Wire Wire Line
	1750 1250 1650 1250
Wire Wire Line
	1650 1250 1650 950 
Wire Wire Line
	900  950  1200 950 
Wire Wire Line
	1500 1350 1500 950 
Connection ~ 1650 950 
Wire Wire Line
	1200 1350 1200 950 
Connection ~ 1500 950 
Wire Wire Line
	900  800  900  950 
Connection ~ 1200 950 
Connection ~ 900  950 
Wire Wire Line
	900  1700 900  1600
Wire Wire Line
	1200 1700 1200 1550
Wire Wire Line
	1500 1700 1500 1550
Wire Wire Line
	1650 4650 1650 4750
Wire Wire Line
	1650 5050 1900 5050
Wire Wire Line
	2400 4950 2550 4950
Wire Wire Line
	2550 4950 2550 4750
Wire Wire Line
	2550 4750 1650 4750
Connection ~ 1650 4750
Wire Wire Line
	1650 4000 2050 4000
Wire Wire Line
	1650 3700 1650 3800
Wire Wire Line
	2050 3700 1650 3700
Connection ~ 1650 4000
Wire Wire Line
	2050 3800 1650 3800
Connection ~ 1650 3800
Wire Wire Line
	2050 3900 1650 3900
Connection ~ 1650 3900
Wire Wire Line
	1650 3600 2050 3600
Wire Wire Line
	1650 3200 1650 3350
Wire Wire Line
	2400 5250 2950 5250
Wire Wire Line
	2950 4950 2800 4950
Wire Wire Line
	2800 4950 2800 5150
Wire Wire Line
	2800 5150 2400 5150
Wire Wire Line
	2400 5350 2550 5350
Wire Wire Line
	2550 5350 2550 5500
Wire Wire Line
	2550 5500 1800 5500
Wire Wire Line
	1800 5500 1800 5350
Wire Wire Line
	1800 5350 1900 5350
Connection ~ 2550 5500
Wire Wire Line
	1850 3350 1650 3350
Connection ~ 1650 3350
Wire Wire Line
	2150 3350 2850 3350
Wire Wire Line
	2850 3350 2850 3800
Wire Wire Line
	2300 3800 2850 3800
Wire Wire Line
	3800 3600 2300 3600
Connection ~ 2850 3800
Wire Wire Line
	3800 3900 2300 3900
Wire Wire Line
	3800 4000 2300 4000
Wire Wire Line
	3250 4950 3800 4950
Wire Wire Line
	3800 5250 3250 5250
Wire Wire Line
	3800 1850 3350 1850
Wire Wire Line
	3800 1950 3350 1950
Wire Wire Line
	3800 2050 3350 2050
Wire Wire Line
	3800 2550 3350 2550
Wire Wire Line
	9800 5000 9800 5100
Wire Wire Line
	9800 5100 10300 5100
Wire Wire Line
	10200 5200 9550 5200
Wire Wire Line
	9550 5200 9550 5000
Wire Wire Line
	10300 5100 10300 5600
Wire Wire Line
	10200 5700 10200 5200
Wire Wire Line
	9550 5800 9500 5800
Wire Wire Line
	5000 6650 5000 6800
Wire Wire Line
	4950 6800 5000 6800
Connection ~ 5000 6800
Wire Wire Line
	5000 6550 4600 6550
Wire Wire Line
	4600 5850 4600 6550
Wire Wire Line
	4600 6800 4650 6800
Connection ~ 4600 6550
Wire Wire Line
	4700 7100 4600 7100
Wire Wire Line
	5000 7300 5000 7400
Wire Wire Line
	5800 6150 6200 6150
Wire Wire Line
	6200 5850 5900 5850
Wire Wire Line
	5900 5850 5900 5950
Wire Wire Line
	5900 5950 5800 5950
Wire Wire Line
	4950 5800 4950 6050
Wire Wire Line
	4950 6050 5000 6050
Wire Wire Line
	4950 5800 6050 5800
Wire Wire Line
	6050 5800 6050 5950
Wire Wire Line
	6050 5950 6200 5950
Wire Wire Line
	6050 6250 6050 6650
Wire Wire Line
	6050 6250 5800 6250
Wire Wire Line
	5800 6450 6200 6450
Wire Wire Line
	4950 6500 6150 6500
Wire Wire Line
	4950 6500 4950 6350
Wire Wire Line
	4950 6350 5000 6350
Wire Wire Line
	4300 7100 4000 7100
Wire Wire Line
	5050 3750 5050 3900
Wire Wire Line
	5000 3900 5050 3900
Connection ~ 5050 3900
Wire Wire Line
	5050 3650 4650 3650
Wire Wire Line
	4650 2950 4650 3650
Wire Wire Line
	4650 3900 4700 3900
Connection ~ 4650 3650
Wire Wire Line
	4750 4200 4650 4200
Wire Wire Line
	5050 4400 5050 4500
Wire Wire Line
	5850 3250 6250 3250
Wire Wire Line
	6250 2950 5950 2950
Wire Wire Line
	5950 2950 5950 3050
Wire Wire Line
	5950 3050 5850 3050
Wire Wire Line
	5000 2900 5000 3150
Wire Wire Line
	5000 3150 5050 3150
Wire Wire Line
	5000 2900 6100 2900
Wire Wire Line
	6100 2900 6100 3050
Wire Wire Line
	6100 3050 6250 3050
Wire Wire Line
	6250 3500 6100 3500
Wire Wire Line
	6100 3500 6100 3350
Wire Wire Line
	6100 3350 5850 3350
Wire Wire Line
	6250 3800 5900 3800
Wire Wire Line
	5900 3800 5900 3550
Wire Wire Line
	5900 3550 5850 3550
Wire Wire Line
	6250 3700 6100 3700
Wire Wire Line
	6100 3700 6100 3600
Wire Wire Line
	6100 3600 5000 3600
Wire Wire Line
	5000 3600 5000 3450
Wire Wire Line
	5000 3450 5050 3450
Wire Wire Line
	4000 4200 4350 4200
Entry Wire Line
	8500 5900 8600 5800
Wire Wire Line
	9200 5800 8600 5800
Text Label 8600 5800 0    60   ~ 0
MISO_GAUGE
$Comp
L clock-display-rescue:CONN_01X02 P2
U 1 1 586FE2DD
P 2300 6750
F 0 "P2" H 2300 6900 50  0000 C CNN
F 1 "CONN_01X02" V 2400 6750 50  0000 C CNN
F 2 "Pin_Headers:Pin_Header_Straight_1x02_Pitch2.54mm" H 2300 6750 50  0001 C CNN
F 3 "" H 2300 6750 50  0000 C CNN
	1    2300 6750
	-1   0    0    1   
$EndComp
$Comp
L power:+5V #PWR021
U 1 1 586FE50E
P 2950 6200
F 0 "#PWR021" H 2950 6050 50  0001 C CNN
F 1 "+5V" H 2950 6340 50  0000 C CNN
F 2 "" H 2950 6200 50  0000 C CNN
F 3 "" H 2950 6200 50  0000 C CNN
	1    2950 6200
	1    0    0    -1  
$EndComp
$Comp
L clock-display-rescue:R R2
U 1 1 586FE5B9
P 2950 6450
F 0 "R2" V 3030 6450 50  0000 C CNN
F 1 "4k7" V 2950 6450 50  0000 C CNN
F 2 "Resistors_SMD:R_1206_HandSoldering" V 2880 6450 50  0001 C CNN
F 3 "" H 2950 6450 50  0000 C CNN
	1    2950 6450
	1    0    0    -1  
$EndComp
Wire Wire Line
	2500 6700 2950 6700
Wire Wire Line
	2950 6700 2950 6600
$Comp
L power:GND #PWR022
U 1 1 586FE7E4
P 2600 6900
F 0 "#PWR022" H 2600 6650 50  0001 C CNN
F 1 "GND" H 2600 6750 50  0000 C CNN
F 2 "" H 2600 6900 50  0000 C CNN
F 3 "" H 2600 6900 50  0000 C CNN
	1    2600 6900
	1    0    0    -1  
$EndComp
Wire Wire Line
	2500 6800 2600 6800
Wire Wire Line
	2600 6800 2600 6900
Connection ~ 2950 6700
Entry Wire Line
	3800 6700 3900 6800
Text Label 3500 6700 0    60   ~ 0
1WIRE
Wire Wire Line
	2950 6300 2950 6200
Entry Wire Line
	6650 650  6750 750 
Entry Wire Line
	6350 650  6450 750 
Entry Wire Line
	6250 650  6350 750 
Entry Wire Line
	6050 650  6150 750 
Wire Wire Line
	6850 1200 6750 1200
Wire Wire Line
	6750 1200 6750 750 
Wire Wire Line
	6850 1500 6450 1500
Wire Wire Line
	6450 1500 6450 750 
Wire Wire Line
	6850 1600 6350 1600
Wire Wire Line
	6350 1600 6350 750 
Wire Wire Line
	6850 1800 6150 1800
Wire Wire Line
	6150 1800 6150 750 
Text Label 6750 950  3    60   ~ 0
CK
$Comp
L power:GND #PWR023
U 1 1 5870136C
P 6750 2250
F 0 "#PWR023" H 6750 2000 50  0001 C CNN
F 1 "GND" H 6750 2100 50  0000 C CNN
F 2 "" H 6750 2250 50  0000 C CNN
F 3 "" H 6750 2250 50  0000 C CNN
	1    6750 2250
	1    0    0    -1  
$EndComp
Wire Wire Line
	6750 2250 6750 1300
Wire Wire Line
	6750 1300 6850 1300
Text Label 6450 1050 1    60   ~ 0
CL
Text Label 6350 1050 1    60   ~ 0
STB
Text Label 6150 1050 1    60   ~ 0
S-IN
$Comp
L clock-display-rescue:R R11
U 1 1 587033D7
P 10300 6150
F 0 "R11" V 10380 6150 50  0000 C CNN
F 1 "100k" V 10300 6150 50  0000 C CNN
F 2 "Resistors_SMD:R_1206_HandSoldering" V 10230 6150 50  0001 C CNN
F 3 "" H 10300 6150 50  0000 C CNN
	1    10300 6150
	-1   0    0    1   
$EndComp
$Comp
L power:GND #PWR024
U 1 1 587034C2
P 10300 6300
F 0 "#PWR024" H 10300 6050 50  0001 C CNN
F 1 "GND" H 10300 6150 50  0000 C CNN
F 2 "" H 10300 6300 50  0000 C CNN
F 3 "" H 10300 6300 50  0000 C CNN
	1    10300 6300
	1    0    0    -1  
$EndComp
Entry Wire Line
	8400 1100 8500 1200
Entry Wire Line
	8400 5000 8500 5100
Wire Wire Line
	8050 1100 8400 1100
Wire Wire Line
	8050 5000 8400 5000
Text Label 8150 1100 0    60   ~ 0
2segA
Text Label 8100 5000 0    60   ~ 0
3segG
Entry Wire Line
	8400 4900 8500 5000
Entry Wire Line
	8400 4800 8500 4900
Entry Wire Line
	8400 1200 8500 1300
Entry Wire Line
	8400 1300 8500 1400
Entry Wire Line
	8400 1400 8500 1500
Entry Wire Line
	8400 4700 8500 4800
Wire Wire Line
	8050 4700 8400 4700
Wire Wire Line
	8400 4800 8050 4800
Wire Wire Line
	8400 4900 8050 4900
Text Label 8100 4900 0    60   ~ 0
3segF
Text Label 8100 4800 0    60   ~ 0
GRID
Text Label 8100 4700 0    60   ~ 0
2segE
Wire Wire Line
	8400 1200 8050 1200
Wire Wire Line
	8050 1300 8400 1300
Wire Wire Line
	8400 1400 8050 1400
Text Label 8150 1200 0    60   ~ 0
2segB
Text Label 8150 1300 0    60   ~ 0
2segD
Text Label 8150 1400 0    60   ~ 0
2segC
Entry Wire Line
	8400 4600 8500 4700
Entry Wire Line
	8400 4500 8500 4600
Entry Wire Line
	8400 4400 8500 4500
Entry Wire Line
	8400 4300 8500 4400
Entry Wire Line
	8400 4200 8500 4300
Entry Wire Line
	8400 4100 8500 4200
Entry Wire Line
	8400 4000 8500 4100
Entry Wire Line
	8400 3900 8500 4000
Entry Wire Line
	8400 3800 8500 3900
Entry Wire Line
	8400 3700 8500 3800
Entry Wire Line
	8400 3600 8500 3700
Wire Wire Line
	8050 4600 8400 4600
Wire Wire Line
	8400 4500 8050 4500
Wire Wire Line
	8050 4400 8400 4400
Wire Wire Line
	8400 4300 8050 4300
Wire Wire Line
	8050 4200 8400 4200
Wire Wire Line
	8400 4100 8050 4100
Wire Wire Line
	8050 4000 8400 4000
Wire Wire Line
	8400 3900 8050 3900
Wire Wire Line
	8050 3800 8400 3800
Wire Wire Line
	8400 3700 8050 3700
Wire Wire Line
	8050 3600 8400 3600
Text Label 8100 4600 0    60   ~ 0
3segAD
Text Label 8100 4500 0    60   ~ 0
3segB
Text Label 8100 4400 0    60   ~ 0
3segE
Text Label 8100 4300 0    60   ~ 0
3segC
Text Label 8100 4200 0    60   ~ 0
4segF
Text Label 8100 4100 0    60   ~ 0
4segG
Text Label 8100 4000 0    60   ~ 0
4segA
Text Label 8100 3900 0    60   ~ 0
4segB
Text Label 8100 3800 0    60   ~ 0
4segE
Text Label 8100 3700 0    60   ~ 0
4segC
Text Label 8100 3600 0    60   ~ 0
4segD
Entry Wire Line
	8400 1500 8500 1600
Entry Wire Line
	8400 1600 8500 1700
Entry Wire Line
	8400 1700 8500 1800
Entry Wire Line
	8400 1800 8500 1900
Entry Wire Line
	8400 1900 8500 2000
Entry Wire Line
	8400 2000 8500 2100
Entry Wire Line
	8400 2100 8500 2200
Entry Wire Line
	8400 2200 8500 2300
Entry Wire Line
	8400 2300 8500 2400
Entry Wire Line
	8400 2400 8500 2500
Entry Wire Line
	8400 2500 8500 2600
Wire Wire Line
	8050 1500 8400 1500
Wire Wire Line
	8400 1600 8050 1600
Wire Wire Line
	8050 1700 8400 1700
Wire Wire Line
	8400 1800 8050 1800
Wire Wire Line
	8050 1900 8400 1900
Wire Wire Line
	8400 2000 8050 2000
Wire Wire Line
	8050 2100 8400 2100
Wire Wire Line
	8400 2200 8050 2200
Wire Wire Line
	8050 2300 8400 2300
Wire Wire Line
	8400 2400 8050 2400
Wire Wire Line
	8050 2500 8400 2500
Text Label 8150 1500 0    60   ~ 0
2segG
Text Label 8150 1600 0    60   ~ 0
2segF
Text Label 8150 1700 0    60   ~ 0
1segB
Text Label 8150 1800 0    60   ~ 0
1segC
Text Label 8050 1900 0    60   ~ 0
1segAED
Text Label 8150 2000 0    60   ~ 0
1segG
Text Label 8150 2100 0    60   ~ 0
1segF
Text Label 8150 2200 0    60   ~ 0
dotAM
Text Label 8150 2300 0    60   ~ 0
dotPM
Text Label 8100 2400 0    60   ~ 0
dotDOWN
Text Label 8150 2500 0    60   ~ 0
dotUP
NoConn ~ 8050 2600
NoConn ~ 8050 2700
NoConn ~ 8050 2800
NoConn ~ 8050 2900
NoConn ~ 8050 3000
NoConn ~ 8050 3100
NoConn ~ 8050 3200
NoConn ~ 8050 3300
NoConn ~ 8050 3400
NoConn ~ 8050 3500
NoConn ~ 2300 3700
NoConn ~ 2400 5050
NoConn ~ 1900 5150
NoConn ~ 1900 5250
NoConn ~ 1900 4950
NoConn ~ 6200 6050
NoConn ~ 6250 3150
NoConn ~ 6250 3600
NoConn ~ 6850 2100
Wire Wire Line
	9950 5900 10300 5900
Wire Wire Line
	9950 5650 9950 5900
Wire Wire Line
	10300 6000 10300 5900
Connection ~ 10300 5900
Connection ~ 9950 5900
Wire Wire Line
	10300 5600 10650 5600
Wire Wire Line
	10650 5700 10200 5700
Wire Wire Line
	9850 5800 10650 5800
Wire Wire Line
	6150 6500 6150 6550
Wire Wire Line
	6150 6550 6200 6550
Wire Wire Line
	6050 6650 6200 6650
Entry Wire Line
	3800 2150 3900 2250
Wire Wire Line
	3350 2150 3800 2150
Text Label 3400 2150 0    60   ~ 0
CL
Text Label 3500 2550 0    60   ~ 0
RST
Entry Wire Line
	3800 1050 3900 1150
Wire Wire Line
	3350 1050 3800 1050
Entry Wire Line
	3800 1150 3900 1250
Wire Wire Line
	3800 1150 3350 1150
Text Label 3500 1050 0    60   ~ 0
STB
Entry Wire Line
	3800 1250 3900 1350
Wire Wire Line
	3800 1250 3350 1250
Text Label 3500 1250 0    60   ~ 0
S-IN
Text Label 3500 1150 0    60   ~ 0
CK
Entry Wire Line
	3800 1350 3900 1450
Wire Wire Line
	3350 1350 3800 1350
Text Label 3500 1350 0    60   ~ 0
1WIRE
$Comp
L RFM83C-433S:RFM83C-433S IC3
U 1 1 5876A5C6
P 4650 2000
F 0 "IC3" H 4750 2600 60  0000 C CNN
F 1 "RFM83C-433S" H 5000 1950 60  0000 C CNN
F 2 "RFM83C_standard:RFM83C_standard" H 5000 1850 60  0001 C CNN
F 3 "" H 5000 1850 60  0001 C CNN
	1    4650 2000
	1    0    0    -1  
$EndComp
$Comp
L clock-display-rescue:CONN_01X02 P8
U 1 1 5876A79F
P 5700 1600
F 0 "P8" H 5700 1750 50  0000 C CNN
F 1 "CONN_01X02" V 5800 1600 50  0000 C CNN
F 2 "Pin_Headers:Pin_Header_Straight_1x02_Pitch2.54mm" H 5700 1600 50  0001 C CNN
F 3 "" H 5700 1600 50  0000 C CNN
	1    5700 1600
	1    0    0    -1  
$EndComp
Wire Wire Line
	5500 1550 5450 1550
Wire Wire Line
	5450 1650 5500 1650
Entry Wire Line
	3900 1750 4000 1650
$Comp
L power:+5V #PWR025
U 1 1 5876B45B
P 4350 1350
F 0 "#PWR025" H 4350 1200 50  0001 C CNN
F 1 "+5V" H 4350 1490 50  0000 C CNN
F 2 "" H 4350 1350 50  0000 C CNN
F 3 "" H 4350 1350 50  0000 C CNN
	1    4350 1350
	1    0    0    -1  
$EndComp
$Comp
L power:GND #PWR026
U 1 1 5876B919
P 4400 2350
F 0 "#PWR026" H 4400 2100 50  0001 C CNN
F 1 "GND" H 4400 2200 50  0000 C CNN
F 2 "" H 4400 2350 50  0000 C CNN
F 3 "" H 4400 2350 50  0000 C CNN
	1    4400 2350
	1    0    0    -1  
$EndComp
Wire Wire Line
	4400 2350 4400 1950
Wire Wire Line
	4400 1950 4450 1950
Wire Wire Line
	4450 1650 4000 1650
Text Label 4000 1650 0    60   ~ 0
RADIO
Entry Wire Line
	3800 2450 3900 2550
Wire Wire Line
	3350 2450 3800 2450
Text Label 3500 2450 0    60   ~ 0
RADIO
Wire Wire Line
	4350 1350 4350 1850
NoConn ~ 4450 1550
Wire Wire Line
	4350 1850 4450 1850
Entry Wire Line
	3800 1550 3900 1650
Entry Wire Line
	3800 1650 3900 1750
Wire Wire Line
	3350 1550 3800 1550
Wire Wire Line
	3800 1650 3350 1650
Text Label 3500 1550 0    60   ~ 0
REL1
Text Label 3500 1650 0    60   ~ 0
REL2
NoConn ~ 6200 6750
NoConn ~ 3350 950 
NoConn ~ 3350 1450
NoConn ~ 3350 2250
NoConn ~ 3350 2350
$Comp
L power:PWR_FLAG #FLG027
U 1 1 587810C4
P 10550 5500
F 0 "#FLG027" H 10550 5595 50  0001 C CNN
F 1 "PWR_FLAG" H 10550 5680 50  0000 C CNN
F 2 "" H 10550 5500 50  0000 C CNN
F 3 "" H 10550 5500 50  0000 C CNN
	1    10550 5500
	1    0    0    -1  
$EndComp
Wire Wire Line
	10550 5500 10550 5900
Connection ~ 10550 5900
Wire Wire Line
	6800 4100 6800 4200
Wire Wire Line
	6450 4600 6450 5000
Wire Wire Line
	6100 4800 6100 5000
Wire Wire Line
	1650 2550 1650 2650
Wire Wire Line
	1650 950  1750 950 
Wire Wire Line
	1500 950  1650 950 
Wire Wire Line
	1200 950  1500 950 
Wire Wire Line
	900  950  900  1300
Wire Wire Line
	1650 4750 1650 5050
Wire Wire Line
	1650 4000 1650 4150
Wire Wire Line
	1650 3800 1650 3900
Wire Wire Line
	1650 3900 1650 4000
Wire Wire Line
	2550 5500 2550 5600
Wire Wire Line
	1650 3350 1650 3600
Wire Wire Line
	2850 3800 3800 3800
Wire Wire Line
	5000 6800 5000 6900
Wire Wire Line
	4600 6550 4600 6800
Wire Wire Line
	5050 3900 5050 4000
Wire Wire Line
	4650 3650 4650 3900
Wire Wire Line
	2950 6700 3800 6700
Wire Wire Line
	10300 5900 10550 5900
Wire Wire Line
	9950 5900 9950 6000
Wire Wire Line
	10550 5900 10650 5900
Wire Bus Line
	4000 650  9600 650 
Wire Bus Line
	3900 750  3900 7400
Wire Bus Line
	9250 750  9250 5200
Wire Bus Line
	8500 750  8500 6050
$EndSCHEMATC
