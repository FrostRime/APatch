use anyhow::{Result, anyhow};
use ap_supercall::su_profile::SuProfile;
use ap_supercall::supercall::SuperCall;
use jni::objects::{JClass, JIntArray, JString, JValue};
use jni::sys::{JNI_ERR, JNI_VERSION_1_6, jboolean, jint, jlong, jobject, jobjectArray};
use jni::{JNIEnv, JavaVM};
use libc::{c_char, c_long, uid_t};
use log::debug;
use std::ffi::{CStr, CString, c_void};
use std::fs::{self, File, OpenOptions};
use std::io::{BufRead, BufReader, BufWriter, Error, Write};
use std::path::Path;
use std::ptr::null_mut;
use std::sync::OnceLock;

static SC_INSTANCE: OnceLock<SuperCall> = OnceLock::new();

const SU_PATH_MAX_LEN: usize = 128;

fn ensure_super_key(super_key_jstr: &JString) {
    if super_key_jstr.is_null() {
        panic!("SuperKey is null!")
    }
}

fn init_supercall() -> Result<()> {
    let sc = SuperCall::new(0, 12, 6);
    SC_INSTANCE
        .set(sc)
        .map_err(|_| anyhow!("SuperCall already initialized!"))
}

fn get_sc() -> &'static SuperCall {
    SC_INSTANCE.get().expect("SuperCall not initialized!")
}

fn jstr_to_cstr(env: &mut JNIEnv, s: &JString) -> CString {
    let r_str: String = env.get_string(s).expect("Invalid jstring").into();
    CString::new(r_str).unwrap()
}

fn ret_to_jlong(ret: Result<c_long>) -> jlong {
    ret.map(|x| x as jlong).unwrap_or_else(|e| {
        e.downcast_ref::<Error>()
            .and_then(|e| e.raw_os_error())
            .map(|x| -x)
            .unwrap_or(-1) as jlong
    })
}

fn native_ready(mut env: JNIEnv, _: JClass, key: JString) -> jboolean {
    ensure_super_key(&key);
    let key = &jstr_to_cstr(&mut env, &key);
    (get_sc().sc_ready(key)) as jboolean
}

fn native_kernel_patch_version(mut env: JNIEnv, _: JClass, key: JString) -> jlong {
    ensure_super_key(&key);
    let key = &jstr_to_cstr(&mut env, &key);
    ret_to_jlong(get_sc().sc_kp_ver(key))
}

fn native_kernel_patch_build_time<'a>(mut env: JNIEnv<'a>, _: JClass, key: JString) -> JString<'a> {
    ensure_super_key(&key);
    let key = &jstr_to_cstr(&mut env, &key);
    let mut buf = [0u8; 4096];
    let _ = get_sc().sc_get_build_time(key, buf.as_mut_ptr() as *mut c_char, buf.len());
    env.new_string(unsafe { CStr::from_ptr(buf.as_ptr().cast()) }.to_string_lossy())
        .unwrap()
}

fn native_su(mut env: JNIEnv, _: JClass, key: JString, to_uid: jint, sctx: JString) -> jlong {
    ensure_super_key(&key);

    let c_key = jstr_to_cstr(&mut env, &key);
    let uid = unsafe { libc::getuid() };

    let sctx_str: String = if !sctx.is_null() {
        env.get_string(&sctx).map(|s| s.into()).unwrap_or_default()
    } else {
        String::new()
    };

    let mut profile = SuProfile::new(uid as i32, to_uid, &sctx_str);

    ret_to_jlong(get_sc().sc_su(&c_key, &mut profile))
}

fn native_set_uid_exclude(
    mut env: JNIEnv,
    _: JClass,
    key: JString,
    uid: jint,
    exclude: jint,
) -> jint {
    ensure_super_key(&key);
    let c_key = jstr_to_cstr(&mut env, &key);
    ret_to_jlong(get_sc().sc_set_ap_mod_exclude(&c_key, uid as i64, exclude)) as jint
}

