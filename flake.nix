{
  inputs = {
    naersk.url = "github:nix-community/naersk/master";
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
    utils.url = "github:numtide/flake-utils";
    rust.url = "github:oxalica/rust-overlay";
  };

  outputs =
    {
      self,
      nixpkgs,
      utils,
      naersk,
      rust,
    }:
    utils.lib.eachDefaultSystem (
      system:
      let
        naersk-lib = pkgs.callPackage naersk { };
        pkgs = import nixpkgs {
          inherit system;
          config = {
            allowUnfree = true;
            android_sdk.accept_license = true;
          };
          overlays = [ rust.overlays.default ];
        };
      in
      {
        defaultPackage = naersk-lib.buildPackage ./.;
        devShell =
          with pkgs;
          mkShell {
            buildInputs = [
              (rust-bin.selectLatestNightlyWith (
                toolchain:
                toolchain.default.override {
                  extensions = [ ];
                  targets = [
                    "aarch64-linux-android"
                    "armv7-linux-androideabi"
                  ];
                }
              ))
              gradle_9
              cargo
              rustc
              rustfmt
              pre-commit
              rustPackages.clippy
              rust-analyzer
              android-studio
              android-tools
            ];
            shellHook = ''
              unset CFLAGS
              unset CXXFLAGS
            '';
            RUST_SRC_PATH = rustPlatform.rustLibSrc;
          };
      }
    );
}
