use crate::{
    core::{self, Match},
    storage, ui as theme,
};
use eframe::egui::*;
use std::time::{Duration, Instant};

enum Prompt {
    Resume,
    Reset,
    Map(usize),
}

pub struct Coinflip {
    artwork: theme::Artwork,
    reduced_motion: bool,
    game: Match,
    saved: Vec<u8>,
    error: Option<String>,
    animation: Option<Instant>,
    prompt: Option<Prompt>,
    settings: bool,
    pool: usize,
    copied: bool,
}

impl Coinflip {
    pub fn new(cc: &eframe::CreationContext<'_>) -> Self {
        theme::install(&cc.egui_ctx);
        let (game, error) = match storage::load() {
            Ok(game) => (game, None),
            Err(_) => (
                Match::default(),
                Some("Не удалось восстановить сохранение".into()),
            ),
        };
        cc.egui_ctx.set_zoom_factor(game.settings.scale);
        Self {
            artwork: theme::Artwork::load(&cc.egui_ctx),
            reduced_motion: theme::reduced_motion(),
            saved: serde_json::to_vec(&game).unwrap(),
            prompt: (game.winner.is_some() && !game.complete()).then_some(Prompt::Resume),
            pool: game.settings.active,
            game,
            error,
            animation: None,
            settings: false,
            copied: false,
        }
    }

    fn flip(&mut self) {
        self.error = self.game.error().map(str::to_owned);
        if self.error.is_some() {
            return;
        }
        match core::flip_side() {
            Ok(side) => {
                self.game.flip(side);
                self.animation = Some(Instant::now());
            }
            Err(_) => self.error = Some("Системный источник случайности недоступен".into()),
        }
    }

    fn coinflip(&mut self, ui: &mut Ui, progress: Option<f32>) {
        let compact = self.game.first.is_some() && progress.is_none();
        theme::panel(ui, |ui| {
            if compact {
                ui.horizontal(|ui| {
                    self.artwork.coin(
                        ui,
                        42.0,
                        self.game.winner,
                        None,
                        self.game.settings.effects,
                        self.game.settings.reduced || self.reduced_motion,
                    );
                    ui.vertical(|ui| {
                        theme::label(ui, "ПОБЕДИТЕЛЬ ЖЕРЕБЬЁВКИ", theme::SUCCESS);
                        ui.label(
                            RichText::new(&self.game.teams[self.game.winner.unwrap()])
                                .size(22.0)
                                .color(theme::AMBER),
                        );
                    });
                });
                if ui.button("Сброс").clicked() {
                    self.prompt = Some(Prompt::Reset);
                }
                return;
            }
            if self.game.winner.is_none() && ui.available_width() >= 620.0 {
                ui.columns(2, |columns| {
                    theme::heading(&mut columns[0], "Жеребьёвка");
                    self.team_field(&mut columns[0], 0);
                    self.team_field(&mut columns[0], 1);
                    self.coin_stage(&mut columns[1], progress);
                });
            } else {
                if self.game.winner.is_none() {
                    theme::heading(ui, "Жеребьёвка");
                    self.team_field(ui, 0);
                    self.team_field(ui, 1);
                }
                self.coin_stage(ui, progress);
            }
        });
    }

    fn coin_stage(&mut self, ui: &mut Ui, progress: Option<f32>) {
        ui.vertical_centered(|ui| {
            self.artwork.coin(
                ui,
                232.0_f32.min(ui.available_width()),
                self.game.winner,
                progress,
                self.game.settings.effects,
                self.game.settings.reduced || self.reduced_motion,
            );
            match (self.game.winner, progress) {
                (_, Some(_)) => {
                    ui.add_enabled(false, Button::new("Жеребьёвка…"));
                }
                (None, None) => {
                    if theme::primary(ui, "Подбросить").clicked() {
                        self.flip();
                    }
                }
                (Some(winner), None) => {
                    let side = if winner == 0 {
                        "Орёл"
                    } else {
                        "Решка"
                    };
                    ui.label(
                        RichText::new(format!("{side} · {}", self.game.teams[winner]))
                            .size(22.0)
                            .size(22.0)
                            .color(theme::AMBER),
                    );
                    if theme::primary(ui, "Начать первым").clicked() {
                        self.game.start(false);
                    }
                    if ui.button("Передать первый ход").clicked() {
                        self.game.start(true);
                    }
                    if ui.button("Сброс").clicked() {
                        self.prompt = Some(Prompt::Reset);
                    }
                }
            }
        });
    }

