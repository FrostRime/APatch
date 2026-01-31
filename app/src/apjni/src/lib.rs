#![deny(clippy::unwrap_used)]
use anyhow::{Result, anyhow, bail};
use ap_supercall::su_profile::SuProfile;
use ap_supercall::supercall::SuperCall;
use jni::objects::{JClass, JIntArray, JString, JValue};
use jni::sys::{JNI_ERR, JNI_FALSE, JNI_VERSION_1_6, jboolean, jint, jlong, jobject, jobjectArray};
use jni::{JNIEnv, JavaVM};
use libc::{c_char, c_long, uid_t};
use log::{debug, warn};
use std::ffi::{CStr, CString, c_void};
use std::fs::{self, File, OpenOptions};
use std::io::{BufRead, BufReader, BufWriter, Error, Write};
use std::path::Path;
use std::ptr::null_mut;
use std::sync::OnceLock;

static SC_INSTANCE: OnceLock<SuperCall> = OnceLock::new();

const SU_PATH_MAX_LEN: usize = 128;

fn ensure_super_key(super_key: &JString) -> Result<()> {
    match super_key.is_null() {
        false => Ok(()),
        _ => bail!("SuperKey must not be null"),
    }
}

fn jni_wrap<'a, T, F>(env: &mut JNIEnv<'a>, default: T, f: F) -> T
where
    F: FnOnce(&mut JNIEnv<'a>) -> Result<T>,
{
    match f(env) {
        Ok(v) => v,
        Err(e) => {
            throw_error(env, e);
            default
        }
    }
}

fn init_supercall() -> Result<()> {
    let sc = SuperCall::new(0, 12, 6);
    SC_INSTANCE
        .set(sc)
        .map_err(|_| anyhow!("SuperCall already initialized!"))
}

fn get_sc() -> Result<&'static SuperCall> {
    SC_INSTANCE
        .get()
        .ok_or_else(|| anyhow!("SuperCall not initialized!"))
}

fn jstr_to_cstr(env: &mut JNIEnv, s: &JString) -> Result<CString> {
    let r_str: String = env.get_string(s)?.into();
    Ok(CString::new(r_str)?)
}

fn throw_error(env: &mut JNIEnv, e: anyhow::Error) {
    let _ = env.throw_new("java/lang/RuntimeException", e.to_string());
}

fn native_ready(mut env: JNIEnv, _: JClass, key: JString) -> jboolean {
    jni_wrap(&mut env, JNI_FALSE, |env| {
        ensure_super_key(&key)?;
        let sc = get_sc()?;
        Ok(sc.sc_ready(&jstr_to_cstr(env, &key)?) as jboolean)
    })
}

fn native_kernel_patch_version(mut env: JNIEnv, _: JClass, key: JString) -> jlong {
    jni_wrap(&mut env, -1, |env| {
        ensure_super_key(&key)?;
        Ok(get_sc()?.sc_kp_ver(&jstr_to_cstr(env, &key)?)? as jlong)
    })
}

fn native_kernel_patch_build_time<'a>(mut env: JNIEnv<'a>, _: JClass, key: JString) -> JString<'a> {
    jni_wrap(&mut env, JString::default(), |env| {
        ensure_super_key(&key)?;
        let mut buf = [0u8; 4096];
        get_sc()?.sc_get_build_time(
            &jstr_to_cstr(env, &key)?,
            buf.as_mut_ptr() as *mut c_char,
            buf.len(),
        )?;
        Ok(env.new_string(unsafe { CStr::from_ptr(buf.as_ptr().cast()) }.to_string_lossy())?)
    })
}

fn native_su(mut env: JNIEnv, _: JClass, key: JString, to_uid: jint, sctx: JString) -> jlong {
    jni_wrap(&mut env, -1, |env| {
        ensure_super_key(&key)?;

        let c_key = jstr_to_cstr(env, &key)?;
        let uid = unsafe { libc::getuid() };

        let sctx_str: String = if !sctx.is_null() {
            env.get_string(&sctx).map(|s| s.into()).unwrap_or_default()
        } else {
            String::new()
        };

        let mut profile = SuProfile::new(uid as i32, to_uid, &sctx_str);

        get_sc()?.sc_su(&c_key, &mut profile)
    })
}

