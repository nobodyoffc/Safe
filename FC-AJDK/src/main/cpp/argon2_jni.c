#include <jni.h>
#include <stdlib.h>
#include <string.h>

#include "argon2.h"
#include "core.h"

/*
 * NativeArgon2.argon2id(password, salt, iterations, memoryKib, parallelism, outLen):
 * the raw Argon2id v1.3 hash, or null if the inputs are out of range or memory runs out.
 */
JNIEXPORT jbyteArray JNICALL
Java_com_fc_fc_1ajdk_core_crypto_NativeArgon2_argon2id(JNIEnv *env, jclass clazz,
                                                       jbyteArray password, jbyteArray salt,
                                                       jint iterations, jint memoryKib,
                                                       jint parallelism, jint outLen) {
    (void) clazz;
    if (password == NULL || salt == NULL || outLen <= 0) return NULL;

    jsize pwdLen = (*env)->GetArrayLength(env, password);
    jsize saltLen = (*env)->GetArrayLength(env, salt);
    // Copied into buffers this code owns, so the password can be wiped afterwards.
    uint8_t *pwd = malloc(pwdLen > 0 ? (size_t) pwdLen : 1);
    uint8_t *slt = malloc(saltLen > 0 ? (size_t) saltLen : 1);
    uint8_t *out = malloc((size_t) outLen);
    jbyteArray result = NULL;
    if (pwd == NULL || slt == NULL || out == NULL) goto done;

    (*env)->GetByteArrayRegion(env, password, 0, pwdLen, (jbyte *) pwd);
    (*env)->GetByteArrayRegion(env, salt, 0, saltLen, (jbyte *) slt);

    int rc = argon2id_hash_raw((uint32_t) iterations, (uint32_t) memoryKib, (uint32_t) parallelism,
                               pwd, (size_t) pwdLen, slt, (size_t) saltLen, out, (size_t) outLen);
    if (rc == ARGON2_OK) {
        result = (*env)->NewByteArray(env, outLen);
        if (result != NULL) (*env)->SetByteArrayRegion(env, result, 0, outLen, (const jbyte *) out);
    }

done:
    if (pwd != NULL) {
        secure_wipe_memory(pwd, (size_t) pwdLen);
        free(pwd);
    }
    free(slt);
    if (out != NULL) {
        secure_wipe_memory(out, (size_t) outLen);
        free(out);
    }
    return result;
}