    fn team_field(&mut self, ui: &mut Ui, index: usize) {
        theme::label(
            ui,
            if index == 0 {
                "●  ОРЁЛ"
            } else {
                "◇  РЕШКА"
            },
            theme::MUTED,
        );
        if ui
            .add_sized(
                [ui.available_width(), 44.0],
                TextEdit::singleline(&mut self.game.teams[index])
                    .hint_text("Название команды")
                    .vertical_align(Align::Center)
                    .margin(Margin::symmetric(12, 7))
                    .desired_width(f32::INFINITY),
            )
            .changed()
        {
            self.error = None;
        }
    }

    fn veto(&mut self, ui: &mut Ui) {
        theme::heading(
            ui,
            if self.game.complete() {
                "Результат матча"
            } else {
                "Map Veto"
            },
        );
        let decider = self.game.decider();
        if let Some(index) = decider {
            theme::status_panel(
                ui,
                &self.game.format,
                &self.game.teams.join("  ·  "),
                &format!(
                    "Победитель жеребьёвки · {}",
                    self.game.teams[self.game.winner.unwrap()]
                ),
            );
            let picks: Vec<_> = self.game.picks().collect();
            let columns = if ui.available_width() >= 620.0 { 2 } else { 1 };
            for (row, picks) in picks.chunks(columns).enumerate() {
                ui.columns(columns, |cells| {
                    for (column, (cell, &(step, map))) in cells.iter_mut().zip(picks).enumerate() {
                        theme::series_map(
                            cell,
                            row * columns + column + 1,
                            &self.game.maps()[map],
                            &self.game.teams[self.game.actor(step).unwrap()],
                            false,
                        );
                    }
                });
            }
            theme::series_map(
                ui,
                picks.len() + 1,
                &self.game.maps()[index],
                "Решающая карта",
                true,
            );
            let result = self.game.result(true);
            self.history(ui);
            ui.horizontal_wrapped(|ui| {
                if theme::primary(ui, "Скопировать результат").clicked() {
                    ui.ctx().copy_text(result.clone());
                    self.copied = true;
                }
                if ui.button("Сохранить PNG").clicked() {
                    match storage::export_result(&result) {
                        Ok(path) => {
                            self.copied = false;
                            self.error = None;
                            let _ = std::process::Command::new("explorer.exe")
                                .arg("/select,")
                                .arg(path)
                                .spawn();
                        }
                        Err(_) => self.error = Some("Не удалось сохранить PNG".into()),
                    }
                }
                if ui.button("Новый матч").clicked() {
                    self.prompt = Some(Prompt::Reset);
                }
                if self.copied {
                    ui.label("Скопировано");
                }
                if ui.button("Отменить ход").clicked() {
                    self.game.undo();
                    self.copied = false;
                }
            });
            return;
        } else {
            let step = self.game.moves.len();
            let actor = self.game.actor(step).unwrap();
            let action = self.game.action(step).unwrap();
            let detail = if action == "PICK" {
                format!("Выберите карту {}", self.game.picks().count() + 1)
            } else {
                "Исключите карту из пула".into()
            };
            theme::status_panel(
                ui,
                &format!("{}  ·  Ход {} / 6", action, step + 1),
                &self.game.teams[actor],
                &detail,
            );
        }
        let columns = if ui.available_width() > 730.0 { 4 } else { 2 };
        let mut selected = None;
        for row in (0..self.game.maps().len())
            .collect::<Vec<_>>()
            .chunks(columns)
        {
            ui.columns(columns, |cells| {
                for (cell, &index) in cells.iter_mut().zip(row) {
                    let status = self
                        .game
                        .moves
                        .iter()
                        .position(|&i| i == index)
                        .and_then(|step| self.game.action(step));
                    let selected_for_confirmation =
                        matches!(self.prompt, Some(Prompt::Map(map)) if map == index);
                    if theme::map_card(
                        cell,
                        &self.game.maps()[index],
                        status,
                        selected_for_confirmation,
                        !self.reduced_motion && !self.game.settings.reduced,
                    )
                    .clicked()
                    {
                        selected = Some(index);
                    }
                }
            });
        }
        if let Some(index) = selected {
            if self.game.settings.confirm {
                self.prompt = Some(Prompt::Map(index));
            } else {
                self.game.select(index);
            }
        }
        if !self.game.complete() && !self.game.moves.is_empty() {
            self.history(ui);
        }
        if ui
            .add_enabled(!self.game.moves.is_empty(), Button::new("Отменить ход"))
            .clicked()
        {
            self.game.undo();
            self.copied = false;
        }
    }

