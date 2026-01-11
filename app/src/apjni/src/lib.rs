use anyhow::{Result, anyhow};
use ap_supercall::su_profile::SuProfile;
use ap_supercall::supercall::SuperCall;
use jni::objects::{JClass, JIntArray, JString, JValue};
use jni::sys::{JNI_ERR, JNI_VERSION_1_6, jboolean, jint, jlong, jobject};
use jni::{JNIEnv, JavaVM};
use libc::{c_char, c_long, uid_t};
use log::debug;
use std::ffi::{CStr, CString, c_void};
use std::io::Error;
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

// 辅助函数：JString -> CString
fn jstr_to_cstr(env: &mut JNIEnv, s: JString) -> CString {
    let r_str: String = env.get_string(&s).expect("Invalid jstring").into();
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
    let key = &jstr_to_cstr(&mut env, key);
    (get_sc().sc_ready(key)) as jboolean
}

fn native_kernel_patch_version(mut env: JNIEnv, _: JClass, key: JString) -> jlong {
    ensure_super_key(&key);
    let key = &jstr_to_cstr(&mut env, key);
    ret_to_jlong(get_sc().sc_kp_ver(key))
}

fn native_kernel_patch_build_time<'a>(mut env: JNIEnv<'a>, _: JClass, key: JString) -> JString<'a> {
    ensure_super_key(&key);
    let key = &jstr_to_cstr(&mut env, key);
    let mut buf = [0u8; 4096];
    let _ = get_sc().sc_get_build_time(key, buf.as_mut_ptr() as *mut c_char, buf.len());
    env.new_string(unsafe { CStr::from_ptr(buf.as_ptr().cast()) }.to_string_lossy())
        .unwrap()
}

fn native_su(mut env: JNIEnv, _: JClass, key: JString, to_uid: jint, sctx: JString) -> jlong {
    ensure_super_key(&key);

    let c_key = jstr_to_cstr(&mut env, key);
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
    let c_key = jstr_to_cstr(&mut env, key);
    ret_to_jlong(get_sc().sc_set_ap_mod_exclude(&c_key, uid as i64, exclude)) as jint
}

fn native_get_uid_exclude(mut env: JNIEnv, _: JClass, key: JString, uid: jint) -> jint {
    ensure_super_key(&key);
    let key = jstr_to_cstr(&mut env, key);
    debug!("[native_get_uid_exclude] uid: {}", uid);
    ret_to_jlong(
        get_sc()
            .sc_get_ap_mod_exclude(&key, uid as uid_t)
            .map_err(|_| anyhow!(Error::from_raw_os_error(0))),
    ) as jint
}

fn native_su_uids<'a>(mut env: JNIEnv<'a>, _: JClass, key: JString) -> JIntArray<'a> {
    ensure_super_key(&key);
    let key = jstr_to_cstr(&mut env, key);
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
    let key = jstr_to_cstr(&mut env, key);
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
    let key = jstr_to_cstr(&mut env, key);
    let module_path = jstr_to_cstr(&mut env, module_path_jstr);
    let args = jstr_to_cstr(&mut env, args_jstr);
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
    let key = jstr_to_cstr(&mut env, key);
    let module_name = jstr_to_cstr(&mut env, module_name_jstr);
    let args = jstr_to_cstr(&mut env, control_args_jstr);
    let mut buf = [0u8; 4096];
    let rc = ret_to_jlong(get_sc().sc_kpm_control(
        &key,
        module_name.as_ptr(),
        args.as_ptr(),
        buf.as_mut_ptr(),
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
    let key = jstr_to_cstr(&mut env, key);
    let module_name = jstr_to_cstr(&mut env, module_name_jstr);
    ret_to_jlong(get_sc().sc_kpm_unload(&key, module_name.as_ptr(), null_mut()))
}

fn native_kernel_patch_module_num<'a>(mut env: JNIEnv<'a>, _: JClass, key: JString) -> jint {
    ensure_super_key(&key);
    let key = jstr_to_cstr(&mut env, key);
    ret_to_jlong(get_sc().sc_kpm_nums(&key)) as jint
}

fn native_kernel_patch_module_list<'a>(
    mut env: JNIEnv<'a>,
    _: JClass,
    key: JString,
) -> JString<'a> {
    ensure_super_key(&key);
    let key = jstr_to_cstr(&mut env, key);
    let mut buf = [0u8; 4096];
    get_sc()
        .sc_kpm_list(&key, buf.as_mut_ptr(), buf.len() as i32)
        .unwrap();
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
    let key = jstr_to_cstr(&mut env, key);
    let module_name = jstr_to_cstr(&mut env, module_name_jstr);
    let mut buf = [0u8; 1024];
    get_sc()
        .sc_kpm_info(
            &key,
            module_name.as_ptr(),
            buf.as_mut_ptr(),
            buf.len() as i32,
        )
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
    let key = jstr_to_cstr(&mut env, key);
    let sctx_str = jstr_to_cstr(&mut env, sctx);
    let mut profile = SuProfile::new(uid as i32, to_uid, &sctx_str.to_string_lossy());
    ret_to_jlong(get_sc().sc_su_grant_uid(&key, &mut profile))
}

fn native_revoke_su(mut env: JNIEnv, _: JClass, key: JString, uid: jint) -> jlong {
    ensure_super_key(&key);
    let key = jstr_to_cstr(&mut env, key);
    ret_to_jlong(get_sc().sc_su_revoke_uid(&key, uid as u32))
}

fn native_su_path<'a>(mut env: JNIEnv<'a>, _: JClass, key: JString) -> JString<'a> {
    ensure_super_key(&key);
    let key = jstr_to_cstr(&mut env, key);
    let mut buf = [0u8; SU_PATH_MAX_LEN];
    get_sc()
        .sc_su_get_path(&key, buf.as_mut_ptr(), buf.len() as i32)
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
    let key = jstr_to_cstr(&mut env, key);
    let path = jstr_to_cstr(&mut env, su_path_jstr);
    get_sc()
        .sc_su_reset_path(&key, path.as_ptr())
        .map(|ret| ret as u8)
        .unwrap_or(0u8)
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
    ];

    // 4. 注册方法
    if let Err(_) = env.register_native_methods(clazz, &methods) {
        return JNI_ERR;
    }

    JNI_VERSION_1_6
}