fn native_get_uid_exclude(mut env: JNIEnv, _: JClass, key: JString, uid: jint) -> jint {
    ensure_super_key(&key);
    let key = jstr_to_cstr(&mut env, &key);
    debug!("[native_get_uid_exclude] uid: {}", uid);
    ret_to_jlong(
        get_sc()
            .sc_get_ap_mod_exclude(&key, uid as uid_t)
            .map_err(|_| anyhow!(Error::from_raw_os_error(0))),
    ) as jint
}

fn native_su_uids<'a>(mut env: JNIEnv<'a>, _: JClass, key: JString) -> JIntArray<'a> {
    ensure_super_key(&key);
    let key = jstr_to_cstr(&mut env, &key);
    let num = get_sc().sc_su_uid_nums(&key).unwrap_or(0) as i32;

    if num <= 0 {
        return env.new_int_array(0).unwrap();
    }

    let mut uids = vec![0u32; num as usize];
    let n = get_sc()
        .sc_su_allow_uids(&key, uids.as_mut_ptr(), num)
        .unwrap_or(0);
    let uids: Vec<i32> = uids.iter().map(|&x| x as i32).collect();

    if n > 0 {
        let array = env.new_int_array(n as i32).unwrap();
        env.set_int_array_region(&array, 0, uids.as_slice())
            .expect("REASON");
        return array;
    }
    env.new_int_array(0).expect("REASON")
}

fn native_su_profile<'a>(mut env: JNIEnv<'a>, _: JClass, key: JString, uid: jint) -> jobject {
    ensure_super_key(&key);
    let key = jstr_to_cstr(&mut env, &key);
    let mut profile = SuProfile::new(uid, 0, "");
    let rc = ret_to_jlong(get_sc().sc_su_uid_profile(&key, uid as u32, &mut profile));
    if rc < 0 {
        return null_mut();
    }
    let cls = env.find_class("me/bmax/apatch/Natives$Profile").unwrap();

    let obj = env.new_object(cls, "()V", &[]).unwrap();

    let scontext_cstr = unsafe { std::ffi::CStr::from_ptr(profile.scontext.as_ptr().cast()) };
    let scontext_jstr = env.new_string(scontext_cstr.to_string_lossy()).unwrap();

    env.set_field(&obj, "uid", "I", JValue::Int(profile.uid))
        .unwrap();
    env.set_field(&obj, "toUid", "I", JValue::Int(profile.to_uid))
        .unwrap();
    env.set_field(
        &obj,
        "scontext",
        "Ljava/lang/String;",
        JValue::from(&scontext_jstr),
    )
    .unwrap();
    obj.as_raw()
}

fn native_load_kernel_patch_module<'a>(
    mut env: JNIEnv<'a>,
    _: JClass,
    key: JString,
    module_path_jstr: JString,
    args_jstr: JString,
) -> jlong {
    ensure_super_key(&key);
    let key = jstr_to_cstr(&mut env, &key);
    let module_path = jstr_to_cstr(&mut env, &module_path_jstr);
    let args = jstr_to_cstr(&mut env, &args_jstr);
    ret_to_jlong(get_sc().sc_kpm_load(&key, module_path.as_ptr(), args.as_ptr(), null_mut()))
}

fn native_control_kernel_patch_module<'a>(
    mut env: JNIEnv<'a>,
    _: JClass,
    key: JString,
    module_name_jstr: JString,
    control_args_jstr: JString,
) -> jobject {
    ensure_super_key(&key);
    let key = jstr_to_cstr(&mut env, &key);
    let module_name = jstr_to_cstr(&mut env, &module_name_jstr);
    let args = jstr_to_cstr(&mut env, &control_args_jstr);
    let mut buf = [c_char::default(); 4096];
    let ptr = buf.as_mut_ptr();

    let rc = ret_to_jlong(get_sc().sc_kpm_control(
        &key,
        module_name.as_ptr(),
        args.as_ptr(),
        ptr,
        buf.len() as c_long,
    ));
    let cls = env.find_class("me/bmax/apatch/Natives$KPMCtlRes").unwrap();

    let obj = env.new_object(cls, "()V", &[]).unwrap();

    let out_msg_str = unsafe { std::ffi::CStr::from_ptr(buf.as_ptr().cast()).to_string_lossy() };
    let j_out_msg = env.new_string(out_msg_str).unwrap();

    env.set_field(&obj, "rc", "J", JValue::Long(rc)).unwrap();
    env.set_field(
        &obj,
        "outMsg",
        "Ljava/lang/String;",
        JValue::Object(&j_out_msg),
    )
    .unwrap();
    obj.as_raw()
}

