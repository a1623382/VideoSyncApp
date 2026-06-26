package com.videosync.app.util

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File

/**
 * 视频编码检测工具
 * 用于检测本地视频的编码格式
 * 只有 H.264/H.265 编码的视频才会有对应的高画质 MKV 文件
 */
object VideoCodecHelper {

    // 支持高画质替换的视频编码
    private val HQ_CODECS = setOf(
        "video/avc",     // H.264
        "video/hevc",    // H.265
        "video/h264",
        "video/h265",
        "video/x-vnd.on2.vp8",  // VP8
        "video/x-vnd.on2.vp9",  // VP9
        "video/av01"     // AV1
    )

    /**
     * 视频编码信息
     * @param codec 编码名称
     * @param mimeType MIME 类型
     * @param isHighQuality 是否为高质量编码（H.264/H.265等）
     */
    data class VideoCodecInfo(
        val codec: String = "",
        val mimeType: String = "",
        val isHighQuality: Boolean = false
    )

    /**
     * 检测视频文件的编码格式
     * @param context 应用上下文
     * @param filePath 文件路径
     * @return 视频编码信息
     */
    fun detectCodec(context: Context, filePath: String): VideoCodecInfo {
        return try {
            val extractor = MediaExtractor()
            extractor.setDataSource(filePath)

            // 查找视频轨道
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mimeType = format.getString(MediaFormat.KEY_MIME) ?: continue

                if (mimeType.startsWith("video/")) {
                    val codec = when (mimeType) {
                        "video/avc" -> "H.264"
                        "video/hevc" -> "H.265"
                        "video/h264" -> "H.264"
                        "video/h265" -> "H.265"
                        "video/x-vnd.on2.vp8" -> "VP8"
                        "video/x-vnd.on2.vp9" -> "VP9"
                        "video/av01" -> "AV1"
                        "video/mp4v-es" -> "MPEG-4"
                        "video/3gpp" -> "3GP"
                        "video/raw" -> "RAW"
                        else -> mimeType.substringAfter("video/").uppercase()
                    }

                    extractor.release()
                    return VideoCodecInfo(
                        codec = codec,
                        mimeType = mimeType,
                        isHighQuality = mimeType in HQ_CODECS
                    )
                }
            }

            extractor.release()
            VideoCodecInfo(codec = "未知", mimeType = "", isHighQuality = false)
        } catch (e: Exception) {
            Logger.w("VideoCodecHelper", "检测编码失败: $filePath", e)
            VideoCodecInfo(codec = "未知", mimeType = "", isHighQuality = false)
        }
    }

    /**
     * 通过 URI 检测视频编码
     */
    fun detectCodec(context: Context, uri: Uri): VideoCodecInfo {
        return try {
            val extractor = MediaExtractor()
            extractor.setDataSource(context, uri, null)

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mimeType = format.getString(MediaFormat.KEY_MIME) ?: continue

                if (mimeType.startsWith("video/")) {
                    val codec = when (mimeType) {
                        "video/avc" -> "H.264"
                        "video/hevc" -> "H.265"
                        "video/h264" -> "H.264"
                        "video/h265" -> "H.265"
                        "video/x-vnd.on2.vp8" -> "VP8"
                        "video/x-vnd.on2.vp9" -> "VP9"
                        "video/av01" -> "AV1"
                        "video/mp4v-es" -> "MPEG-4"
                        "video/3gpp" -> "3GP"
                        "video/raw" -> "RAW"
                        else -> mimeType.substringAfter("video/").uppercase()
                    }

                    extractor.release()
                    return VideoCodecInfo(
                        codec = codec,
                        mimeType = mimeType,
                        isHighQuality = mimeType in HQ_CODECS
                    )
                }
            }

            extractor.release()
            VideoCodecInfo(codec = "未知", mimeType = "", isHighQuality = false)
        } catch (e: Exception) {
            Logger.w("VideoCodecHelper", "检测编码失败: $uri", e)
            VideoCodecInfo(codec = "未知", mimeType = "", isHighQuality = false)
        }
    }

    /**
     * 检查视频是否为高画质编码
     * 只有高画质编码的视频才可能有对应的 MKV 高画质版本
     */
    fun isHighQualityCodec(context: Context, filePath: String): Boolean {
        return detectCodec(context, filePath).isHighQuality
    }
}
