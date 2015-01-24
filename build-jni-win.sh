#!/bin/sh

#############################################
# Build the JNI libraries for Windows       #
#                                           #
# 64-bit gcc is in PATH                     #
# 32-bit gcc is in /c/mingw/bin             #
#                                           #
# 64-bit Windows switches \Windows\system32 #
# based on whether the executable is        #
# 32-bit or 64-bit, so we need to use       #
# the 32-bit gcc when linking the           #
# 32-bit dll.                               #
#############################################

CLASS=target/classes
INCLUDE=target/generated-sources/include
SRC=src/main/c
OBJ=target/generated-sources/obj
JNI=target/jni
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
javah -d $INCLUDE -cp $CLASS $PKG.HashKnv25  || exit 1

echo "Building NxtMint_x86_64.dll"
gcc -c -O3 -m64 -D_POSIX_C_SOURCE -I"$INCLUDE" -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/win32" -o $OBJ/JniKnv25.o $SRC/JniKnv25.c || exit 1
gcc -m64 -shared -Wl,--kill-at -o $JNI/NxtMint_x86_64.dll $OBJ/JniKnv25.o || exit 1

echo "Building NxtMint_x86.dll"
gcc -c -O3 -m32 -D_POSIX_C_SOURCE -I"$INCLUDE" -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/win32" -o $OBJ/JniKnv25.o $SRC/JniKnv25.c || exit 1
/c/mingw/bin/gcc -m32 -shared -Wl,--kill-at -o $JNI/NxtMint_x86.dll $OBJ/JniKnv25.o || exit 1

exit 0

