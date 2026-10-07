{
  description = "qalc-android: libqalculate on Android";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs = { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs {
        inherit system;
        config = {
          allowUnfree = true;
          android_sdk.accept_license = true;
        };
      };
      android = pkgs.androidenv.composeAndroidPackages {
        platformVersions = [ "35" ];
        buildToolsVersions = [ "35.0.0" ];
        includeNDK = true;
        cmakeVersions = [ "3.22.1" ];
      };
      sdk = "${android.androidsdk}/libexec/android-sdk";
    in {
      devShells.${system}.default = pkgs.mkShell {
        packages = [
          pkgs.jdk17
          pkgs.gradle
          android.androidsdk
          # native dependency build (native/build-deps.sh)
          pkgs.gnumake pkgs.m4 pkgs.curl pkgs.file
          # host-side bridge tests
          pkgs.cmake pkgs.pkg-config pkgs.libqalculate pkgs.gmp pkgs.mpfr pkgs.libxml2
        ];
        ANDROID_HOME = sdk;
        ANDROID_SDK_ROOT = sdk;
        JAVA_HOME = pkgs.jdk17.home;
        shellHook = ''
          export ANDROID_NDK_ROOT=$(echo ${sdk}/ndk/* | cut -d' ' -f1)
          # aapt2 from Maven is dynamically linked; use the SDK's patched one
          export GRADLE_OPTS="-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdk}/build-tools/35.0.0/aapt2"
        '';
      };
    };
}
