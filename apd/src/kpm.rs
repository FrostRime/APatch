use std::{
    ffi::{CStr, CString},
    fs::File,
    io::{BufRead, BufReader},
    path::Path,
    ptr::{null, null_mut},
    thread,
    time::Duration,
};

use anyhow::{Result, anyhow};
use log::warn;
use rustix::path::Arg;

use crate::{
    cli::SUPERCALL,
    defs::{KPMS_CONFIG, KPMS_DIR},
};

struct KpmInfo {
    enabled: bool,
    stage: u8,
    file_name: String,
}

pub const KPM_STAGE_BOOT_COMPLETED: u8 = 0;
pub const KPM_STAGE_SERVICE: u8 = 1;
pub const KPM_STAGE_POSTFS_DATA: u8 = 2;
pub const KPM_STAGE_POSTMOUNT: u8 = 3;

pub fn stage_map(stage: &str) -> u8 {
    match stage {
        "boot-completed" => KPM_STAGE_BOOT_COMPLETED,
        "service" => KPM_STAGE_SERVICE,
        "post-fs-data" => KPM_STAGE_POSTFS_DATA,
        "post-mount" => KPM_STAGE_POSTMOUNT,
        _ => u8::MAX,
    }
}

#[inline]
fn kpm_list() -> Option<impl Iterator<Item = KpmInfo>> {
    let max_retry = 5;
    let file = (0..max_retry).find_map(|_| {
        File::open(KPMS_CONFIG).ok().or_else(|| {
            thread::sleep(Duration::from_secs(1));
            None
        })
    })?;

    Some(
        BufReader::new(file)
            .split(b'\n')
            .map_while(Result::ok)
            .filter(|line| !line.is_empty())
            .filter(|line| line.len() > 34)
            .map(|line| {
                let bool8 = &line[32];
                let enabled = (bool8 & 0b01) != 0;
                let stage = line[33];
                let file_name = String::from_utf8_lossy(&line[34..])
                    .trim()
                    .trim_matches('\0')
                    .to_string();
                KpmInfo {
                    enabled,
                    stage,
                    file_name,
                }
            })
            .filter(|kpm| kpm.enabled),
    )
}

pub fn load_kpms(key: &CStr, stage: &str) -> Result<()> {
    let stage = stage_map(stage);
    if stage == u8::MAX {
        return Ok(());
    }
    let mut list = kpm_list()
        .ok_or(anyhow!("no kpm needed to load"))?
        .filter(|kpm| kpm.stage == stage)
        .peekable();
    list.peek().ok_or(anyhow!("no kpm needed to load"))?;

    for kpm in list {
        let path = Path::new(&KPMS_DIR).join(&kpm.file_name);
        let path = match CString::new(path.to_string_lossy().as_ref()) {
            Ok(path) => path,
            Err(_) => {
                warn!("failed to load {}", kpm.file_name);
                continue;
            }
        };
        let ptr = path.as_ptr();
        if SUPERCALL.sc_kpm_load(key, ptr, null(), null_mut()).is_err() {
            warn!("failed to load {}", kpm.file_name);
            continue;
        }
    }

    Ok(())
}
