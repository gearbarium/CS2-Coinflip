use eframe::egui::{self, *};

pub const NIGHT: Color32 = Color32::from_rgb(5, 11, 18);
pub const PANEL: Color32 = Color32::from_rgb(10, 21, 32);
pub const RAISED: Color32 = Color32::from_rgb(17, 33, 46);
pub const AMBER: Color32 = Color32::from_rgb(255, 176, 46);
pub const SILVER: Color32 = Color32::from_rgb(241, 247, 251);
pub const MUTED: Color32 = Color32::from_rgb(156, 174, 187);
pub const LINE: Color32 = Color32::from_rgb(82, 106, 121);
pub const NAV: Color32 = Color32::from_rgb(7, 18, 27);
pub const HIGHLIGHT: Color32 = Color32::from_rgb(255, 209, 132);
pub const PRESSED: Color32 = Color32::from_rgb(216, 139, 24);
pub const DIVIDER: Color32 = Color32::from_rgb(36, 55, 68);
pub const ELEVATED: Color32 = Color32::from_rgb(8, 17, 27);
pub const HOVER: Color32 = Color32::from_rgb(23, 44, 58);
pub const ACCENT_HOVER: Color32 = Color32::from_rgb(255, 193, 91);
pub const SUCCESS: Color32 = Color32::from_rgb(145, 204, 174);
pub const ERROR: Color32 = Color32::from_rgb(235, 154, 150);

pub fn install(ctx: &Context) {
    let mut fonts = FontDefinitions::default();
    fonts.font_data.insert(
        "Inter".into(),
        FontData::from_static(include_bytes!("../../assets/inter.ttf")).into(),
    );
    fonts
        .families
        .entry(FontFamily::Proportional)
        .or_default()
        .insert(0, "Inter".into());
    ctx.set_fonts(fonts);
    let mut style = Style {
        visuals: Visuals::dark(),
        ..Default::default()
    };
    style.spacing.item_spacing = vec2(10.0, 10.0);
    style.spacing.interact_size.y = 40.0;
    style.spacing.button_padding = vec2(14.0, 8.0);
    style
        .text_styles
        .insert(TextStyle::Body, FontId::proportional(16.0));
    style
        .text_styles
        .insert(TextStyle::Button, FontId::proportional(15.0));
    style.visuals.panel_fill = NIGHT;
    style.visuals.window_fill = PANEL;
    style.visuals.window_corner_radius = CornerRadius::same(16);
    style.visuals.extreme_bg_color = NIGHT;
    style.visuals.override_text_color = Some(SILVER);
    style.visuals.selection.bg_fill = RAISED;
    style.visuals.selection.stroke = Stroke::new(1.0_f32, AMBER);
    style.visuals.widgets.inactive.weak_bg_fill = RAISED;
    style.visuals.widgets.inactive.bg_stroke = Stroke::new(1.0_f32, DIVIDER);
    style.visuals.widgets.noninteractive.bg_stroke = Stroke::new(1.0_f32, LINE);
    style.visuals.widgets.hovered.weak_bg_fill = HOVER;
    style.visuals.widgets.hovered.bg_stroke = Stroke::new(1.0_f32, HIGHLIGHT);
    style.visuals.widgets.active.bg_stroke = Stroke::new(2.0_f32, HIGHLIGHT);
    let widgets = &mut style.visuals.widgets;
    for widget in [
        &mut widgets.noninteractive,
        &mut widgets.inactive,
        &mut widgets.hovered,
        &mut widgets.active,
        &mut widgets.open,
    ] {
        widget.corner_radius = CornerRadius::same(10);
        widget.expansion = 0.0;
    }
    ctx.set_style(style);
}

pub fn heading(ui: &mut Ui, text: &str) {
    ui.label(RichText::new(text).size(26.0).strong());
}

