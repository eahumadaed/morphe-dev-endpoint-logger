/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.logging.responseLogging

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags

internal const val JACKSON_CLASS = "/fasterxml/jackson/core/"

internal object InputStreamFingerprintPrimary : Fingerprint(
    definingClass = JACKSON_CLASS,
    parameters = listOf("Ljava/io/InputStream"),
    custom = { methodDef, _ ->
        !AccessFlags.STATIC.isSet(methodDef.accessFlags) && methodDef.returnType.contains(JACKSON_CLASS)
    },
)

internal object InputStreamFingerprintPrimaryStatic : Fingerprint(
    definingClass = JACKSON_CLASS,
    parameters = listOf("Ljava/io/InputStream"),
    custom = { methodDef, _ ->
        AccessFlags.STATIC.isSet(methodDef.accessFlags) && methodDef.returnType.contains(JACKSON_CLASS)
    },
)

internal object InputStreamFingerprintByName : Fingerprint(
    parameters = listOf("Ljava/io/InputStream"),
    custom = { methodDef, _ ->
        !AccessFlags.STATIC.isSet(methodDef.accessFlags) &&
            methodDef.name.lowercase().contains("createparser") &&
            methodDef.returnType.startsWith("L")
    },
)

internal object InputStreamFingerprintByReturnType : Fingerprint(
    parameters = listOf("Ljava/io/InputStream"),
    custom = { methodDef, _ ->
        !AccessFlags.STATIC.isSet(methodDef.accessFlags) &&
            (methodDef.returnType.contains(JACKSON_CLASS) || methodDef.returnType.contains("JsonParser"))
    },
)

internal object InputStreamFingerprintByReturnTypeStatic : Fingerprint(
    parameters = listOf("Ljava/io/InputStream"),
    custom = { methodDef, _ ->
        AccessFlags.STATIC.isSet(methodDef.accessFlags) &&
            (methodDef.returnType.contains(JACKSON_CLASS) || methodDef.returnType.contains("JsonParser"))
    },
)

internal object InputStreamFingerprintAnyInstance : Fingerprint(
    parameters = listOf("Ljava/io/InputStream"),
    custom = { methodDef, _ ->
        !AccessFlags.STATIC.isSet(methodDef.accessFlags) &&
            methodDef.returnType.startsWith("L")
    },
)

internal object InputStreamFingerprintAnyStatic : Fingerprint(
    parameters = listOf("Ljava/io/InputStream"),
    custom = { methodDef, _ ->
        AccessFlags.STATIC.isSet(methodDef.accessFlags) &&
            methodDef.returnType.startsWith("L")
    },
)

internal object ByteArrayFingerprintInstance : Fingerprint(
    parameters = listOf("[B"),
    custom = { methodDef, _ ->
        !AccessFlags.STATIC.isSet(methodDef.accessFlags) &&
            methodDef.returnType.startsWith("L")
    },
)

internal object ByteArrayFingerprintStatic : Fingerprint(
    parameters = listOf("[B"),
    custom = { methodDef, _ ->
        AccessFlags.STATIC.isSet(methodDef.accessFlags) &&
            methodDef.returnType.startsWith("L")
    },
)

internal object StringFingerprintInstance : Fingerprint(
    parameters = listOf("Ljava/lang/String;"),
    custom = { methodDef, _ ->
        !AccessFlags.STATIC.isSet(methodDef.accessFlags) &&
            (methodDef.name.lowercase().contains("parse") || methodDef.name.lowercase().contains("fromjson")) &&
            methodDef.returnType.startsWith("L")
    },
)

internal object StringFingerprintStatic : Fingerprint(
    parameters = listOf("Ljava/lang/String;"),
    custom = { methodDef, _ ->
        AccessFlags.STATIC.isSet(methodDef.accessFlags) &&
            (methodDef.name.lowercase().contains("parse") || methodDef.name.lowercase().contains("fromjson")) &&
            methodDef.returnType.startsWith("L")
    },
)

internal object StringBuilderFingerprintInstance : Fingerprint(
    parameters = listOf("Ljava/lang/StringBuilder;"),
    custom = { methodDef, _ ->
        !AccessFlags.STATIC.isSet(methodDef.accessFlags) &&
            methodDef.returnType.startsWith("L")
    },
)

internal object StringBuilderFingerprintStatic : Fingerprint(
    parameters = listOf("Ljava/lang/StringBuilder;"),
    custom = { methodDef, _ ->
        AccessFlags.STATIC.isSet(methodDef.accessFlags) &&
            methodDef.returnType.startsWith("L")
    },
)

internal object ByteBufferFingerprintInstance : Fingerprint(
    parameters = listOf("Ljava/nio/ByteBuffer;"),
    custom = { methodDef, _ ->
        !AccessFlags.STATIC.isSet(methodDef.accessFlags) &&
            methodDef.returnType.startsWith("L")
    },
)

internal object ByteBufferFingerprintStatic : Fingerprint(
    parameters = listOf("Ljava/nio/ByteBuffer;"),
    custom = { methodDef, _ ->
        AccessFlags.STATIC.isSet(methodDef.accessFlags) &&
            methodDef.returnType.startsWith("L")
    },
)

private fun injectOrNull(
    fingerprint: Fingerprint,
    register: String,
    smaliSnippet: String,
): String? =
    runCatching {
        fingerprint.method.addInstructions(
            0,
            smaliSnippet.trimIndent(),
        )
        register
    }.getOrNull()

