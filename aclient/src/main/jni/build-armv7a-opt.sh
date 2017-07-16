#!/bin/bash
# https://kvurd.com/blog/compiling-a-cpp-library-for-android-with-android-studio/

arch="arm"
toolchain=/tmp/"$arch"acc
/ssdhome/Daniel/android-sdk/android-ndk-r10e/build/tools/make-standalone-toolchain.sh --arch=$arch --install-dir=$toolchain
export PATH=$toolchain/bin:$PATH
gxx=$(ls $toolchain/bin | grep g++)
prefix=${gxx::-4}
export CC="$prefix"-gcc
export CXX=$gxx
export CFLAGS="-march=armv7a+fp" #even the tegra 2 can do vfpv3d16 BUT couldn't do neon so... no neon
cd $1
echo $arch $toolchain $gxx $prefix
rm -rf build-"$arch"
./configure --prefix=$(pwd)/build-"$arch-v7a" --host=$prefix --disable-shared
make clean
make -j16
make install
