#!/bin/bash
# https://kvurd.com/blog/compiling-a-cpp-library-for-android-with-android-studio/

arch="arm"
toolchain=/tmp/"$arch"acc
/ssdhome/Daniel/android-sdk/android-ndk-r10e/build/tools/make-standalone-toolchain.sh --arch="$arch" --install-dir="$toolchain"
echo $toolchain/bin
export PATH=$toolchain/bin:$PATH
gxx=$(ls "$toolchain/bin" | grep g++)
ls $toolchain/bin
prefix="${gxx::-4}"
export CC="$prefix-gcc"
export CXX="$gxx"
cd "$1"
rm -rf build-"$arch"
rm Makefile
./configure CFLAGS="-O2 -march=armv7-a -mfpu=vfpv3-d16" --prefix=$(pwd)/build-"$arch-v7a" --host="$prefix" --disable-shared
make clean
make -j16
make install