fn native_set_uid_exclude(
    mut env: JNIEnv,
    _: JClass,
    key: JString,
    uid: jint,
    exclude: jint,
) -> jint {
    jni_wrap(&mut env, -1, |env| {
        ensure_super_key(&key)?;
        let c_key = jstr_to_cstr(env, &key)?;
        Ok(get_sc()?.sc_set_ap_mod_exclude(&c_key, uid as i64, exclude)? as jint)
    })
}

fn native_get_uid_exclude(mut env: JNIEnv, _: JClass, key: JString, uid: jint) -> jint {
    jni_wrap(&mut env, -1, |env| {
        ensure_super_key(&key)?;
        let key = jstr_to_cstr(env, &key)?;
        Ok(get_sc()?.sc_get_ap_mod_exclude(&key, uid as uid_t)? as jint)
    })
}

fn native_su_uids<'a>(mut env: JNIEnv<'a>, _: JClass, key: JString) -> JIntArray<'a> {
    let default = env
        .new_int_array(0)
        .unwrap_or_else(|_| JIntArray::default());
    jni_wrap(&mut env, default, |env| {
        ensure_super_key(&key)?;
        let key = jstr_to_cstr(env, &key)?;
        let num = get_sc()?.sc_su_uid_nums(&key)? as i32;
        if num <= 0 {
            return Ok(env.new_int_array(0)?);
        }
        let mut uids = vec![0u32; num as usize];
        let _ = get_sc()?.sc_su_allow_uids(&key, uids.as_mut_ptr(), num)?;
        let array = env.new_int_array(num as i32)?;
        let uids: Vec<i32> = uids.iter().map(|&x| x as i32).collect();
        env.set_int_array_region(&array, 0, uids.as_slice())?;
        Ok(array)
    })
}

fn native_su_profile<'a>(mut env: JNIEnv<'a>, _: JClass, key: JString, uid: jint) -> jobject {
    jni_wrap(&mut env, null_mut(), |env| {
        ensure_super_key(&key)?;
        let key = jstr_to_cstr(env, &key)?;
        let mut profile = SuProfile::new(uid, 0, "");
        let _ = get_sc()?.sc_su_uid_profile(&key, uid as u32, &mut profile)?;
        let cls = env.find_class("me/bmax/apatch/Natives$Profile")?;

        let obj = env.new_object(cls, "()V", &[])?;

        let scontext_cstr = unsafe { std::ffi::CStr::from_ptr(profile.scontext.as_ptr().cast()) };
        let scontext_jstr = env.new_string(scontext_cstr.to_string_lossy())?;

        env.set_field(&obj, "uid", "I", JValue::Int(profile.uid))?;
        env.set_field(&obj, "toUid", "I", JValue::Int(profile.to_uid))?;
        env.set_field(
            &obj,
            "scontext",
            "Ljava/lang/String;",
            JValue::from(&scontext_jstr),
        )?;
        Ok(obj.as_raw())
    })
}

fn native_load_kernel_patch_module<'a>(
    mut env: JNIEnv<'a>,
    _: JClass,
    key: JString,
    module_path_jstr: JString,
    args_jstr: JString,
) -> jlong {
    jni_wrap(&mut env, -1, |env| {
        ensure_super_key(&key)?;
        let key = jstr_to_cstr(env, &key)?;
        let module_path = jstr_to_cstr(env, &module_path_jstr)?;
        let args = jstr_to_cstr(env, &args_jstr)?;
        _load_kernel_patch_module(&key, &module_path, &args)
    })
}

fn _load_kernel_patch_module(key: &CStr, module_path: &CStr, args: &CStr) -> Result<c_long> {
    get_sc()?.sc_kpm_load(key, module_path.as_ptr(), args.as_ptr(), null_mut())
}

