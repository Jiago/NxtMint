#!/bin/sh
# Start NxtMint
echo "Starting NxtMint"
java -Xmx256m -Djava.library.path=aparapi -jar Nxt-1.1.0.jar