fn native_unload_kernel_patch_module<'a>(
    mut env: JNIEnv<'a>,
    _: JClass,
    key: JString,
    module_name_jstr: JString,
) -> jlong {
    ensure_super_key(&key);
    let key = jstr_to_cstr(&mut env, &key);
    let module_name = jstr_to_cstr(&mut env, &module_name_jstr);
    ret_to_jlong(get_sc().sc_kpm_unload(&key, module_name.as_ptr(), null_mut()))
}

fn native_kernel_patch_module_num<'a>(mut env: JNIEnv<'a>, _: JClass, key: JString) -> jint {
    ensure_super_key(&key);
    let key = jstr_to_cstr(&mut env, &key);
    ret_to_jlong(get_sc().sc_kpm_nums(&key)) as jint
}

fn native_kernel_patch_module_list<'a>(
    mut env: JNIEnv<'a>,
    _: JClass,
    key: JString,
) -> JString<'a> {
    ensure_super_key(&key);
    let key = jstr_to_cstr(&mut env, &key);
    let mut buf = [c_char::default(); 4096];

    let ptr = buf.as_mut_ptr();
    get_sc().sc_kpm_list(&key, ptr, buf.len() as i32).unwrap();
    let out_msg_str = unsafe { std::ffi::CStr::from_ptr(buf.as_ptr().cast()).to_string_lossy() };
    env.new_string(out_msg_str).unwrap()
}

fn native_kernel_patch_module_info<'a>(
    mut env: JNIEnv<'a>,
    _: JClass,
    key: JString,
    module_name_jstr: JString,
) -> JString<'a> {
    ensure_super_key(&key);
    let key = jstr_to_cstr(&mut env, &key);
    let module_name = jstr_to_cstr(&mut env, &module_name_jstr);
    let mut buf = [c_char::default(); 1024];

    let ptr = buf.as_mut_ptr();
    get_sc()
        .sc_kpm_info(&key, module_name.as_ptr(), ptr, buf.len() as i32)
        .unwrap();
    let out_msg_str = unsafe { std::ffi::CStr::from_ptr(buf.as_ptr().cast()).to_string_lossy() };
    env.new_string(out_msg_str).unwrap()
}

fn native_grant_su(
    mut env: JNIEnv,
    _: JClass,
    key: JString,
    uid: jint,
    to_uid: jint,
    sctx: JString,
) -> jlong {
    ensure_super_key(&key);
    let key = jstr_to_cstr(&mut env, &key);
    let sctx_str = jstr_to_cstr(&mut env, &sctx);
    let mut profile = SuProfile::new(uid, to_uid, &sctx_str.to_string_lossy());
    ret_to_jlong(get_sc().sc_su_grant_uid(&key, &mut profile))
}

fn native_revoke_su(mut env: JNIEnv, _: JClass, key: JString, uid: jint) -> jlong {
    ensure_super_key(&key);
    let key = jstr_to_cstr(&mut env, &key);
    ret_to_jlong(get_sc().sc_su_revoke_uid(&key, uid as u32))
}

fn native_su_path<'a>(mut env: JNIEnv<'a>, _: JClass, key: JString) -> JString<'a> {
    ensure_super_key(&key);
    let key = jstr_to_cstr(&mut env, &key);
    let mut buf = [c_char::default(); SU_PATH_MAX_LEN];

    let ptr = buf.as_mut_ptr();
    get_sc()
        .sc_su_get_path(&key, ptr, buf.len() as i32)
        .unwrap();
    let out_msg_str = unsafe { std::ffi::CStr::from_ptr(buf.as_ptr().cast()).to_string_lossy() };
    env.new_string(out_msg_str).unwrap()
}

