use crate::supercall_map::SUPERCALL_SCONTEXT_LEN;

#[repr(C)]
pub struct SuProfile {
    pub uid: i32,
    pub to_uid: i32,
    pub scontext: [u8; SUPERCALL_SCONTEXT_LEN as usize],
}

impl SuProfile {
    pub fn new(uid: i32, to_uid: i32, scontext: &str) -> Self {
        Self {
            uid,
            to_uid,
            scontext: convert_string_to_u8_array(scontext),
        }
    }
}

fn convert_string_to_u8_array(s: &str) -> [u8; SUPERCALL_SCONTEXT_LEN as usize] {
    let mut u8_array = [0u8; SUPERCALL_SCONTEXT_LEN as usize];
    let bytes = s.as_bytes();
    let len = usize::min(SUPERCALL_SCONTEXT_LEN as usize, bytes.len());
    u8_array[..len].copy_from_slice(&bytes[..len]);
    u8_array
}
