#!/bin/sh
# Start NxtMint

##################################################
# Rename to mint.sh and make any desired changes #
##################################################

echo "Starting NxtMint"
java -Xmx256m -Djava.library.path="jni" -jar NxtMint-1.5.0.jar

