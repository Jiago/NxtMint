#!/bin/sh
# Start NxtMint

##################################################
# Rename to mint.sh and make any desired changes #
##################################################

echo "Starting NxtMint"
java -Xmx256m -Djava.library.path="aparapi;jni" -jar Nxt-1.2.0.jar