pub fn primary(ui: &mut Ui, text: &str) -> Response {
    ui.scope(|ui| {
        let widgets = &mut ui.visuals_mut().widgets;
        widgets.inactive.weak_bg_fill = AMBER;
        widgets.hovered.weak_bg_fill = ACCENT_HOVER;
        widgets.active.weak_bg_fill = PRESSED;
        ui.add(Button::new(RichText::new(text).color(NIGHT).strong()).min_size(vec2(146.0, 46.0)))
    })
    .inner
}

pub fn label(ui: &mut Ui, value: &str, color: Color32) {
    ui.label(RichText::new(value).size(12.0).color(color));
}

pub fn status_panel(ui: &mut Ui, caption: &str, title: &str, detail: &str) {
    panel(ui, |ui| {
        label(ui, caption, AMBER);
        ui.label(RichText::new(title).size(24.0));
        ui.label(RichText::new(detail).color(MUTED));
    });
}

pub fn history_row(ui: &mut Ui, step: usize, action: &str, map: &str, actor: &str) {
    ui.horizontal_wrapped(|ui| {
        label(ui, &format!("{:02}", step + 1), MUTED);
        label(ui, action, if action == "PICK" { AMBER } else { ERROR });
        ui.label(map);
        ui.label(RichText::new(actor).color(MUTED));
    });
}

pub fn toggle(ui: &mut Ui, label: &str, value: &mut bool, motion: bool) {
    ui.horizontal(|ui| {
        let (rect, mut response) = ui.allocate_exact_size(vec2(48.0, 32.0), Sense::click());
        if response.clicked() {
            *value = !*value;
            response.mark_changed();
        }
        response.widget_info(|| WidgetInfo::selected(WidgetType::Checkbox, true, *value, label));
        let position =
            ui.ctx()
                .animate_bool_with_time(response.id, *value, if motion { 0.12 } else { 0.0 });
        let track = Rect::from_center_size(rect.center(), vec2(42.0, 24.0));
        let border = if response.has_focus() || response.hovered() {
            HIGHLIGHT
        } else {
            LINE
        };
        ui.painter().rect(
            track,
            12.0,
            RAISED.lerp_to_gamma(AMBER, position),
            Stroke::new(1.0_f32, border),
            StrokeKind::Inside,
        );
        ui.painter().circle_filled(
            pos2(
                lerp(track.left() + 12.0..=track.right() - 12.0, position),
                track.center().y,
            ),
            7.0,
            SILVER.lerp_to_gamma(NIGHT, position),
        );
        ui.label(label);
    });
}

pub fn map_card(
    ui: &mut Ui,
    name: &str,
    status: Option<&str>,
    selected: bool,
    motion: bool,
) -> Response {
    let label = status.unwrap_or("Доступна");
    let tint = match status {
        Some("PICK") => AMBER,
        Some(_) => ERROR,
        None => MUTED,
    };
    let mut text = text::LayoutJob::default();
    text.append(
        name,
        0.0,
        TextFormat {
            font_id: FontId::proportional(18.0),
            color: SILVER,
            ..Default::default()
        },
    );
    text.append(
        &format!("\n{label}"),
        0.0,
        TextFormat {
            font_id: FontId::proportional(12.0),
            color: tint,
            ..Default::default()
        },
    );
    let response = ui.allocate_response(
        vec2(ui.available_width(), 76.0),
        if status.is_none() {
            Sense::click()
        } else {
            Sense::hover()
        },
    );
    response.widget_info(|| {
        WidgetInfo::labeled(
            WidgetType::Button,
            status.is_none(),
            format!("{name} · {label}"),
        )
    });
    let hover = ui.ctx().animate_bool_with_time(
        response.id,
        status.is_none() && response.hovered(),
        if motion { 0.12 } else { 0.0 },
    );
    let fill = if selected || status == Some("PICK") {
        Color32::from_rgb(48, 33, 22)
    } else {
        RAISED.lerp_to_gamma(HOVER, hover)
    };
    let border = if selected || response.has_focus() {
        AMBER
    } else {
        DIVIDER.lerp_to_gamma(LINE, hover)
    };
    let rect = response
        .rect
        .shrink(if response.is_pointer_button_down_on() {
            1.0
        } else {
            0.0
        });
    ui.painter().rect(
        rect,
        14.0,
        fill,
        Stroke::new(1.0_f32, border),
        StrokeKind::Inside,
    );
    let galley = ui.painter().layout_job(text);
    let clipped = ui.painter().with_clip_rect(rect.shrink(8.0));
    clipped.galley(
        rect.left_center() + vec2(14.0, -galley.size().y * 0.5),
        galley,
        SILVER,
    );
    response
}