@Suppress("unused")
val responseLoggingPatch =
    bytecodePatch(
        name = "Endpoint response logger",
        description = "Logs parser InputStream payloads to endpoint response logs",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            val matchedHooks = mutableListOf<String>()

            injectOrNull(
                InputStreamFingerprintPrimary,
                "primary_fasterxml_instance",
                """
                invoke-static {p1}, $PATCHES_DESCRIPTOR/ResponseLogger;->saveInputStreamPrimary(Ljava/io/InputStream;)Ljava/io/InputStream;
                move-result-object p1
                """,
            )?.let { matchedHooks.add(it) }

            injectOrNull(
                InputStreamFingerprintPrimaryStatic,
                "primary_fasterxml_static",
                """
                invoke-static {p0}, $PATCHES_DESCRIPTOR/ResponseLogger;->saveInputStreamPrimary(Ljava/io/InputStream;)Ljava/io/InputStream;
                move-result-object p0
                """,
            )?.let { matchedHooks.add(it) }

            injectOrNull(
                InputStreamFingerprintByName,
                "fallback_createparser_name_instance",
                """
                invoke-static {p1}, $PATCHES_DESCRIPTOR/ResponseLogger;->saveInputStreamByName(Ljava/io/InputStream;)Ljava/io/InputStream;
                move-result-object p1
                """,
            )?.let { matchedHooks.add(it) }

            injectOrNull(
                InputStreamFingerprintByReturnType,
                "fallback_returntype_instance",
                """
                invoke-static {p1}, $PATCHES_DESCRIPTOR/ResponseLogger;->saveInputStreamByReturnType(Ljava/io/InputStream;)Ljava/io/InputStream;
                move-result-object p1
                """,
            )?.let { matchedHooks.add(it) }

            injectOrNull(
                InputStreamFingerprintByReturnTypeStatic,
                "fallback_returntype_static",
                """
                invoke-static {p0}, $PATCHES_DESCRIPTOR/ResponseLogger;->saveInputStreamByReturnType(Ljava/io/InputStream;)Ljava/io/InputStream;
                move-result-object p0
                """,
            )?.let { matchedHooks.add(it) }

            injectOrNull(
                InputStreamFingerprintAnyInstance,
                "fallback_any_inputstream_instance",
                """
                invoke-static {p1}, $PATCHES_DESCRIPTOR/ResponseLogger;->saveInputStreamByName(Ljava/io/InputStream;)Ljava/io/InputStream;
                move-result-object p1
                """,
            )?.let { matchedHooks.add(it) }

            injectOrNull(
                InputStreamFingerprintAnyStatic,
                "fallback_any_inputstream_static",
                """
                invoke-static {p0}, $PATCHES_DESCRIPTOR/ResponseLogger;->saveInputStreamByName(Ljava/io/InputStream;)Ljava/io/InputStream;
                move-result-object p0
                """,
            )?.let { matchedHooks.add(it) }

            injectOrNull(
                ByteArrayFingerprintInstance,
                "fallback_bytearray_instance",
                """
                invoke-static {p1}, $PATCHES_DESCRIPTOR/ResponseLogger;->saveByteArray([B)[B
                move-result-object p1
                """,
            )?.let { matchedHooks.add(it) }

            injectOrNull(
                ByteArrayFingerprintStatic,
                "fallback_bytearray_static",
                """
                invoke-static {p0}, $PATCHES_DESCRIPTOR/ResponseLogger;->saveByteArray([B)[B
                move-result-object p0
                """,
            )?.let { matchedHooks.add(it) }

            injectOrNull(
                StringFingerprintInstance,
                "fallback_string_instance",
                """
                invoke-static {p1}, $PATCHES_DESCRIPTOR/ResponseLogger;->saveString(Ljava/lang/String;)Ljava/lang/String;
                move-result-object p1
                """,
            )?.let { matchedHooks.add(it) }

            injectOrNull(
                StringFingerprintStatic,
                "fallback_string_static",
                """
                invoke-static {p0}, $PATCHES_DESCRIPTOR/ResponseLogger;->saveString(Ljava/lang/String;)Ljava/lang/String;
                move-result-object p0
                """,
            )?.let { matchedHooks.add(it) }

            injectOrNull(
                StringBuilderFingerprintInstance,
                "fallback_stringbuilder_instance",
                """
                invoke-static {p1}, $PATCHES_DESCRIPTOR/ResponseLogger;->saveStringBuilder(Ljava/lang/StringBuilder;)Ljava/lang/StringBuilder;
                move-result-object p1
                """,
            )?.let { matchedHooks.add(it) }

            injectOrNull(
                StringBuilderFingerprintStatic,
                "fallback_stringbuilder_static",
                """
                invoke-static {p0}, $PATCHES_DESCRIPTOR/ResponseLogger;->saveStringBuilder(Ljava/lang/StringBuilder;)Ljava/lang/StringBuilder;
                move-result-object p0
                """,
            )?.let { matchedHooks.add(it) }

            injectOrNull(
                ByteBufferFingerprintInstance,
                "fallback_bytebuffer_instance",
                """
                invoke-static {p1}, $PATCHES_DESCRIPTOR/ResponseLogger;->saveByteBuffer(Ljava/nio/ByteBuffer;)Ljava/nio/ByteBuffer;
                move-result-object p1
                """,
            )?.let { matchedHooks.add(it) }

            injectOrNull(
                ByteBufferFingerprintStatic,
                "fallback_bytebuffer_static",
                """
                invoke-static {p0}, $PATCHES_DESCRIPTOR/ResponseLogger;->saveByteBuffer(Ljava/nio/ByteBuffer;)Ljava/nio/ByteBuffer;
                move-result-object p0
                """,
            )?.let { matchedHooks.add(it) }

            if (matchedHooks.isEmpty()) {
                // no-op: patch remains installable even if no response parser hook is found
            }
        }
    }