fn native_control_kernel_patch_module<'a>(
    mut env: JNIEnv<'a>,
    _: JClass,
    key: JString,
    module_name_jstr: JString,
    control_args_jstr: JString,
) -> jobject {
    jni_wrap(&mut env, null_mut(), |env| {
        ensure_super_key(&key)?;
        let key = jstr_to_cstr(env, &key)?;
        let module_name = jstr_to_cstr(env, &module_name_jstr)?;
        let args = jstr_to_cstr(env, &control_args_jstr)?;
        let mut buf = [c_char::default(); 4096];
        let ptr = buf.as_mut_ptr();

        let rc = get_sc()?.sc_kpm_control(
            &key,
            module_name.as_ptr(),
            args.as_ptr(),
            ptr,
            buf.len() as c_long,
        )?;
        let cls = env.find_class("me/bmax/apatch/Natives$KPMCtlRes")?;

        let obj = env.new_object(cls, "()V", &[])?;

        let out_msg_str =
            unsafe { std::ffi::CStr::from_ptr(buf.as_ptr().cast()).to_string_lossy() };
        let j_out_msg = env.new_string(out_msg_str)?;

        env.set_field(&obj, "rc", "J", JValue::Long(rc))?;
        env.set_field(
            &obj,
            "outMsg",
            "Ljava/lang/String;",
            JValue::Object(&j_out_msg),
        )?;
        Ok(obj.as_raw())
    })
}

fn native_unload_kernel_patch_module<'a>(
    mut env: JNIEnv<'a>,
    _: JClass,
    key: JString,
    module_name_jstr: JString,
) -> jlong {
    jni_wrap(&mut env, -1, |env| {
        ensure_super_key(&key)?;
        let key = jstr_to_cstr(env, &key)?;
        let module_name = jstr_to_cstr(env, &module_name_jstr)?;
        let res = get_sc()?.sc_kpm_unload(&key, module_name.as_ptr(), null_mut())?;
        if res != 0 {
            return Err(Error::from_raw_os_error(res as i32).into());
        }
        Ok(res as jlong)
    })
}

fn native_kernel_patch_module_num<'a>(mut env: JNIEnv<'a>, _: JClass, key: JString) -> jint {
    jni_wrap(&mut env, -1, |env| {
        ensure_super_key(&key)?;
        let key = jstr_to_cstr(env, &key)?;
        Ok(get_sc()?.sc_kpm_nums(&key)? as jint)
    })
}

fn native_kernel_patch_module_list<'a>(
    mut env: JNIEnv<'a>,
    _: JClass,
    key: JString,
) -> JString<'a> {
    jni_wrap(&mut env, JString::default(), |env| {
        ensure_super_key(&key)?;
        let key = jstr_to_cstr(env, &key)?;
        let mut buf = [c_char::default(); 4096];

        let ptr = buf.as_mut_ptr();
        get_sc()?.sc_kpm_list(&key, ptr, buf.len() as i32)?;
        let out_msg_str =
            unsafe { std::ffi::CStr::from_ptr(buf.as_ptr().cast()).to_string_lossy() };
        Ok(env.new_string(out_msg_str)?)
    })
}

fn native_kernel_patch_module_info<'a>(
    mut env: JNIEnv<'a>,
    _: JClass,
    key: JString,
    module_name_jstr: JString,
) -> JString<'a> {
    jni_wrap(&mut env, JString::default(), |env| {
        ensure_super_key(&key)?;
        let key = jstr_to_cstr(env, &key)?;
        let module_name = jstr_to_cstr(env, &module_name_jstr)?;
        let mut buf = [c_char::default(); 1024];

        let ptr = buf.as_mut_ptr();
        get_sc()?.sc_kpm_info(&key, module_name.as_ptr(), ptr, buf.len() as i32)?;
        let out_msg_str =
            unsafe { std::ffi::CStr::from_ptr(buf.as_ptr().cast()).to_string_lossy() };
        Ok(env.new_string(out_msg_str)?)
    })
}

fn native_grant_su(
    mut env: JNIEnv,
    _: JClass,
    key: JString,
    uid: jint,
    to_uid: jint,
    sctx: JString,
) -> jlong {
    jni_wrap(&mut env, -1, |env| {
        ensure_super_key(&key)?;
        let key = jstr_to_cstr(env, &key)?;
        let sctx_str = jstr_to_cstr(env, &sctx)?;
        let mut profile = SuProfile::new(uid, to_uid, &sctx_str.to_string_lossy());
        get_sc()?.sc_su_grant_uid(&key, &mut profile)
    })
}

fn native_revoke_su(mut env: JNIEnv, _: JClass, key: JString, uid: jint) -> jlong {
    jni_wrap(&mut env, -1, |env| {
        ensure_super_key(&key)?;
        let key = jstr_to_cstr(env, &key)?;
        get_sc()?.sc_su_revoke_uid(&key, uid as u32)
    })
}

