use crate::cli::SUPERCALL;
use crate::package::synchronize_package_config;
use ap_supercall::su_profile::SuProfile;
use std::{
    ffi::{CStr, CString},
    fmt::Write,
    fs::File,
    io::{self, Read},
    process,
    process::exit,
    ptr,
    sync::{Arc, Mutex},
};

use errno::errno;
use libc::{c_int, c_long, execv, fork, pid_t, setenv, uid_t, wait};
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

pub fn init_load_package_uid_config(superkey: &Option<String>) {
    match synchronize_package_config() {
        Ok(package_configs) => {
            let key = convert_superkey(superkey);

            for config in package_configs {
                if config.allow == 1 && config.exclude == 0 {
                    match key {
                        Some(ref key) => {
                            let mut profile = SuProfile {
                                uid: config.uid,
                                to_uid: config.to_uid,
                                scontext: convert_string_to_u8_array(&config.sctx),
                            };
                            match SUPERCALL.sc_su_grant_uid(key, &mut profile) {
                                Ok(result) => {
                                    info!("Processed {}: result = {}", config.pkg, result)
                                }
                                Err(e) => warn!("Processed {}: result = Err: {:?}", config.pkg, e),
                            }
                        }
                        _ => {
                            warn!("Superkey is None, skipping config: {}", config.pkg);
                        }
                    }
                }
                if config.allow == 0 && config.exclude == 1 {
                    match key {
                        Some(ref key) => {
                            match SUPERCALL.sc_set_ap_mod_exclude(key, config.uid as i64, 1) {
                                Ok(result) => {
                                    info!("Processed exclude {}: result = {}", config.pkg, result)
                                }
                                Err(e) => {
                                    warn!("Processed exclude {}: result = Err: {:?}", config.pkg, e)
                                }
                            }
                        }
                        _ => {
                            warn!("Superkey is None, skipping config: {}", config.pkg);
                        }
                    }
                }
            }
        }
        Err(e) => error!("Failed to synchronize package UIDs: {}", e),
    };
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

fn set_env_var(key: &str, value: &str) {
    let key_c = CString::new(key).expect("CString::new failed");
    let value_c = CString::new(value).expect("CString::new failed");
    unsafe {
        setenv(key_c.as_ptr(), value_c.as_ptr(), 1);
    }
}

fn log_kernel(key: &CStr, _fmt: &str, args: std::fmt::Arguments) -> c_long {
    let mut buf = String::with_capacity(1024);
    write!(&mut buf, "{}", args).expect("Error formatting string");

    let c_buf = CString::new(buf).expect("CString::new failed");
    SUPERCALL.sc_klog(key, c_buf.as_ptr()).unwrap_or_else(|e| {
        e.downcast_ref::<io::Error>()
            .unwrap()
            .raw_os_error()
            .unwrap() as c_long
    })
}

#[macro_export]
macro_rules! log_kernel {
    ($key:expr_2021, $fmt:expr_2021, $($arg:tt)*) => (
        log_kernel($key, $fmt, std::format_args!($fmt, $($arg)*))
    )
}

pub fn fork_for_result(exec: &str, argv: &[&str], key: &Option<String>) {
    let mut cmd = String::new();
    for arg in argv {
        cmd.push_str(arg);
        cmd.push(' ');
    }

    let superkey_cstr = convert_superkey(key);

    match superkey_cstr {
        Some(superkey_cstr) => {
            unsafe {
                let pid: pid_t = fork();
                if pid < 0 {
                    log_kernel!(
                        &superkey_cstr,
                        "{} fork {} error: {}\n",
                        libc::getpid(),
                        exec,
                        -1
                    );
                } else if pid == 0 {
                    set_env_var("KERNELPATCH", "true");
                    let kpver = format!("{:x}", SUPERCALL.sc_kp_ver(&superkey_cstr).unwrap_or(0));
                    set_env_var("KERNELPATCH_VERSION", kpver.as_str());
                    let kver = format!("{:x}", SUPERCALL.sc_k_ver(&superkey_cstr).unwrap_or(0));
                    set_env_var("KERNEL_VERSION", kver.as_str());

                    let c_exec = CString::new(exec).expect("CString::new failed");
                    let c_argv: Vec<CString> =
                        argv.iter().map(|&arg| CString::new(arg).unwrap()).collect();
                    let mut c_argv_ptrs: Vec<*const libc::c_char> =
                        c_argv.iter().map(|arg| arg.as_ptr()).collect();
                    c_argv_ptrs.push(ptr::null());

                    execv(c_exec.as_ptr(), c_argv_ptrs.as_ptr());

                    log_kernel!(
                        &superkey_cstr,
                        "{} exec {} error: {}\n",
                        libc::getpid(),
                        cmd,
                        CStr::from_ptr(libc::strerror(errno().0))
                            .to_string_lossy()
                            .into_owned()
                    );
                    exit(1); // execv only returns on error
                } else {
                    let mut status: c_int = 0;
                    wait(&mut status);
                    log_kernel!(
                        &superkey_cstr,
                        "{} wait {} status: 0x{}\n",
                        libc::getpid(),
                        cmd,
                        status
                    );
                }
            }
        }
        _ => {
            warn!("[fork_for_result] SuperKey convert failed!");
        }
    }
}
