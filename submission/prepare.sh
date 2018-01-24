#!/bin/sh

# We prepare an object file containing our bot
# NOTE: RUN THIS BEFORE SUBMITTING THE BOT!!!

# we provide this env variable for you
BC_PLATFORM='LINUX'
if [ "$BC_PLATFORM" = 'LINUX' ]; then
    LIBRARIES="-lbattlecode-linux -lutil -ldl -lrt -pthread -lgcc_s -lc -lm -L../battlecode/c/lib"
    INCLUDES="-I../battlecode/c/include -I."
elif [ "$BC_PLATFORM" = 'DARWIN' ]; then
    LIBRARIES="-lbattlecode-darwin -lSystem -lresolv -lc -lm -L../battlecode/c/lib"
    INCLUDES="-I../battlecode/c/include -I."
else
	echo "Unknown platform '$BC_PLATFORM' or platform not set"
	echo "Make sure the BC_PLATFORM environment variable is set"
	exit 1
fi

echo "$ g++ -o main.o main.cpp -c -O3 -g $INCLUDES -DNDEBUG -std=c++11 -fPIC -U_FORTIFY_SOURCE"
g++ -o main.o main.cpp -c -O3 -g $INCLUDES -DNDEBUG -std=c++11 -fPIC -U_FORTIFY_SOURCE