fn native_reset_su_path<'a>(
    mut env: JNIEnv<'a>,
    _: JClass,
    key: JString,
    su_path_jstr: JString,
) -> jboolean {
    ensure_super_key(&key);
    let key = jstr_to_cstr(&mut env, &key);
    let path = jstr_to_cstr(&mut env, &su_path_jstr);
    get_sc()
        .sc_su_reset_path(&key, path.as_ptr())
        .map(|ret| ret as u8)
        .unwrap_or(0u8)
}

fn native_install_kpm_module(
    mut env: JNIEnv,
    class: JClass,
    key_jstr: JString,
    module_path_jstr: JString,
    args_jstr: JString,
) -> jlong {
    ensure_super_key(&key_jstr);
    let path: String = env.get_string(&module_path_jstr).unwrap().into();
    let path = Path::new(&path);
    let data = fs::read(path).unwrap();

    let content = String::from_utf8_lossy(&data);
    let name = _find_kpm_field(&content, "name=").unwrap();
    _uninstall_kernel_patch_module(
        &CString::new(name).unwrap()
    );

    let res = native_load_kernel_patch_module(env, class, key_jstr, module_path_jstr, args_jstr);
    let mut name_buf = [0u8; 32];
    let name_bytes = name.as_bytes();
    let len = name_bytes.len().min(32);
    name_buf[..len].copy_from_slice(&name_bytes[..len]);
    let mut info = "\n".as_bytes().to_vec();
    info.extend_from_slice(&name_buf);
    info.push(0b01);
    info.push(0u8);
    let kpms_path = Path::new("/data/adb/ap/kpms/");
    if !kpms_path.exists() {
        fs::create_dir_all(kpms_path).unwrap();
    }
    let file_name = &path.file_name().unwrap().to_string_lossy().to_string();
    let dest_path = kpms_path.join(file_name);
    fs::copy(path, dest_path).unwrap();
    fs::remove_file(path).unwrap();
    let config_path = kpms_path.join("config");
    let mut config = OpenOptions::new()
        .append(true)
        .create(true)
        .open(config_path)
        .unwrap();
    info.extend_from_slice(file_name.as_bytes());
    config.write_all(&info).unwrap();
    res
}

fn native_uninstall_kernel_patch_module<'a>(
    mut env: JNIEnv<'a>,
    jclass: JClass,
    key_jstr: JString,
    module_name_jstr: JString,
) -> jlong {
    ensure_super_key(&key_jstr);
    let name = jstr_to_cstr(&mut env, &module_name_jstr);
    _uninstall_kernel_patch_module(&name);
    native_unload_kernel_patch_module(env, jclass, key_jstr, module_name_jstr)
}

fn _uninstall_kernel_patch_module(name: &CString)-> jlong {
    let mut name_buf = [0u8; 32];
    let name_bytes = name.as_bytes();
    let len = name_bytes.len().min(32);
    name_buf[..len].copy_from_slice(&name_bytes[..len]);
    let kpms_path = Path::new("/data/adb/ap/kpms/");
    if !kpms_path.exists() {
        return 0;
    }
    let config_path = kpms_path.join("config");
    let mut reader = BufReader::new(File::open(&config_path).unwrap());

    let mut kept = Vec::new();
    let mut record = Vec::new();
    while reader.read_until(b'\n', &mut record).unwrap() > 0 {
        if record.len() < 35 {
            record.clear();
            continue;
        }
        if record[0..32] == name_buf {
            let filename = String::from_utf8_lossy(&record[34..record.len() - 1]).to_string();
            let path = Path::new("/data/adb/ap/kpms/").join(filename);
            if path.exists() {
                fs::remove_file(path).unwrap();
            }
            record.clear();
            continue;
        }
        kept.extend(&record);
        record.clear();
    }

    let mut writer = BufWriter::new(File::create(config_path).unwrap());
    writer.write_all(&kept).unwrap();
    0
}

