#ifndef LEDGERFLOW_CACTUS_H
#define LEDGERFLOW_CACTUS_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * C API contract for Cactus Compute's libcactus.so (Mirage 22 MB VLM).
 *
 * Drop-in verification:
 *   When placing libcactus.so in app/src/main/jniLibs/arm64-v8a/libcactus.so,
 *   confirm the exported symbols match these signatures using:
 *     nm -D app/src/main/jniLibs/arm64-v8a/libcactus.so | grep cactus_
 *
 * If your libcactus.so build returns an allocated char* from cactus_describe
 * instead of filling out_buf, update cactus_describe_fn in cactus_jni.cpp.
 */
typedef struct cactus_model cactus_model_t;

typedef cactus_model_t* (*cactus_load_fn)(
        const uint8_t* model_bytes,
        size_t model_len
);

typedef int (*cactus_describe_fn)(
        cactus_model_t* model,
        const uint8_t* rgb_pixels,
        int width,
        int height,
        const char* options_json,
        char* out_buf,
        size_t out_buf_len
);

typedef void (*cactus_free_fn)(cactus_model_t* model);

#ifdef __cplusplus
}
#endif

#endif // LEDGERFLOW_CACTUS_H