    fn history(&self, ui: &mut Ui) {
        theme::panel(ui, |ui| {
            theme::label(ui, "ИСТОРИЯ VETO", theme::MUTED);
            for (step, &map) in self.game.moves.iter().enumerate() {
                theme::history_row(
                    ui,
                    step,
                    self.game.action(step).unwrap(),
                    &self.game.maps()[map],
                    &self.game.teams[self.game.actor(step).unwrap()],
                );
            }
        });
    }

    fn settings(&mut self, ui: &mut Ui) {
        let settings = &mut self.game.settings;
        let editable = self.game.winner.is_none();
        let motion = !self.reduced_motion && !settings.reduced;
        theme::panel(ui, |ui| {
            theme::heading(ui, "Карты");
            if !editable {
                ui.label("Пул можно изменить после сброса матча.");
            }
            ComboBox::from_id_salt("map pool")
                .selected_text(&settings.pools[self.pool].name)
                .width(260.0_f32.min(ui.available_width()))
                .show_ui(ui, |ui| {
                    for (i, pool) in settings.pools.iter().enumerate() {
                        let label = if i == settings.active {
                            format!("{} · активный", pool.name)
                        } else {
                            pool.name.clone()
                        };
                        ui.selectable_value(&mut self.pool, i, label);
                    }
                });
            if ui
                .add_enabled(editable, Button::new("Создать пул"))
                .clicked()
            {
                let mut pool = core::standard_pool();
                pool.name = format!("Пул {}", settings.pools.len() + 1);
                settings.pools.push(pool);
                self.pool = settings.pools.len() - 1;
            }
            let pool = &mut settings.pools[self.pool];
            ui.add_enabled_ui(editable, |ui| {
                ui.add(TextEdit::singleline(&mut pool.name).hint_text("Название пула"));
                let len = pool.maps.len();
                let mut remove = None;
                let mut swap = None;
                for (index, map) in pool.maps.iter_mut().enumerate() {
                    ui.push_id(index, |ui| {
                        ui.horizontal(|ui| {
                            ui.add(
                                TextEdit::singleline(map)
                                    .desired_width((ui.available_width() - 142.0).min(390.0)),
                            );
                            if ui.add_enabled(index > 0, Button::new("↑")).clicked() {
                                swap = Some((index, index - 1));
                            }
                            if ui.add_enabled(index + 1 < len, Button::new("↓")).clicked() {
                                swap = Some((index, index + 1));
                            }
                            if ui.button("×").clicked() {
                                remove = Some(index);
                            }
                        });
                    });
                }
                if let Some((a, b)) = swap {
                    pool.maps.swap(a, b);
                }
                if let Some(index) = remove {
                    pool.maps.remove(index);
                }
                ui.horizontal_wrapped(|ui| {
                    if ui.button("Добавить карту").clicked() {
                        pool.maps.push(String::new());
                    }
                    if ui.button("Восстановить стандартные карты").clicked()
                    {
                        pool.maps.clone_from(&core::RULES.pool);
                    }
                    if ui
                        .add_enabled(
                            self.pool != settings.active && core::valid_pool(&pool.maps),
                            Button::new("Сделать активным"),
                        )
                        .clicked()
                    {
                        settings.active = self.pool;
                    }
                });
            });
            if !core::valid_pool(&pool.maps) {
                ui.label("Для активации нужны семь уникальных карт.");
            }
        });
        theme::panel(ui, |ui| {
            theme::heading(ui, "Монетка");
            ui.add(
                Slider::new(&mut settings.duration, 500..=4000)
                    .step_by(100.0)
                    .suffix(" мс")
                    .text("Длительность"),
            );
            theme::toggle(ui, "Звуковые эффекты", &mut settings.sound, motion);
            theme::toggle(ui, "Упрощённая анимация", &mut settings.reduced, motion);
            theme::toggle(
                ui,
                "Победитель выбирает первый ход",
                &mut settings.choose,
                motion,
            );
        });
        theme::panel(ui, |ui| {
            theme::heading(ui, "Интерфейс");
            ui.add(
                Slider::new(&mut settings.scale, 0.85..=1.4)
                    .step_by(0.05)
                    .text("Масштаб"),
            );
            theme::toggle(ui, "Декоративные эффекты", &mut settings.effects, motion);
            theme::toggle(
                ui,
                "Подтверждать выбор карты",
                &mut settings.confirm,
                motion,
            );
        });
        theme::heading(ui, concat!("CS2 Coinflip ", env!("CARGO_PKG_VERSION")));
        ui.label("egui, eframe, serde, getrandom · MIT / Apache 2.0\nInter · SIL OFL 1.1");
    }
}