fn native_change_installed_kpm_module_state<'a>(
    mut env: JNIEnv<'a>,
    jclass: JClass,
    key_jstr: JString,
    module_name_jstr: JString,
    enabled: jboolean
) -> jlong {
    let name = jstr_to_cstr(&mut env, &module_name_jstr);
        let mut name_buf = [0u8; 32];
    let name_bytes = name.as_bytes();
    let len = name_bytes.len().min(32);
    name_buf[..len].copy_from_slice(&name_bytes[..len]);
    let kpms_path = Path::new("/data/adb/ap/kpms/");
    if !kpms_path.exists() {
        return 0;
    }
    let config_path = kpms_path.join("config");
    let mut reader = BufReader::new(File::open(&config_path).unwrap());

    let mut module_path = String::default();

    let mut kept = Vec::new();
    let mut record = Vec::new();
    while reader.read_until(b'\n', &mut record).unwrap() > 0 {
        if record.len() < 35 {
            record.clear();
            continue;
        }
        if record[0..32] == name_buf {
            record[32] = if enabled != 0 {
                0b01
            } else {
                0b00
            };

            module_path = kpms_path.join(String::from_utf8_lossy(&record[34..]).to_string().trim()).to_string_lossy().to_string();
            debug!("{}", module_path)
        }
        debug!("record len: {}", record.len());
        debug!("record bytes: {:?}", record);

        kept.extend(&record);
        record.clear();
    }

    debug!("kept bytes: {:?}", kept);

    debug!("writing config");
    let mut writer = BufWriter::new(File::create(config_path).unwrap());
    writer.write_all(&kept).unwrap();
    writer.flush().unwrap();

    debug!("done writing config");

    if enabled != 0 {
        let path_jstr = env.new_string(&module_path).unwrap();
        let args = env.new_string("").unwrap();
        native_load_kernel_patch_module(env, jclass, key_jstr, path_jstr, args)
    } else {
        0
    }
}

fn native_installed_kpm_list<'a>(mut env: JNIEnv<'a>, _: JClass) -> jobjectArray {
    let mut kpm_list = Vec::new();
    let kpm_dir = Path::new("/data/adb/ap/kpms/");
    debug!("kpm dir exists:{}" , kpm_dir.exists());
    if kpm_dir.exists() {
        let config = kpm_dir.join("config");
        debug!("Reading kpm config from {}", config.to_string_lossy());
        debug!("config exists:{}" , config.exists());
        if config.exists() {
            let mut reader = BufReader::new(File::open(config).unwrap());
            let mut record = Vec::new();
            while reader.read_until(b'\n', &mut record).unwrap() > 0 {
                if record.len() < 35 {
                    record.clear();
                    continue;
                }
                let name = String::from_utf8_lossy(&record[0..32]).trim_end_matches('\0').to_string();
                    kpm_list.push(env.new_string(name).unwrap());
                record.clear();
            }
        }
    }
    let array = env
        .new_object_array(
            kpm_list.len() as i32,
            "java/lang/String",
            JString::default(),
        )
        .unwrap();
    for (i, jstr) in kpm_list.iter().enumerate() {
        env.set_object_array_element(&array, i as i32, jstr).unwrap();
    }
    array.as_raw()
}

fn _find_kpm_field<'a>(content: &'a str, prefix: &str) -> Option<&'a str> {
    let start = content.find(prefix)?;
    let after_prefix = start + prefix.len();

    let end = content[after_prefix..]
        .find(|c: char| ['\0', '\n'].contains(&c))
        .map(|pos| after_prefix + pos)
        .unwrap_or_else(|| content.len());

    Some(&content[after_prefix..end])
}

macro_rules! method {
    ($name:expr, $sig:expr, $fn:ident) => {
        jni::NativeMethod {
            name: $name.into(),
            sig: $sig.into(),
            fn_ptr: $fn as *mut c_void,
        }
    };
}

