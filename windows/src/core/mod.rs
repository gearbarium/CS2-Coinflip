use serde::{Deserialize, Serialize};
use std::{
    collections::{HashMap, HashSet},
    sync::LazyLock,
};

#[derive(Deserialize)]
pub struct Rules {
    pub pool: Vec<String>,
    pub formats: HashMap<String, Vec<String>>,
}
pub static RULES: LazyLock<Rules> = LazyLock::new(|| {
    serde_json::from_str(include_str!("../../../shared-spec/veto-rules.json"))
        .expect("bundled rules")
});

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct Pool {
    pub name: String,
    pub maps: Vec<String>,
}
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct Settings {
    pub pools: Vec<Pool>,
    pub active: usize,
    pub duration: u32,
    pub sound: bool,
    pub reduced: bool,
    pub choose: bool,
    pub scale: f32,
    pub effects: bool,
    pub confirm: bool,
}
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct Match {
    pub version: u32,
    pub teams: [String; 2],
    pub format: String,
    pub settings: Settings,
    pub winner: Option<usize>,
    pub first: Option<usize>,
    pub moves: Vec<usize>,
}

pub fn standard_pool() -> Pool {
    Pool {
        name: "Стандартный".into(),
        maps: RULES.pool.clone(),
    }
}
pub fn valid_pool(maps: &[String]) -> bool {
    maps.len() == 7
        && maps.iter().all(|s| !s.trim().is_empty())
        && maps
            .iter()
            .map(|s| s.trim().to_lowercase())
            .collect::<HashSet<_>>()
            .len()
            == 7
}
pub fn flip_side() -> Result<usize, getrandom::Error> {
    let mut byte = [0];
    getrandom::fill(&mut byte)?;
    Ok((byte[0] & 1) as usize)
}
impl Default for Match {
    fn default() -> Self {
        Self {
            version: 1,
            teams: Default::default(),
            format: "BO3".into(),
            settings: Settings {
                pools: vec![standard_pool()],
                active: 0,
                duration: 2200,
                sound: false,
                reduced: false,
                choose: true,
                scale: 1.0,
                effects: true,
                confirm: true,
            },
            winner: None,
            first: None,
            moves: Vec::new(),
        }
    }
}
impl Match {
    pub fn maps(&self) -> &[String] {
        &self.settings.pools[self.settings.active].maps
    }
    pub fn complete(&self) -> bool {
        self.moves.len() == 6
    }
    pub fn decider(&self) -> Option<usize> {
        self.complete()
            .then(|| (0..self.maps().len()).find(|i| !self.moves.contains(i)))
            .flatten()
    }
    pub fn actor(&self, step: usize) -> Option<usize> {
        self.first.map(|first| (first + step) % 2)
    }
    pub fn action(&self, step: usize) -> Option<&str> {
        RULES.formats[&self.format].get(step).map(String::as_str)
    }
    pub fn select(&mut self, index: usize) {
        if self.first.is_some()
            && !self.complete()
            && index < self.maps().len()
            && !self.moves.contains(&index)
        {
            self.moves.push(index);
        }
    }
    pub fn undo(&mut self) {
        self.moves.pop();
    }
    pub fn reset(&mut self) {
        self.winner = None;
        self.first = None;
        self.moves.clear();
    }
    pub fn start(&mut self, pass: bool) {
        if self.first.is_none() {
            self.first = self
                .winner
                .map(|winner| if pass { 1 - winner } else { winner });
        }
    }
    pub fn error(&self) -> Option<&'static str> {
        if self.teams.iter().any(|s| s.trim().is_empty()) {
            return Some("Введите названия обеих команд");
        }
        if self.teams[0].trim().to_lowercase() == self.teams[1].trim().to_lowercase() {
            return Some("Названия команд должны различаться");
        }
        if !valid_pool(self.maps()) {
            return Some("В активном пуле нужны семь уникальных карт");
        }
        None
    }
    pub fn flip(&mut self, side: usize) {
        if self.winner.is_none() && side < 2 && self.error().is_none() {
            self.teams = self.teams.each_ref().map(|s| s.trim().to_owned());
            self.winner = Some(side);
            if !self.settings.choose {
                self.start(false);
            }
        }
    }
    pub fn is_valid(&self) -> bool {
        self.version == 1
            && RULES.formats.contains_key(&self.format)
            && self.settings.active < self.settings.pools.len()
            && (500..=4000).contains(&self.settings.duration)
            && (0.85..=1.4).contains(&self.settings.scale)
            && self.winner.is_none_or(|i| i < 2 && self.error().is_none())
            && self.first.is_none_or(|i| i < 2 && self.winner.is_some())
            && (self.moves.is_empty() || self.first.is_some())
            && self.moves.len() <= 6
            && self.moves.iter().copied().collect::<HashSet<_>>().len() == self.moves.len()
            && self.moves.iter().all(|i| *i < self.maps().len())
    }
    pub fn picks(&self) -> impl Iterator<Item = (usize, usize)> + '_ {
        self.moves
            .iter()
            .copied()
            .enumerate()
            .filter(|(step, _)| self.action(*step) == Some("PICK"))
    }

    pub fn result(&self, include_series: bool) -> String {
        use std::fmt::Write as _;
        let mut value = format!(
            "{} — {} · {}\nПобедитель жеребьёвки: {}\n",
            self.teams[0],
            self.teams[1],
            self.format,
            self.winner.map(|i| self.teams[i].as_str()).unwrap_or("")
        );
        if include_series {
            let mut number = 1;
            for (step, map) in self.picks() {
                let _ = writeln!(
                    value,
                    "Map {number}: {} — {}",
                    self.maps()[map],
                    self.teams[self.actor(step).unwrap()]
                );
                number += 1;
            }
            if let Some(index) = self.decider() {
                let _ = writeln!(value, "Map {number}: {} — DECIDER", self.maps()[index]);
            }
        }
        let _ = writeln!(value, "История");
        for (i, &map) in self.moves.iter().enumerate() {
            let _ = writeln!(
                value,
                "{} · {} · {}",
                self.teams[self.actor(i).unwrap()],
                self.action(i).unwrap(),
                self.maps()[map]
            );
        }
        value.trim_end().into()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[derive(Deserialize)]
    struct Case {
        format: String,
        first: usize,
        moves: Vec<usize>,
        maps: Vec<usize>,
        actors: Vec<usize>,
    }
    #[test]
    fn shared_sequences_and_restoration() {
        let cases: Vec<Case> =
            serde_json::from_str(include_str!("../../../shared-spec/test-cases.json")).unwrap();
        for case in cases {
            let mut game = Match {
                format: case.format,
                teams: ["Орлы".repeat(80), "Решка".into()],
                ..Default::default()
            };
            game.flip(0);
            game.start(case.first == 1);
            for (step, map) in case.moves.into_iter().enumerate() {
                assert_eq!(game.actor(step), Some(case.actors[step]));
                game.select(map);
                game.select(map);
                assert_eq!(game.moves.len(), step + 1);
                game = serde_json::from_str(&serde_json::to_string(&game).unwrap()).unwrap();
                assert!(game.is_valid());
            }
            let maps: Vec<_> = game
                .picks()
                .map(|(_, map)| map)
                .chain(game.decider())
                .collect();
            assert_eq!(maps, case.maps);
            game.undo();
            assert_eq!(game.decider(), None);
        }
    }
    #[test]
    fn system_coin_samples_both_sides() {
        let heads: usize = (0..1000).map(|_| flip_side().unwrap()).sum();
        assert!((300..700).contains(&heads));
    }
}
