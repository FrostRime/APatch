use std::{
    ffi::CStr,
    sync::atomic::{Ordering, compiler_fence},
};

use anyhow::{Result, bail};
use libc::*;
use log::{debug, warn};

use crate::{sc_call, su_profile::SuProfile, supercall_map::*};

macro_rules! sc_impl {
    ($struct_name:ident {
        $(
            $vis:vis fn $name:ident ( $($arg:ident : $ty:ty),* ) use $cmd:expr
        )* }) => {
        impl $struct_name {
            $(
                #[inline]
                $vis fn $name(&self, key: &CStr, $($arg: $ty),*) -> Result<c_long> {
                    self.check_key(key)?;
                    sc_call!(self, $cmd, key.as_ptr() as c_long, $($arg as c_long),*)
                }
            )*
        }
    };
}

pub struct SuperCall {
    version_code: c_long,
}

impl SuperCall {
    #[inline]
    fn check_key(&self, key: &CStr) -> Result<()> {
        if key.to_bytes().is_empty() {
            let code = EINVAL;
            bail!(std::io::Error::from_raw_os_error(code))
        }
        Ok(())
    }

    #[inline]
    pub fn new(major: c_long, minor: c_long, patch: c_long) -> Self {
        let version_code = ((major << 16) | (minor << 8) | patch) as c_long;
        Self { version_code }
    }

    #[inline]
    fn ver_and_cmd(&self, cmd: c_long) -> c_long {
        (self.version_code << 32) | (0x1158 << 16) | (cmd & 0xFFFF)
    }

    #[inline]
    pub fn sc_ready(&self, key: &CStr) -> bool {
        self.sc_hello(key)
            .map(|ret| ret == SUPERCALL_HELLO_MAGIC)
            .unwrap_or(false)
    }

    #[inline]
    pub fn sc_kstorage_write(
        &self,
        key: &CStr,
        gid: i32,
        did: i64,
        data: *mut c_void,
        offset: i32,
        dlen: i32,
    ) -> Result<c_long> {
        let offset_len = ((offset as i64) << 32) | (dlen as i64);
        self._sc_kstorage_write(key, gid, did, data, offset_len)
    }

    #[inline]
    pub fn sc_set_ap_mod_exclude(&self, key: &CStr, uid: i64, exclude: i32) -> Result<c_long> {
        if exclude == 1 {
            self.sc_kstorage_write(
                key,
                KSTORAGE_EXCLUDE_LIST_GROUP,
                uid,
                &exclude as *const i32 as *mut c_void,
                0,
                size_of::<i32>() as i32,
            )
        } else {
            self.sc_kstorage_remove(key, KSTORAGE_EXCLUDE_LIST_GROUP, uid)
        }
    }

    #[inline]
    pub fn sc_get_ap_mod_exclude(&self, key: &CStr, uid: uid_t) -> Result<c_long> {
        let did = (uid as u32) as c_long;
        let mut exclude: i32 = 0;

        let ptr = &mut exclude as *mut i32 as *mut c_void;

        let result = self.sc_kstorage_read(
            key,
            KSTORAGE_EXCLUDE_LIST_GROUP,
            did,
            ptr,
            0,
            std::mem::size_of::<i32>() as i32,
        );

        match result {
            Ok(_) => {
                compiler_fence(Ordering::SeqCst);
                Ok(exclude as c_long)
            }
            Err(e) => {
                debug!("sc_kstorage_read failed: {:?}", e);
                Ok(0)
            }
        }
    }

    #[inline]
    pub fn sc_kstorage_read(
        &self,
        key: &CStr,
        gid: i32,
        did: c_long,
        out_data: *mut c_void,
        offset: c_long,
        dlen: i32,
    ) -> Result<c_long> {
        let packed_off_len = ((offset as c_long) << 32) | (dlen as c_long);
        self._sc_kstorage_read(key, gid, did, out_data, packed_off_len)
    }

