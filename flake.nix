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
        pkgs = nixpkgs.legacyPackages.${system}.extend rust.overlays.default;
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
              gradle
              cargo
              rustc
              rustfmt
              pre-commit
              rustPackages.clippy
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
