use log::{info, warn};
use serde::{Deserialize, Serialize};
use std::collections::HashSet;
use std::fs::{self, File};
use std::io::{self, BufRead};
use std::path::Path;
use std::thread;
use std::time::Duration;

#[derive(Deserialize, Serialize, Clone)]
pub struct PackageConfig {
    pub pkg: String,
    pub exclude: i32,
    pub allow: i32,
    pub uid: i32,
    pub to_uid: i32,
    pub sctx: String,
}

pub fn read_ap_package_config() -> Vec<PackageConfig> {
    let max_retry = 5;
    for _ in 0..max_retry {
        let file = match File::open("/data/adb/ap/package_config") {
            Ok(file) => file,
            Err(e) => {
                warn!("Error opening file: {}", e);
                thread::sleep(Duration::from_secs(1));
                continue;
            }
        };

        let mut reader = csv::Reader::from_reader(file);
        let mut package_configs = Vec::new();
        let mut success = true;

        for record in reader.deserialize() {
            match record {
                Ok(config) => package_configs.push(config),
                Err(e) => {
                    warn!("Error deserializing record: {}", e);
                    success = false;
                    break;
                }
            }
        }

        if success {
            return package_configs;
        }
        thread::sleep(Duration::from_secs(1));
    }
    Vec::new()
}

pub fn whitelist_mode() -> i32 {
    let max_retry = 5;
    for _ in 0..max_retry {
        return match fs::read_to_string("/data/adb/ap/whitelist_config") {
            Ok(s) => s.trim().parse::<i32>().unwrap_or(-1),
            Err(e) => {
                warn!("Error opening file: {}", e);
                thread::sleep(Duration::from_secs(1));
                continue;
            }
        };
    }
    -1
}

pub fn manager_package_id() -> String {
    let max_retry = 5;
    for i in 0..max_retry {
        match fs::read_to_string("/data/adb/ap/ap_info") {
            Ok(content) => return content.trim().to_string(),
            Err(e) => {
                warn!("读取 ap_info 失败 (第 {} 次): {}", i + 1, e);
                thread::sleep(Duration::from_secs(1));
            }
        }
    }
    "me.bmax.apatch".to_string()
}

pub fn write_ap_package_config(package_configs: &[PackageConfig]) -> io::Result<()> {
    let max_retry = 5;
    for _ in 0..max_retry {
        let temp_path = "/data/adb/ap/package_config.tmp";
        let file = match File::create(temp_path) {
            Ok(file) => file,
            Err(e) => {
                warn!("Error creating temp file: {}", e);
                thread::sleep(Duration::from_secs(1));
                continue;
            }
        };

        let mut writer = csv::Writer::from_writer(file);
        let mut success = true;

        for config in package_configs {
            if let Err(e) = writer.serialize(config) {
                warn!("Error serializing record: {}", e);
                success = false;
                break;
            }
        }

        if !success {
            thread::sleep(Duration::from_secs(1));
            continue;
        }

        if let Err(e) = writer.flush() {
            warn!("Error flushing writer: {}", e);
            thread::sleep(Duration::from_secs(1));
            continue;
        }

        if let Err(e) = std::fs::rename(temp_path, "/data/adb/ap/package_config") {
            warn!("Error renaming temp file: {}", e);
            thread::sleep(Duration::from_secs(1));
            continue;
        }
        return Ok(());
    }
    Err(io::Error::other("Failed after max retries"))
}

fn read_lines<P>(filename: P) -> io::Result<io::Lines<io::BufReader<File>>>
where
    P: AsRef<Path>,
{
    File::open(filename).map(|file| io::BufReader::new(file).lines())
}

pub fn synchronize_package_config() -> io::Result<Vec<PackageConfig>> {
    info!("[synchronize_package_uid] Start synchronizing root list with system packages...");

    let max_retry = 5;
    for _ in 0..max_retry {
        match read_lines("/data/system/packages.list") {
            Ok(lines) => {
                let system_packages_map: HashSet<(String, String, bool)> = lines
                    .map_while(Result::ok)
                    .filter_map(|line| {
                        let mut parts = line.split_whitespace();
                        let pkg = parts.next()?.to_string();
                        let uid = parts.next()?.to_string();
                        let is_system_app = parts.last()? == "@system";
                        Some((pkg, uid, is_system_app))
                    })
                    .collect();

                let mut package_configs = read_ap_package_config();

                let system_packages: HashSet<String> = system_packages_map
                    .iter()
                    .map(|(pkg, _, _)| pkg.clone())
                    .collect();

                let original_len = package_configs.len();
                package_configs.retain(|config| system_packages.contains(&config.pkg));
                let mut seen = HashSet::new();
                package_configs.retain(|config| seen.insert(config.pkg.clone()));
                drop(seen);
                let removed_count = original_len - package_configs.len();

                if removed_count > 0 {
                    info!(
                        "Removed {} uninstalled package configurations",
                        removed_count
                    );
                }

                let mut updated = false;

                let mut config_map: std::collections::HashMap<String, &mut PackageConfig> =
                    package_configs
                        .iter_mut()
                        .map(|c| (c.pkg.clone(), c))
                        .collect();

                let whitelist_mode = whitelist_mode();
                let mut extra_configs = Vec::new();
                let manager_package_id = manager_package_id();
                let manager_package_id = if system_packages.contains(&manager_package_id) {
                    manager_package_id
                } else {
                    "com.bmax.apatch".to_string()
                };

                for (pkg, uid, is_system_app) in system_packages_map {
                    if let Ok(uid) = uid.parse::<i32>() {
                        if let Some(config) = config_map.get_mut(&pkg) {
                            if config.uid % 100000 != uid % 100000 {
                                let new_uid = config.uid / 100000 * 100000 + uid % 100000;
                                info!(
                                    "Updating uid for package {}: {} -> {}",
                                    pkg, config.uid, new_uid
                                );
                                config.uid = new_uid;
                                updated = true;
                            }
                        } else if manager_package_id != pkg
                            && match whitelist_mode {
                                -1 => false,
                                0 => !is_system_app,
                                1 => is_system_app,
                                2 => true,
                                _ => false,
                            }
                        {
                            let config = PackageConfig {
                                pkg,
                                exclude: 1,
                                allow: 0,
                                uid,
                                to_uid: 0,
                                sctx: "u:r:untrusted_app:s0".to_string(),
                            };
                            extra_configs.push(config);
                        }
                    }
                }

                if updated || removed_count > 0 {
                    write_ap_package_config(&package_configs)?;
                }
                return Ok([extra_configs, package_configs].concat());
            }
            Err(e) => {
                warn!("Error reading packages.list: {}", e);
                thread::sleep(Duration::from_secs(1));
            }
        }
    }
    Err(io::Error::other("Failed after max retries"))
}
