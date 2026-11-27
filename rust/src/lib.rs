use jni::objects::JClass;
use jni::sys::{jdouble, jint, jstring};
use jni::JNIEnv;

#[no_mangle]
pub extern "system" fn Java_com_tanishk_classesreborn_bridge_RustBridge_ping<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jstring {
    env.new_string("Rust bridge active")
        .expect("Failed to create Java string")
        .into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_tanishk_classesreborn_bridge_RustBridge_computeDamage(
    _env: JNIEnv,
    _class: JClass,
    base_damage: jdouble,
    might_points: jint,
) -> jdouble {
    base_damage + (might_points as f64) * 0.5
}
