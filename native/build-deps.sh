#!/usr/bin/env bash
# Cross-compile GMP, MPFR, libxml2 and libqalculate as static arm64 libraries
# for Android. Run inside the dev shell (direnv exec . native/build-deps.sh).
# Skips any library already built; delete native/prebuilt to rebuild.
set -euo pipefail
trap 'echo "build-deps: FAILED (logs in native/src/*.log)" >&2' ERR

here=$(cd "$(dirname "$0")" && pwd)
source "$here/versions.env"
: "${ANDROID_NDK_ROOT:?run inside the dev shell}"

abi=arm64-v8a
triple=aarch64-linux-android
prefix=$here/prebuilt/$abi
src=$here/src
mkdir -p "$prefix" "$src"

tc=$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin
export CC=$tc/$triple$API-clang
export CXX=$tc/$triple$API-clang++
export AR=$tc/llvm-ar RANLIB=$tc/llvm-ranlib STRIP=$tc/llvm-strip NM=$tc/llvm-nm
export CFLAGS="-O2 -fPIC" CXXFLAGS="-O2 -fPIC"
export CPPFLAGS="-I$prefix/include" LDFLAGS="-L$prefix/lib"
export PKG_CONFIG_LIBDIR=$prefix/lib/pkgconfig PKG_CONFIG_PATH=
jobs=$(nproc)

# fetch NAME URL SHA256 -> prints the unpacked source dir
fetch() {
  local url=$2 sha=$3 file=$src/${2##*/}
  if [ ! -f "$file" ] || ! echo "$sha  $file" | sha256sum -c --quiet - 2>/dev/null; then
    curl -sSLf -o "$file" "$url"
    echo "$sha  $file" | sha256sum -c --quiet -
  fi
  local dir=$src/$(tar tf "$file" | head -1 | cut -d/ -f1)
  rm -rf "$dir"
  tar xf "$file" -C "$src"
  echo "$dir"
}

# build NAME LIBFILE URL SHA256 CONFIGURE_ARGS...
build() {
  local name=$1 lib=$2 url=$3 sha=$4; shift 4
  if [ -f "$prefix/lib/$lib" ]; then echo "== $name: already built"; return; fi
  echo "== $name"
  local dir; dir=$(fetch "$name" "$url" "$sha")
  for p in "$here"/patches/$name-*.patch; do
    if [ -e "$p" ]; then patch -d "$dir" -p1 -s < "$p"; fi
  done
  (cd "$dir" &&
    ./configure --host=$triple --prefix="$prefix" --enable-static --disable-shared --with-pic "$@" > "$src/$name.configure.log" &&
    make -j"$jobs" > "$src/$name.make.log" 2>&1 &&
    make install > "$src/$name.install.log")
}

build gmp libgmp.a "$GMP_URL" "$GMP_SHA256" --enable-cxx=no
build mpfr libmpfr.a "$MPFR_URL" "$MPFR_SHA256" --with-gmp="$prefix"
build libxml2 libxml2.a "$LIBXML2_URL" "$LIBXML2_SHA256" \
  --without-python --without-zlib --without-icu --without-modules --without-readline --without-history \
  --without-programs
build libqalculate libqalculate.a "$LIBQALCULATE_URL" "$LIBQALCULATE_SHA256" \
  --enable-compiled-definitions --without-libcurl --without-icu --without-gnuplot-call \
  --disable-insecure --disable-nls --disable-textport

echo "== done: $prefix"
