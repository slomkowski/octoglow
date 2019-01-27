#!/bin/bash

BUTTON_GPIO=6
STARTED_GPIO=2

BUTTON_GPIO_DIR="/sys/class/gpio/gpio${BUTTON_GPIO}" 
STARTED_GPIO_DIR="/sys/class/gpio/gpio${STARTED_GPIO}" 

if [ ! -d $STARTED_GPIO_DIR ] ; then
    echo ${STARTED_GPIO} > /sys/class/gpio/export
fi

if [ ! -d $BUTTON_GPIO_DIR ] ; then
    echo ${BUTTON_GPIO} > /sys/class/gpio/export
fi

echo in > $BUTTON_GPIO_DIR/direction

echo out > $STARTED_GPIO_DIR/direction
echo 1 > $STARTED_GPIO_DIR/value

while true ; do
    button_val=`cat $BUTTON_GPIO_DIR/value`
    if [ $button_val == '0' ] ; then
        echo "Shutting the system down."
	echo 0 > $STARTED_GPIO_DIR/value
	shutdown -h now
	exit 0
    fi
    sleep 0.3
done