#[unsafe(no_mangle)]
pub extern "system" fn JNI_OnLoad(vm: JavaVM, _reserved: *mut c_void) -> jint {
    // 1. 获取 JNIEnv
    let mut env = match vm.get_env() {
        Ok(env) => env,
        Err(_) => return JNI_ERR,
    };
    android_logger::init_once(
        android_logger::Config::default().with_max_level(log::LevelFilter::Debug),
    );

    init_supercall().unwrap();

    debug!("JNI_OnLoad");

    let class_name = "me/bmax/apatch/Natives";
    let clazz = match env.find_class(class_name) {
        Ok(c) => c,
        Err(_) => return JNI_ERR,
    };
    let methods = [
        method!("nativeReady", "(Ljava/lang/String;)Z", native_ready),
        method!(
            "nativeKernelPatchVersion",
            "(Ljava/lang/String;)J",
            native_kernel_patch_version
        ),
        method!(
            "nativeKernelPatchBuildTime",
            "(Ljava/lang/String;)Ljava/lang/String;",
            native_kernel_patch_build_time
        ),
        method!(
            "nativeSu",
            "(Ljava/lang/String;ILjava/lang/String;)J",
            native_su
        ),
        method!(
            "nativeSetUidExclude",
            "(Ljava/lang/String;II)I",
            native_set_uid_exclude
        ),
        method!(
            "nativeGetUidExclude",
            "(Ljava/lang/String;I)I",
            native_get_uid_exclude
        ),
        method!("nativeSuUids", "(Ljava/lang/String;)[I", native_su_uids),
        method!(
            "nativeSuProfile",
            "(Ljava/lang/String;I)Lme/bmax/apatch/Natives$Profile;",
            native_su_profile
        ),
        method!(
            "nativeLoadKernelPatchModule",
            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)J",
            native_load_kernel_patch_module
        ),
        method!(
            "nativeControlKernelPatchModule",
            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Lme/bmax/apatch/Natives$KPMCtlRes;",
            native_control_kernel_patch_module
        ),
        method!(
            "nativeUnloadKernelPatchModule",
            "(Ljava/lang/String;Ljava/lang/String;)J",
            native_unload_kernel_patch_module
        ),
        method!(
            "nativeKernelPatchModuleNum",
            "(Ljava/lang/String;)J",
            native_kernel_patch_module_num
        ),
        method!(
            "nativeKernelPatchModuleList",
            "(Ljava/lang/String;)Ljava/lang/String;",
            native_kernel_patch_module_list
        ),
        method!(
            "nativeKernelPatchModuleInfo",
            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
            native_kernel_patch_module_info
        ),
        method!(
            "nativeGrantSu",
            "(Ljava/lang/String;IILjava/lang/String;)J",
            native_grant_su
        ),
        method!("nativeRevokeSu", "(Ljava/lang/String;I)J", native_revoke_su),
        method!(
            "nativeSuPath",
            "(Ljava/lang/String;)Ljava/lang/String;",
            native_su_path
        ),
        method!(
            "nativeResetSuPath",
            "(Ljava/lang/String;Ljava/lang/String;)Z",
            native_reset_su_path
        ),
        method!(
            "nativeInstallKpmModule",
            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)J",
            native_install_kpm_module
        ),
        method!(
            "nativeUninstallKpmModule",
            "(Ljava/lang/String;Ljava/lang/String;)J",
            native_uninstall_kernel_patch_module
        ),
        method!(
            "nativeInstalledKpmList",
            "()[Ljava/lang/String;",
            native_installed_kpm_list
        ),
        method!(
            "nativeChangeInstalledKpmModuleState",
            "(Ljava/lang/String;Ljava/lang/String;Z)J",
            native_change_installed_kpm_module_state
        )
    ];

    // 4. 注册方法
    if env.register_native_methods(clazz, &methods).is_err() {
        return JNI_ERR;
    }

    JNI_VERSION_1_6
}