impl eframe::App for Coinflip {
    fn update(&mut self, ctx: &Context, _: &mut eframe::Frame) {
        let progress = self
            .animation
            .map(|time| time.elapsed().as_secs_f32() * 1000.0 / self.game.settings.duration as f32);
        let progress = progress.filter(|&p| p < 1.0);
        if progress.is_some() {
            ctx.request_repaint_after(Duration::from_millis(16));
        } else if self.animation.take().is_some() && self.game.settings.sound {
            #[link(name = "user32")]
            extern "system" {
                fn MessageBeep(kind: u32) -> i32;
            }
            // MessageBeep uses the current Windows sound scheme and returns immediately.
            unsafe {
                MessageBeep(0);
            }
        }
        TopBottomPanel::top("header")
            .frame(Frame::new().fill(theme::ELEVATED).inner_margin(18))
            .show(ctx, |ui| {
                ui.horizontal(|ui| {
                    if self.settings {
                        theme::heading(ui, "Настройки");
                    } else {
                        self.artwork.logo(ui);
                    }
                    ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                        if ui
                            .add_enabled(
                                progress.is_none() && self.prompt.is_none(),
                                Button::new(if self.settings { "Готово" } else { "⚙" }),
                            )
                            .on_hover_text("Настройки")
                            .clicked()
                        {
                            self.settings = !self.settings;
                        }
                    });
                });
            });
        if !self.settings {
            TopBottomPanel::bottom("format")
                .frame(Frame::new().fill(theme::NAV).inner_margin(12))
                .show(ctx, |ui| {
                    Frame::new()
                        .fill(theme::ELEVATED)
                        .corner_radius(14)
                        .stroke(Stroke::new(1.0_f32, theme::DIVIDER))
                        .inner_margin(5)
                        .show(ui, |ui| {
                            ui.columns(3, |columns| {
                                for (column, format) in
                                    columns.iter_mut().zip(["BO1", "BO3", "BO5"])
                                {
                                    let selected = self.game.format == format;
                                    let button =
                                        Button::new(RichText::new(format).color(if selected {
                                            theme::AMBER
                                        } else {
                                            theme::MUTED
                                        }))
                                        .fill(if selected {
                                            theme::RAISED
                                        } else {
                                            theme::ELEVATED
                                        })
                                        .stroke(Stroke::new(
                                            1.0_f32,
                                            if selected {
                                                theme::AMBER
                                            } else {
                                                theme::ELEVATED
                                            },
                                        ))
                                        .corner_radius(10)
                                        .min_size(vec2(column.available_width(), 42.0));
                                    if column
                                        .add_enabled(self.game.winner.is_none(), button)
                                        .clicked()
                                    {
                                        self.game.format = format.into();
                                    }
                                }
                            })
                        });
                });
        }
        CentralPanel::default()
            .frame(Frame::new().fill(theme::NIGHT).inner_margin(16))
            .show(ctx, |ui| {
                theme::background(ui);
                ScrollArea::vertical()
                    .auto_shrink([false, false])
                    .show(ui, |ui| {
                        let width = ui.available_width().min(1120.0);
                        let inset = (ui.available_width() - width) * 0.5;
                        ui.horizontal_top(|ui| {
                            ui.spacing_mut().item_spacing.x = 0.0;
                            ui.add_space(inset);
                            ui.vertical(|ui| {
                                ui.spacing_mut().item_spacing.x = 10.0;
                                ui.set_width(width);
                                let stage = if self.settings {
                                    100
                                } else if self.animation.is_some() {
                                    90
                                } else if self.game.first.is_some() {
                                    10 + self.game.moves.len() as u16
                                } else if self.game.winner.is_some() {
                                    1
                                } else {
                                    0
                                };
                                theme::reveal(
                                    ui,
                                    stage,
                                    !self.reduced_motion && !self.game.settings.reduced,
                                );
                                if self.settings {
                                    self.settings(ui);
                                } else if !matches!(self.prompt, Some(Prompt::Resume)) {
                                    if !self.game.complete() {
                                        self.coinflip(ui, progress);
                                    }
                                    if let Some(error) = &self.error {
                                        ui.colored_label(theme::ERROR, error);
                                    }
                                    if self.animation.is_none() && self.game.first.is_some() {
                                        self.veto(ui);
                                    }
                                }
                            });
                        });
                    });
            });
        if let Some(prompt) = &self.prompt {
            let choice = match prompt {
                Prompt::Resume => theme::dialog(
                    ctx,
                    "Продолжить матч?",
                    &self.game.teams.join(" — "),
                    "Продолжить",
                    "Новый матч",
                ),
                Prompt::Reset => theme::dialog(
                    ctx,
                    "Сбросить матч?",
                    "Жеребьёвка и ходы будут удалены.",
                    "Сбросить",
                    "Отмена",
                ),
                Prompt::Map(index) => theme::dialog(
                    ctx,
                    &format!(
                        "{} · {}",
                        self.game.action(self.game.moves.len()).unwrap(),
                        self.game.maps()[*index]
                    ),
                    &self.game.teams[self.game.actor(self.game.moves.len()).unwrap()],
                    "Подтвердить",
                    "Отмена",
                ),
            };
            if let Some(yes) = choice {
                match self.prompt.take().unwrap() {
                    Prompt::Resume if !yes => self.game.reset(),
                    Prompt::Reset if yes => {
                        self.game.reset();
                        self.copied = false;
                        self.error = None;
                    }
                    Prompt::Map(index) if yes => self.game.select(index),
                    _ => {}
                }
            }
        }
        ctx.set_zoom_factor(self.game.settings.scale);
        let bytes = serde_json::to_vec(&self.game).unwrap();
        if bytes != self.saved {
            match storage::save(&self.game) {
                Ok(()) => self.saved = bytes,
                Err(_) => {
                    self.error = Some("Не удалось сохранить состояние".into());
                    ctx.request_repaint_after(Duration::from_secs(2));
                }
            }
        }
    }
}
