#!/bin/sh

#############################################
# Build the JNI libraries for Linux         #
#############################################

JAVA_HOME=/usr/lib/jvm/default-java
CLASS=NxtMint-1.2.0.jar
INCLUDE=include
SRC=src
OBJ=obj
JNI=jni
PKG=org.ScripterRon.NxtMint

if [ ! -d $INCLUDE ] ; then
    mkdir $INCLUDE
fi

if [ ! -d $OBJ ] ; then
    mkdir $OBJ
fi

if [ ! -d $JNI ] ; then
    mkdir $JNI
fi

echo "Building the Java include files"
javah -d $INCLUDE -cp $CLASS $PKG.HashKnv25 $PKG.HashScrypt  || exit 1

echo "Building libNxtMint_x86_64.so"
gcc -c -O3 -m64 -fPIC -D_POSIX_C_SOURCE -I"$INCLUDE" -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" -o $OBJ/JniKnv25.o $SRC/JniKnv25.c || exit 1
gcc -c -O3 -m64 -fPIC -D_POSIX_C_SOURCE -I"$INCLUDE" -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" -o $OBJ/JniScrypt.o $SRC/JniScrypt.c || exit 1
gcc -m64 -shared -o $JNI/libNxtMint_x86_64.so $OBJ/JniKnv25.o $OBJ/JniScrypt.o || exit 1

echo "Building libNxtMint_x86.so"
gcc -c -O3 -m32 -fPIC -D_POSIX_C_SOURCE -I"$INCLUDE" -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" -o $OBJ/JniKnv25.o $SRC/JniKnv25.c || exit 1
gcc -c -O3 -m32 -fPIC -D_POSIX_C_SOURCE -I"$INCLUDE" -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" -o $OBJ/JniScrypt.o $SRC/JniScrypt.c || exit 1
gcc -m32 -shared -o $JNI/libNxtMint_x86.so $OBJ/JniKnv25.o $OBJ/JniScrypt.o || exit 1

exit 0