pub fn panel(ui: &mut Ui, content: impl FnOnce(&mut Ui)) {
    Frame::new()
        .fill(PANEL)
        .corner_radius(16)
        .stroke(Stroke::new(1.0_f32, DIVIDER))
        .inner_margin(16)
        .show(ui, |ui| {
            ui.set_min_width(ui.available_width());
            content(ui);
        });
}

pub fn series_map(ui: &mut Ui, number: usize, name: &str, detail: &str, decisive: bool) {
    let accent = if decisive { HIGHLIGHT } else { AMBER };
    Frame::new()
        .fill(if decisive {
            Color32::from_rgb(48, 33, 22)
        } else {
            RAISED
        })
        .corner_radius(16)
        .stroke(Stroke::new(1.0_f32, if decisive { AMBER } else { LINE }))
        .inner_margin(16)
        .show(ui, |ui| {
            ui.set_min_width(ui.available_width());
            ui.spacing_mut().item_spacing.y = 5.0;
            ui.label(
                RichText::new(format!("Карта {number}"))
                    .size(14.0)
                    .color(accent),
            );
            ui.label(RichText::new(name).size(24.0).strong());
            ui.label(RichText::new(detail).color(if decisive { HIGHLIGHT } else { MUTED }));
        });
}

pub struct Artwork {
    logo: TextureHandle,
    faces: [TextureHandle; 2],
}

impl Artwork {
    pub fn load(ctx: &Context) -> Self {
        let texture = |name, bytes: &[u8]| {
            let image = image::load_from_memory_with_format(bytes, image::ImageFormat::Png)
                .expect("bundled PNG")
                .into_rgba8();
            ctx.load_texture(
                name,
                ColorImage::from_rgba_unmultiplied(
                    [image.width() as usize, image.height() as usize],
                    image.as_raw(),
                ),
                TextureOptions::LINEAR.with_mipmap_mode(Some(TextureFilter::Linear)),
            )
        };
        Self {
            logo: texture("logo", include_bytes!("../../../logo.png")),
            faces: [
                texture("heads", include_bytes!("../../../coinside1.png")),
                texture("tails", include_bytes!("../../../coinside2.png")),
            ],
        }
    }

    pub fn logo(&self, ui: &mut Ui) {
        let uv = Rect::from_min_max(
            pos2(96.0 / 2172.0, 180.0 / 724.0),
            pos2(2076.0 / 2172.0, 552.0 / 724.0),
        );
        ui.add(
            Image::new((self.logo.id(), vec2(188.0, 188.0 * 372.0 / 1980.0)))
                .uv(uv)
                .alt_text("CS2 Coinflip"),
        );
        ui.label(RichText::new("by gearbarium").size(12.0).color(MUTED));
    }