    #[inline]
    pub fn sc_su_get_safemode(&self, key: &CStr) -> c_long {
        if key.to_bytes().is_empty() {
            warn!("[sc_su_get_safemode] null superkey, tell apd we are not in safemode!");
            return 0;
        }

        let key_ptr = key.as_ptr();
        if key_ptr.is_null() {
            warn!("[sc_su_get_safemode] superkey pointer is null!");
            return 0;
        }

        sc_call!(self, SUPERCALL_SU_GET_SAFEMODE, key_ptr as c_long).unwrap_or_else(|e| {
            e.downcast_ref::<std::io::Error>()
                .unwrap()
                .raw_os_error()
                .unwrap() as c_long
        })
    }
}

sc_impl! {
    SuperCall {
        pub fn sc_hello() use SUPERCALL_HELLO
        pub fn sc_klog(msg: *const c_char) use SUPERCALL_KLOG
        pub fn sc_get_build_time(buf: *mut c_char, len: size_t) use SUPERCALL_BUILD_TIME
        pub fn sc_kp_ver() use SUPERCALL_KERNELPATCH_VER
        pub fn sc_k_ver() use SUPERCALL_KERNEL_VER
        pub fn sc_su(profile: *mut SuProfile) use SUPERCALL_SU
        pub fn sc_su_task(tid: pid_t, profile: *mut SuProfile) use SUPERCALL_SU_TASK

        // KStorage 接口
        fn _sc_kstorage_write(gid: i32, did: c_long, data: *mut c_void, offset_len: c_long) use SUPERCALL_KSTORAGE_WRITE
        pub fn _sc_kstorage_read(gid: i32, did: c_long, out_data: *mut c_void, packed_off_len: c_long) use SUPERCALL_KSTORAGE_READ
        pub fn sc_kstorage_list_ids(gid: i32, ids: *mut c_long, ids_len: i32) use SUPERCALL_KSTORAGE_LIST_IDS
        pub fn sc_kstorage_remove(gid: i32, did: c_long) use SUPERCALL_KSTORAGE_REMOVE

        // Su 权限管理
        pub fn sc_su_grant_uid(profile: *mut SuProfile) use SUPERCALL_SU_GRANT_UID
        pub fn sc_su_revoke_uid(uid: uid_t) use SUPERCALL_SU_REVOKE_UID
        pub fn sc_su_uid_nums() use SUPERCALL_SU_NUMS
        pub fn sc_su_allow_uids(buf: *mut uid_t, num: i32) use SUPERCALL_SU_LIST
        pub fn sc_su_uid_profile(uid: uid_t, out_profile: *mut SuProfile) use SUPERCALL_SU_PROFILE
        pub fn sc_su_get_path(out_path: *mut c_char, path_len: i32) use SUPERCALL_SU_GET_PATH
        pub fn sc_su_reset_path(path: *const c_char) use SUPERCALL_SU_RESET_PATH

        // KPM 模块管理
        pub fn sc_kpm_load(path: *const c_char, args: *const c_char, reserved: *mut c_void) use SUPERCALL_KPM_LOAD
        pub fn sc_kpm_control(name: *const c_char, ctl_args: *const c_char, out_msg: *mut c_char, outlen: c_long) use SUPERCALL_KPM_CONTROL
        pub fn sc_kpm_unload(name: *const c_char, reserved: *mut c_void) use SUPERCALL_KPM_UNLOAD
        pub fn sc_kpm_nums() use SUPERCALL_KPM_NUMS
        pub fn sc_kpm_list(names_buf: *mut c_char, buf_len: i32) use SUPERCALL_KPM_LIST
        pub fn sc_kpm_info(name: *const c_char, buf: *mut c_char, buf_len: i32) use SUPERCALL_KPM_INFO

        // 安全与测试
        pub fn sc_skey_get(out_key: *mut c_char, outlen: i32) use SUPERCALL_SKEY_GET
        pub fn sc_skey_set(new_key: *const c_char) use SUPERCALL_SKEY_SET
        pub fn sc_skey_root_enable(enable: bool) use SUPERCALL_SKEY_ROOT_ENABLE
        pub fn sc_bootlog() use SUPERCALL_BOOTLOG
        pub fn sc_panic() use SUPERCALL_PANIC
        pub fn sc_test(a1: c_long, a2: c_long, a3: c_long) use SUPERCALL_TEST
    }
}
