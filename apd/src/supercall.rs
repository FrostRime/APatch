use crate::cli::SUPERCALL;
use crate::package::synchronize_package_config;
use ap_supercall::su_profile::SuProfile;
use std::{
    ffi::{CStr, CString},
    fs::File,
    io::{self, Read},
    process,
    sync::{Arc, Mutex},
};

use libc::uid_t;
use log::{error, info, warn};

const SUPERCALL_SCONTEXT_LEN: usize = 0x60;

fn read_file_to_string(path: &str) -> io::Result<String> {
    let mut file = File::open(path)?;
    let mut content = String::new();
    file.read_to_string(&mut content)?;
    Ok(content)
}

fn convert_string_to_u8_array(s: &str) -> [u8; SUPERCALL_SCONTEXT_LEN] {
    let mut u8_array = [0u8; SUPERCALL_SCONTEXT_LEN];
    let bytes = s.as_bytes();
    let len = usize::min(SUPERCALL_SCONTEXT_LEN, bytes.len());
    u8_array[..len].copy_from_slice(&bytes[..len]);
    u8_array
}

fn convert_superkey(s: &Option<String>) -> Option<CString> {
    s.as_ref().and_then(|s| CString::new(s.clone()).ok())
}

pub fn refresh_ap_package_list(skey: &CStr, mutex: &Arc<Mutex<()>>) {
    let _lock = mutex.lock().unwrap();

    let num = match SUPERCALL.sc_su_uid_nums(skey) {
        Ok(num) => num,
        Err(e) => {
            error!("[refresh_su_list] Error getting number of UIDs: {}", e);
            return;
        }
    };
    let num = num as usize;
    let mut uids = vec![0 as uid_t; num];
    if SUPERCALL
        .sc_su_allow_uids(skey, uids.as_mut_ptr(), num as i32)
        .is_err()
    {
        error!("[refresh_su_list] Error getting su list");
        return;
    };
    for uid in &uids {
        if *uid == 0 || *uid == 2000 {
            warn!(
                "[refresh_ap_package_list] Skip revoking critical uid: {}",
                uid
            );
            continue;
        }
        info!(
            "[refresh_ap_package_list] Revoking {} root permission...",
            uid
        );
        if let Err(e) = SUPERCALL.sc_su_revoke_uid(skey, *uid) {
            error!("[refresh_ap_package_list] Error revoking UID: {}", e);
        }
    }

    match synchronize_package_config() {
        Ok(package_configs) => {
            for config in package_configs {
                if config.allow == 1 && config.exclude == 0 {
                    let mut profile = SuProfile {
                        uid: config.uid,
                        to_uid: config.to_uid,
                        scontext: convert_string_to_u8_array(&config.sctx),
                    };
                    match SUPERCALL.sc_su_grant_uid(skey, &mut profile) {
                        Ok(result) => info!(
                            "[refresh_ap_package_list] Loading {}: result = {}",
                            config.pkg, result
                        ),
                        Err(e) => error!(
                            "[refresh_ap_package_list] Loading {}: result = Err: {:?}",
                            config.pkg, e
                        ),
                    }
                }
                if config.allow == 0 && config.exclude == 1 {
                    match SUPERCALL.sc_set_ap_mod_exclude(skey, config.uid as i64, 1) {
                        Ok(result) => info!(
                            "[refresh_ap_package_list] Loading exclude {}: result = {}",
                            config.pkg, result
                        ),
                        Err(e) => error!(
                            "[refresh_ap_package_list] Loading exclude {}: result = Err: {:?}",
                            config.pkg, e
                        ),
                    }
                }
            }
        }
        Err(e) => error!("Failed to synchronize package UIDs: {}", e),
    }
}

pub fn privilege_apd_profile(superkey: &Option<String>) {
    let key = convert_superkey(superkey);

    let all_allow_ctx = "u:r:magisk:s0";
    let mut profile = SuProfile {
        uid: process::id().try_into().expect("PID conversion failed"),
        to_uid: 0,
        scontext: convert_string_to_u8_array(all_allow_ctx),
    };
    if let Some(ref key) = key {
        let _ = SUPERCALL
            .sc_su(key, &mut profile)
            .inspect(|val| info!("[privilege_apd_profile] result = {}", val))
            .inspect_err(|e| info!("[privilege_apd_profile] result = Err: {:?}", e));
    }
}

pub fn init_load_su_path(superkey: &Option<String>) {
    let su_path_file = "/data/adb/ap/su_path";

    match read_file_to_string(su_path_file) {
        Ok(su_path) => {
            let superkey_cstr = convert_superkey(superkey);

            match superkey_cstr {
                Some(superkey_cstr) => match CString::new(su_path.trim()) {
                    Ok(su_path_cstr) => {
                        let _ = SUPERCALL
                            .sc_su_reset_path(&superkey_cstr, su_path_cstr.as_ptr())
                            .inspect(|_| {
                                info!("suPath load successfully");
                            })
                            .inspect_err(|e| warn!("Failed to load su path, error code: {}", e));
                    }
                    Err(e) => {
                        warn!("Failed to convert su_path: {}", e);
                    }
                },
                _ => {
                    warn!("Superkey is None, skipping...");
                }
            }
        }
        Err(e) => {
            warn!("Failed to read su_path file: {}", e);
        }
    }
}
