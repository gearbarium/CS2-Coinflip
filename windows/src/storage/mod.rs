use crate::core::Match;
use std::{fs, io, path::PathBuf};

fn path() -> PathBuf {
    std::env::var_os("LOCALAPPDATA")
        .map(PathBuf::from)
        .unwrap_or_else(std::env::temp_dir)
        .join("CS2 Coinflip")
        .join("match.json")
}

pub fn load() -> io::Result<Match> {
    let bytes = match fs::read(path()) {
        Ok(bytes) => bytes,
        Err(err) if err.kind() == io::ErrorKind::NotFound => return Ok(Match::default()),
        Err(err) => return Err(err),
    };
    let state: Match = serde_json::from_slice(&bytes)?;
    if state.is_valid() {
        Ok(state)
    } else {
        Err(io::Error::new(
            io::ErrorKind::InvalidData,
            "unsupported match state",
        ))
    }
}

pub fn save(state: &Match) -> io::Result<()> {
    use std::io::Write;
    let target = path();
    fs::create_dir_all(target.parent().unwrap())?;
    let pending = target.with_file_name(format!("match.{}.tmp", std::process::id()));
    let mut file = fs::File::create(&pending)?;
    file.write_all(&serde_json::to_vec(state)?)?;
    file.sync_all()?;
    drop(file);
    fs::rename(pending, target)
}

pub fn export_result(text: &str) -> Result<PathBuf, Box<dyn std::error::Error>> {
    use ab_glyph::{point, Font, FontRef, PxScale, ScaleFont};
    use image::{Rgb, RgbImage};

    let font = FontRef::try_from_slice(include_bytes!("../../assets/inter.ttf"))?;
    let scale = PxScale::from(25.0);
    let scaled = font.as_scaled(scale);
    let mut lines = vec![String::new()];
    let mut width = 0.0;
    for ch in text.chars() {
        let advance = scaled.h_advance(font.glyph_id(ch));
        if ch == '\n' || width + advance > 840.0 {
            lines.push(String::new());
            width = 0.0;
        }
        if ch != '\n' {
            lines.last_mut().unwrap().push(ch);
            width += advance;
        }
    }
    let mut image = RgbImage::from_pixel(920, 80 + lines.len() as u32 * 36, Rgb([10, 21, 32]));
    for (row, line) in lines.iter().enumerate() {
        let color = if row == 0 {
            [255, 176, 46]
        } else {
            [241, 247, 251]
        };
        let mut x = 40.0;
        for ch in line.chars() {
            let id = font.glyph_id(ch);
            if let Some(glyph) = font.outline_glyph(
                id.with_scale_and_position(scale, point(x, 56.0 + row as f32 * 36.0)),
            ) {
                let bounds = glyph.px_bounds();
                glyph.draw(|dx, dy, coverage| {
                    let x = bounds.min.x as i32 + dx as i32;
                    let y = bounds.min.y as i32 + dy as i32;
                    if x >= 0 && y >= 0 && x < image.width() as i32 && y < image.height() as i32 {
                        let pixel = image.get_pixel_mut(x as u32, y as u32);
                        for (value, target) in pixel.0.iter_mut().zip(color) {
                            *value =
                                (*value as f32 * (1.0 - coverage) + target as f32 * coverage) as u8;
                        }
                    }
                });
            }
            x += scaled.h_advance(id);
        }
    }
    let directory = path().with_file_name("Результаты");
    fs::create_dir_all(&directory)?;
    let stamp = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)?
        .as_millis();
    let target = directory.join(format!("CS2 Coinflip {stamp}.png"));
    image.save(&target)?;
    Ok(target)
}
