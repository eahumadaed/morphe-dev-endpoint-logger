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

@Suppress("unused")
val responseLoggingPatch =
    bytecodePatch(
        name = "Endpoint response logger",
        description = "Logs parser InputStream payloads to endpoint response logs",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            val hooked =
                runCatching {
                    InputStreamFingerprintPrimary.method.addInstructions(
                        0,
                        """
                        invoke-static {p1}, $PATCHES_DESCRIPTOR/ResponseLogger;->saveInputStreamPrimary(Ljava/io/InputStream;)Ljava/io/InputStream;
                        move-result-object p1
                        """.trimIndent(),
                    )
                }.isSuccess ||
                    runCatching {
                        InputStreamFingerprintByName.method.addInstructions(
                            0,
                            """
                            invoke-static {p1}, $PATCHES_DESCRIPTOR/ResponseLogger;->saveInputStreamByName(Ljava/io/InputStream;)Ljava/io/InputStream;
                            move-result-object p1
                            """.trimIndent(),
                        )
                    }.isSuccess ||
                    runCatching {
                        InputStreamFingerprintByReturnType.method.addInstructions(
                            0,
                            """
                            invoke-static {p1}, $PATCHES_DESCRIPTOR/ResponseLogger;->saveInputStreamByReturnType(Ljava/io/InputStream;)Ljava/io/InputStream;
                            move-result-object p1
                            """.trimIndent(),
                        )
                    }.isSuccess

            if (!hooked) {
                // no-op: patch remains installable even if response parser hook is not found
            }
        }
    }