    pub fn coin(
        &self,
        ui: &mut Ui,
        size: f32,
        side: Option<usize>,
        progress: Option<f32>,
        effects: bool,
        reduced: bool,
    ) {
        let (rect, _) = ui.allocate_exact_size(Vec2::splat(size), Sense::hover());
        let center = rect.center();
        let lift = if reduced {
            0.0
        } else {
            progress.map_or(0.0, |t| (t * std::f32::consts::PI).sin() * size * 0.09)
        };
        let face_center = center - vec2(0.0, lift);
        let diameter = if size > 100.0 { size * 0.87 } else { size };
        let p = ui.painter();
        let target = if side == Some(1) { -1.0 } else { 1.0 };
        let turn = progress.map_or(target, |t| {
            if reduced {
                return 1.0;
            }
            let turns = if target < 0.0 { 9.0 } else { 8.0 };
            (t * t * (3.0 - 2.0 * t) * turns * std::f32::consts::PI).cos()
        });
        // Rotation about X keeps the horizontal diameter fixed.
        let height = turn.abs().max(0.02);
        let edge = (1.0 - turn * turn).max(0.0).sqrt();
        if effects && size > 50.0 {
            for i in (1..=12).rev() {
                p.circle_filled(
                    center,
                    size * (0.36 + i as f32 * 0.010),
                    Color32::from_rgba_unmultiplied(143, 95, 43, 3),
                );
            }
            p.circle_stroke(
                center,
                size * 0.455,
                Stroke::new(1.0_f32, AMBER.gamma_multiply(0.28)),
            );
            p.circle_stroke(
                center,
                size * 0.49,
                Stroke::new(1.0_f32, HIGHLIGHT.gamma_multiply(0.17)),
            );
        }
        if edge > 0.02 {
            p.rect_filled(
                Rect::from_center_size(face_center, vec2(diameter * 0.86, diameter * edge * 0.055)),
                3.0,
                Color32::from_rgb(184, 138, 82),
            );
        }
        let face = &self.faces[usize::from(turn < 0.0)];
        p.image(
            face.id(),
            Rect::from_center_size(face_center, vec2(diameter, diameter * height)),
            Rect::from_min_max(Pos2::ZERO, pos2(1.0, 1.0)),
            Color32::WHITE,
        );
    }
}

pub fn reduced_motion() -> bool {
    #[link(name = "user32")]
    extern "system" {
        fn SystemParametersInfoW(action: u32, parameter: u32, value: *mut i32, flags: u32) -> i32;
    }
    let mut animation = 1;
    // SPI_GETCLIENTAREAANIMATION writes a Win32 BOOL.
    unsafe {
        SystemParametersInfoW(0x1042, 0, &mut animation, 0);
    }
    animation == 0
}

pub fn background(ui: &Ui) {
    let rect = ui.max_rect();
    let mut mesh = Mesh::default();
    let top = Color32::from_rgb(10, 27, 40);
    for (y, color) in [
        (rect.top(), top),
        (rect.center().y, NIGHT),
        (rect.bottom(), NIGHT),
    ] {
        mesh.colored_vertex(pos2(rect.left(), y), color);
        mesh.colored_vertex(pos2(rect.right(), y), color);
    }
    mesh.indices.extend([0, 1, 2, 1, 3, 2, 2, 3, 4, 3, 5, 4]);
    ui.painter().add(Shape::mesh(mesh));
}

pub fn reveal(ui: &mut Ui, state: u16, enabled: bool) {
    if !enabled {
        return;
    }
    let id = ui.id().with("panel transition");
    let time = ui.input(|input| input.time);
    let start = ui.data_mut(|data| {
        let previous = data.get_temp::<(u16, f64)>(id);
        let start = previous
            .filter(|(key, _)| *key == state)
            .map_or(time, |(_, start)| start);
        data.insert_temp(id, (state, start));
        start
    });
    let progress = ((time - start) / 0.18).clamp(0.0, 1.0) as f32;
    ui.multiply_opacity(0.78 + 0.22 * progress);
    if progress < 1.0 {
        ui.ctx().request_repaint();
    }
}

pub fn dialog(
    ctx: &egui::Context,
    title: &str,
    description: &str,
    yes: &str,
    no: &str,
) -> Option<bool> {
    let mut picked = None;
    Modal::new(Id::new("confirm"))
        .frame(
            Frame::new()
                .fill(RAISED)
                .inner_margin(22)
                .corner_radius(18)
                .stroke(Stroke::new(1.0_f32, LINE)),
        )
        .show(ctx, |ui| {
            ui.set_width(ui.ctx().screen_rect().width().clamp(250.0, 410.0) - 40.0);
            heading(ui, title);
            ui.label(description);
            ui.horizontal_wrapped(|ui| {
                if primary(ui, yes).clicked() {
                    picked = Some(true);
                }
                if ui.button(no).clicked() {
                    picked = Some(false);
                }
            });
        });
    picked
}