fn native_su_path<'a>(mut env: JNIEnv<'a>, _: JClass, key: JString) -> JString<'a> {
    jni_wrap(&mut env, JString::default(), |env| {
        ensure_super_key(&key)?;
        let key = jstr_to_cstr(env, &key)?;
        let mut buf = [c_char::default(); SU_PATH_MAX_LEN];
        let ptr = buf.as_mut_ptr();
        get_sc()?.sc_su_get_path(&key, ptr, buf.len() as i32)?;
        let out_msg_str =
            unsafe { std::ffi::CStr::from_ptr(buf.as_ptr().cast()).to_string_lossy() };
        Ok(env.new_string(out_msg_str)?)
    })
}

fn native_reset_su_path<'a>(
    mut env: JNIEnv<'a>,
    _: JClass,
    key: JString,
    su_path_jstr: JString,
) -> jboolean {
    jni_wrap(&mut env, JNI_FALSE, |env| {
        ensure_super_key(&key)?;
        let key = jstr_to_cstr(env, &key)?;
        let path = jstr_to_cstr(env, &su_path_jstr)?;
        Ok(get_sc()?.sc_su_reset_path(&key, path.as_ptr())? as jboolean)
    })
}

fn native_install_kpm_module(
    mut env: JNIEnv,
    _: JClass,
    key_jstr: JString,
    module_path_jstr: JString,
    args_jstr: JString,
) -> jlong {
    jni_wrap(&mut env, -1, |env| {
        ensure_super_key(&key_jstr)?;
        let path: String = env.get_string(&module_path_jstr)?.into();
        let path = Path::new(&path);
        let data = fs::read(path)?;
        let key = jstr_to_cstr(env, &key_jstr)?;
        let module_path = jstr_to_cstr(env, &module_path_jstr)?;
        let args = jstr_to_cstr(env, &args_jstr)?;

        let content = String::from_utf8_lossy(&data);
        let name = _find_kpm_field(&content, "name=")?;
        _uninstall_kernel_patch_module(&CString::new(name)?)?;

        let res = _load_kernel_patch_module(&key, &module_path, &args)?;
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
            fs::create_dir_all(kpms_path)?;
        }
        let file_name = &path
            .file_name()
            .ok_or_else(|| anyhow!("no file name"))?
            .to_string_lossy()
            .to_string();
        let dest_path = kpms_path.join(file_name);
        fs::copy(path, dest_path)?;
        fs::remove_file(path)?;
        let config_path = kpms_path.join("config");
        let mut config = OpenOptions::new()
            .append(true)
            .create(true)
            .open(config_path)?;
        info.extend_from_slice(file_name.as_bytes());
        config.write_all(&info)?;
        Ok(res as jlong)
    })
}

fn native_uninstall_kernel_patch_module<'a>(
    mut env: JNIEnv<'a>,
    _: JClass,
    key_jstr: JString,
    module_name_jstr: JString,
) -> jlong {
    jni_wrap(&mut env, -1, |env| {
        ensure_super_key(&key_jstr)?;
        let name = jstr_to_cstr(env, &module_name_jstr)?;
        let key = jstr_to_cstr(env, &key_jstr)?;
        let module_name = jstr_to_cstr(env, &module_name_jstr)?;
        _uninstall_kernel_patch_module(&name)?;
        get_sc()?.sc_kpm_unload(&key, module_name.as_ptr(), null_mut())
    })
}

fn _uninstall_kernel_patch_module(name: &CString) -> Result<()> {
    let mut name_buf = [0u8; 32];
    let name_bytes = name.as_bytes();
    let len = name_bytes.len().min(32);
    name_buf[..len].copy_from_slice(&name_bytes[..len]);
    let kpms_path = Path::new("/data/adb/ap/kpms/");
    if !kpms_path.exists() {
        return Ok(());
    }
    let config_path = kpms_path.join("config");
    let mut reader = BufReader::new(File::open(&config_path)?);

    let mut kept = Vec::new();
    let mut record = Vec::new();
    while reader.read_until(b'\n', &mut record)? > 0 {
        if record.len() < 35 {
            record.clear();
            continue;
        }
        if record[0..32] == name_buf {
            let filename = String::from_utf8_lossy(&record[34..record.len() - 1]).to_string();
            let path = Path::new("/data/adb/ap/kpms/").join(filename);
            if path.exists() {
                fs::remove_file(path)?;
            }
            record.clear();
            continue;
        }
        kept.extend(&record);
        record.clear();
    }

    let mut writer = BufWriter::new(File::create(config_path)?);
    writer.write_all(&kept)?;
    Ok(())
}

