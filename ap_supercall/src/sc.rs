use std::ffi::{CStr, c_long};

use anyhow::{Result, bail};
use libc::syscall;

#[inline]
pub fn error_handler(ret: c_long) -> Result<c_long> {
    if ret < 0 {
        let code = ret.abs() as i32;
        bail!(std::io::Error::from_raw_os_error(code))
    } else {
        Ok(ret)
    }
}

#[macro_export]
macro_rules! sc_call {
    ($self:ident, $cmd:expr, $key:expr) => {
        {
            let cmd_val = $self.ver_and_cmd($cmd);
            let key_val = $key;
            $crate::sc::error_handler(unsafe {
                syscall(
                    __NR_SUPERCALL,
                    key_val,
                    cmd_val
                )
            })
        }
    };

    ($self:ident, $cmd:expr, $key:expr, $($arg:expr),*) => {
        {
            let cmd_val = $self.ver_and_cmd($cmd);
            let key_val = $key;
            $( let _ = &$arg; )* $crate::sc::error_handler(unsafe {
                syscall(
                    __NR_SUPERCALL,
                    key_val,
                    cmd_val,
                    $($arg),*
                )
            })
        }
    };
}
