#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]
mod app;
mod core;
mod storage;
mod ui;

fn main() -> eframe::Result {
    let icon = eframe::icon_data::from_png_bytes(include_bytes!("../assets/icon.png"))
        .expect("bundled app icon");
    eframe::run_native(
        "CS2 Coinflip & Map Veto",
        eframe::NativeOptions {
            viewport: eframe::egui::ViewportBuilder::default()
                .with_icon(icon)
                .with_inner_size([1024.0, 720.0])
                .with_min_inner_size([370.0, 480.0]),
            ..Default::default()
        },
        Box::new(|cc| Ok(Box::new(app::Coinflip::new(cc)))),
    )
}
