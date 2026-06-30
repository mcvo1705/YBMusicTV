package com.ybmusic.tv.build

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Build-time bytecode fix cho NewPipe Extractor v0.24.2.
 *
 * `org.schabi.newpipe.extractor.utils.Utils#encodeUrlUtf8` /`#decodeUrlUtf8` gọi
 * `URLEncoder.encode(String, Charset)` / `URLDecoder.decode(String, Charset)` —
 * các overload chỉ tồn tại từ Android API 33. Trên TV box API 28–30 (vd Xiaomi
 * A43 Pro) runtime ném `NoSuchMethodError` mỗi khi search/lấy stream/playlist.
 *
 * Core library desugaring (desugar_jdk_libs) KHÔNG backport các overload này
 * (không có entry java.net.* trong desugar config), nên cần fix ở mức bytecode.
 *
 * Factory này viết lại thân hai phương thức trên để gọi overload nhận `String`
 * charset ("UTF-8") — có từ API 1 — giữ nguyên hành vi, không phải đổi version
 * NewPipe và không đụng tới kiến trúc app.
 */
abstract class NewPipeUrlEncoderFix :
    AsmClassVisitorFactory<InstrumentationParameters.None> {

    override fun createClassVisitor(
        classContext: ClassContext,
        nextClassVisitor: ClassVisitor,
    ): ClassVisitor = UtilsClassVisitor(
        instrumentationContext.apiVersion.get(),
        nextClassVisitor,
    )

    override fun isInstrumentable(classData: ClassData): Boolean =
        classData.className == "org.schabi.newpipe.extractor.utils.Utils"
}

private class UtilsClassVisitor(
    private val asmApi: Int,
    nextClassVisitor: ClassVisitor,
) : ClassVisitor(asmApi, nextClassVisitor) {

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?,
    ): MethodVisitor {
        val original = super.visitMethod(access, name, descriptor, signature, exceptions)
        if (descriptor != STRING_TO_STRING) return original
        return when (name) {
            "encodeUrlUtf8" -> LegacyCharsetOverload(asmApi, original, "java/net/URLEncoder", "encode")
            "decodeUrlUtf8" -> LegacyCharsetOverload(asmApi, original, "java/net/URLDecoder", "decode")
            else -> original
        }
    }

    private companion object {
        const val STRING_TO_STRING = "(Ljava/lang/String;)Ljava/lang/String;"
    }
}

/**
 * Bỏ thân gốc (gọi overload Charset của API 33) và sinh lại:
 *
 *     return owner.method(arg, "UTF-8");
 *
 * Không truyền delegate cho [MethodVisitor] nên mọi callback của thân gốc bị bỏ
 * qua; chỉ [visitEnd] ghi thân mới vào writer thật.
 */
private class LegacyCharsetOverload(
    asmApi: Int,
    private val writer: MethodVisitor,
    private val owner: String,
    private val method: String,
) : MethodVisitor(asmApi) {

    override fun visitEnd() {
        writer.visitCode()
        writer.visitVarInsn(Opcodes.ALOAD, 0)
        writer.visitLdcInsn("UTF-8")
        writer.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            owner,
            method,
            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
            false,
        )
        writer.visitInsn(Opcodes.ARETURN)
        writer.visitMaxs(2, 1)
        writer.visitEnd()
    }
}