fn native_change_installed_kpm_module_state<'a>(
    mut env: JNIEnv<'a>,
    _: JClass,
    key_jstr: JString,
    module_name_jstr: JString,
    enabled: jboolean,
) -> jlong {
    jni_wrap(&mut env, -1, |env| {
        ensure_super_key(&key_jstr)?;
        let name = jstr_to_cstr(env, &module_name_jstr)?;
        let mut name_buf = [0u8; 32];
        let name_bytes = name.as_bytes();
        let len = name_bytes.len().min(32);
        let key = jstr_to_cstr(env, &key_jstr)?;
        name_buf[..len].copy_from_slice(&name_bytes[..len]);
        let kpms_path = Path::new("/data/adb/ap/kpms/");
        if !kpms_path.exists() {
            return Ok(0);
        }
        let config_path = kpms_path.join("config");
        let mut reader = BufReader::new(File::open(&config_path)?);

        let mut module_path = String::default();

        let mut kept = Vec::new();
        let mut record = Vec::new();
        while reader.read_until(b'\n', &mut record)? > 0 {
            if record.len() < 35 {
                record.clear();
                continue;
            }
            if record[0..32] == name_buf {
                record[32] = if enabled != 0 { 0b01 } else { 0b00 };

                module_path = kpms_path
                    .join(String::from_utf8_lossy(&record[34..]).to_string().trim())
                    .to_string_lossy()
                    .to_string();
                debug!("{}", module_path)
            }
            debug!("record len: {}", record.len());
            debug!("record bytes: {:?}", record);

            kept.extend(&record);
            record.clear();
        }

        debug!("kept bytes: {:?}", kept);

        debug!("writing config");
        let mut writer = BufWriter::new(File::create(config_path)?);
        writer.write_all(&kept)?;
        writer.flush()?;

        debug!("done writing config");

        if enabled != 0 {
            let args = c"";
            _load_kernel_patch_module(&key, &CString::new(module_path)?, args)
        } else {
            Ok(0)
        }
    })
}

fn native_installed_kpm_list<'a>(mut env: JNIEnv<'a>, _: JClass) -> jobjectArray {
    jni_wrap(&mut env, null_mut(), |env| {
        let mut kpm_list = Vec::new();
        let kpm_dir = Path::new("/data/adb/ap/kpms/");
        debug!("kpm dir exists:{}", kpm_dir.exists());
        if kpm_dir.exists() {
            let config = kpm_dir.join("config");
            debug!("Reading kpm config from {}", config.to_string_lossy());
            debug!("config exists:{}", config.exists());
            if config.exists() {
                let mut reader = BufReader::new(File::open(config)?);
                let mut record = Vec::new();
                while reader.read_until(b'\n', &mut record)? > 0 {
                    if record.len() < 35 {
                        record.clear();
                        continue;
                    }
                    let name = String::from_utf8_lossy(&record[0..32])
                        .trim_end_matches('\0')
                        .to_string();
                    kpm_list.push(env.new_string(name)?);
                    record.clear();
                }
            }
        }
        let array = env.new_object_array(
            kpm_list.len() as i32,
            "java/lang/String",
            JString::default(),
        )?;
        for (i, jstr) in kpm_list.iter().enumerate() {
            env.set_object_array_element(&array, i as i32, jstr)?;
        }
        Ok(array.as_raw())
    })
}

fn _find_kpm_field<'a>(content: &'a str, prefix: &str) -> Result<&'a str> {
    let start = content
        .find(prefix)
        .ok_or_else(|| anyhow!("prefix not found"))?;
    let after_prefix = start + prefix.len();

    let end = content[after_prefix..]
        .find(|c: char| ['\0', '\n'].contains(&c))
        .map(|pos| after_prefix + pos)
        .unwrap_or_else(|| content.len());

    Ok(&content[after_prefix..end])
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

    if let Err(e) = init_supercall() {
        warn!("Failed to initialize SuperCall: {}", e);
        return JNI_ERR;
    }

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
        ),
    ];

    // 4. 注册方法
    if env.register_native_methods(clazz, &methods).is_err() {
        return JNI_ERR;
    }

    JNI_VERSION_1_6
}
