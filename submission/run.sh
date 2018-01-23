#!/bin/sh
# build the program!
# note: there will eventually be a separate build step for your bot, but for now it counts against your runtime.
echo starting script

LIBRARIES="-lutil -ldl -lrt -lpthread -lgcc_s -lc -lm -L/battlecode-c/lib/ -lbattlecode"
INCLUDES="-I/battlecode-c/include"

echo starting compilation

g++ -std=c++14 -O3 main.cpp -o main $LIBRARIES $INCLUDES -DNOT_IN_DEBUG_MODE

echo compilation finished, starting bot

# run the program!
./main
