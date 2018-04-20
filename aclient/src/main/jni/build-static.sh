#!/bin/bash
# https://kvurd.com/blog/compiling-a-cpp-library-for-android-with-android-studio/

declare -a arches=("arm" "arm64" "x86" "x86_64" "mips" "mips64")

for arch in "${arches[@]}"
do
	toolchain=/tmp/"$arch"acc
	/ssdhome/Daniel/android-sdk/android-ndk-r10e/build/tools/make-standalone-toolchain.sh --arch=$arch --install-dir=$toolchain
	export PATH=$toolchain/bin:$PATH
	gxx=$(ls $toolchain/bin | grep g++)
	prefix=${gxx::-4}
	export CC="$prefix-gcc"
	export CXX="$gxx"
	export CFLAGS="-O2"
	cd "$1"
	rm -rf build-"$arch"
	rm Makefile
	./configure --prefix=$(pwd)/build-"$arch" --host="$prefix" --disable-shared
	make clean
	make -j16
	make install
done
