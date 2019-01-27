#!/usr/bin/env python3

"""Eventlistener for supervisord. It sets the GPIO high when the process has RUNNING state."""

import sys
import os


def write_stdout(s):
    # only eventlistener protocol messages may be sent to stdout
    sys.stdout.write(s)
    sys.stdout.flush()


def write_stderr(s):
    sys.stderr.write(s)
    sys.stderr.write('\n')
    sys.stderr.flush()


def write_to_gpio(path, content):
    p = '/sys/class/gpio/' + path
    write_stderr("Writing %s to %s" % (content, p))
    with open(p, 'w') as f:
        print(content, file=f)


if len(sys.argv) < 3:
    write_stderr("Usage: %s {process name} {GPIO number}" % sys.argv[0])
    sys.exit(1)

process_name, gpio_number = sys.argv[1:3]

gpio_dir = '/sys/class/gpio/gpio' + gpio_number
if not os.path.isdir(gpio_dir):
    write_to_gpio('export', str(gpio_number))

write_to_gpio('gpio' + str(gpio_number) + '/direction', 'out')
gpio_value_file = 'gpio' + str(gpio_number) + '/value'

write_stderr("GPIO event listener started for process %s and GPIO %s." % (process_name, gpio_number))

while 1:
    write_stdout('READY\n')
    line = sys.stdin.readline()

    headers = dict([x.split(':') for x in line.split()])

    data = sys.stdin.read(int(headers['len']))
    data_dict = dict([x.split(':') for x in data.split()])

    if data_dict['processname'] == process_name:
        if headers['eventname'] == 'PROCESS_STATE_RUNNING':
            write_stderr("Process %s is running." % process_name)
            write_to_gpio(gpio_value_file, '1')
        else:
            write_stderr("Process %s is stopped or failed." % process_name)
            write_to_gpio(gpio_value_file, '0')

    write_stdout('RESULT 2\nOK')
