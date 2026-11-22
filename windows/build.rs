use std::{env, path::PathBuf, process::Command};

fn main() {
    println!("cargo:rerun-if-changed=assets/icon.rc");
    println!("cargo:rerun-if-changed=assets/icon.ico");
    println!("cargo:rerun-if-env-changed=WINDRES");
    if env::var("CARGO_CFG_TARGET_OS").as_deref() != Ok("windows") {
        return;
    }
    let output = PathBuf::from(env::var_os("OUT_DIR").expect("Cargo output directory"));
    let (mut compiler, resource) = if env::var("CARGO_CFG_TARGET_ENV").as_deref() == Ok("msvc") {
        let resource = output.join("app-icon.res");
        let mut compiler = Command::new("rc.exe");
        compiler
            .arg("/nologo")
            .arg("/fo")
            .arg(&resource)
            .arg("icon.rc");
        (compiler, resource)
    } else {
        let resource = output.join("app-icon.o");
        let mut compiler = Command::new(env::var_os("WINDRES").unwrap_or_else(|| "windres".into()));
        compiler
            .args(["-i", "icon.rc", "-O", "coff", "-o"])
            .arg(&resource);
        (compiler, resource)
    };
    let status = compiler
        .current_dir("assets")
        .status()
        .expect("Windows resource compiler");
    assert!(
        status.success(),
        "could not embed the Windows application icon"
    );
    println!("cargo:rustc-link-arg={}", resource.display());
}
