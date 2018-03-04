#!/usr/bin/env python3

import csv

code = 32
with open('original.csv') as csvfile:
    reader = csv.reader(csvfile)
    for row in reader:
        print(' <CHAR CODE="%d" PIXELS="' % code, end="")

        pixels = []
        for col in range(0, 5):
            col_val = int(row[col], 16)
            for i in range(0, 7):
                if col_val & 2 ** i:
                    pixels.append("0")
                else:
                    pixels.append("16777215")
        print(','.join(pixels), end='')
        print('"/>')
        code += 1
