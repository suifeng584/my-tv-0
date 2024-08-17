package com.lizongying.mytv0

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.DISCONTINUITY_REASON_AUTO_TRANSITION
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import com.lizongying.mytv0.databinding.PlayerBinding
import com.lizongying.mytv0.models.SourceType
import com.lizongying.mytv0.models.TVModel


class PlayerFragment : Fragment() {
    private var _binding: PlayerBinding? = null
    private val binding get() = _binding!!

    private var player: ExoPlayer? = null

    private var tvModel: TVModel? = null
    private val aspectRatio = 16f / 9f

    private lateinit var mainActivity: MainActivity

    private var metadata = Metadata()

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        mainActivity = activity as MainActivity
        super.onActivityCreated(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = PlayerBinding.inflate(inflater, container, false)
        val playerView = _binding!!.playerView

        playerView.viewTreeObserver?.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            @OptIn(UnstableApi::class)
            override fun onGlobalLayout() {
                playerView.viewTreeObserver.removeOnGlobalLayoutListener(this)

                val renderersFactory = context?.let { DefaultRenderersFactory(it) }
                val playerMediaCodecSelector = PlayerMediaCodecSelector()
                renderersFactory?.setMediaCodecSelector(playerMediaCodecSelector)
                renderersFactory?.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                renderersFactory?.setEnableDecoderFallback(true)

                player = context?.let {
                    ExoPlayer.Builder(it)
                        .setRenderersFactory(renderersFactory!!)
                        .build()
                }
                playerView.player = player
                player?.repeatMode = REPEAT_MODE_ALL
                player?.playWhenReady = true
                player?.addAnalyticsListener(metadataListener)
                player?.addListener(object : Player.Listener {
                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        val ratio = playerView.measuredWidth.div(playerView.measuredHeight)
                        val layoutParams = playerView.layoutParams
                        if (ratio < aspectRatio) {
                            layoutParams?.height =
                                (playerView.measuredWidth.div(aspectRatio)).toInt()
                            playerView.layoutParams = layoutParams
                        } else if (ratio > aspectRatio) {
                            layoutParams?.width =
                                (playerView.measuredHeight.times(aspectRatio)).toInt()
                            playerView.layoutParams = layoutParams
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        super.onIsPlayingChanged(isPlaying)
                        if (isPlaying) {
                            tvModel?.confirmSourceType()
                            tvModel?.setErrInfo("")
                            tvModel!!.retryTimes = 0
                        } else {
                            Log.i(TAG, "${tvModel?.tv?.title} 播放停止")
//                                tvModel?.setErrInfo("播放停止")
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        val stateString = when (playbackState) {
                            Player.STATE_IDLE -> "idle"
                            Player.STATE_BUFFERING -> "buffering"
                            Player.STATE_READY -> "ready"
                            Player.STATE_ENDED -> "end"
                            else -> "unknown"
                        }
                        Log.d(TAG, "playbackState $stateString")
                        super.onPlaybackStateChanged(playbackState)
                    }


                    override fun onPositionDiscontinuity(
                        oldPosition: Player.PositionInfo,
                        newPosition: Player.PositionInfo,
                        reason: Int
                    ) {
                        if (reason == DISCONTINUITY_REASON_AUTO_TRANSITION) {
                            mainActivity.onPlayEnd()
                        }
                        super.onPositionDiscontinuity(oldPosition, newPosition, reason)
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        super.onPlayerError(error)
                        Log.i(
                            TAG,
                            "播放错误 ${error.errorCode}||| ${error.errorCodeName}||| ${error.message}||| $error"
                        )

                        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                            tvModel?.setReady()
                            return
                        }

                        "错误码[${error.errorCode}] ${error.errorCodeName}".showToast()
                        tvModel?.setErrInfo("播放错误")
                        if (tvModel?.getSourceType() == SourceType.UNKNOWN) {//FIXME: retryTimes and UNKNOWN
                            tvModel?.nextSource()
                        }
                        if (tvModel!!.retryTimes < tvModel!!.retryMaxTimes) {
                            tvModel?.setReady()
                            tvModel!!.retryTimes++
                        }
                        if (tvModel!!.retryTimes == tvModel!!.retryMaxTimes) {
                            val errorType = when (error.errorCode) {
                                in 2000 until 2003 -> "网络异常"
                                in 2003 until 3000 -> "服务器异常"
                                in 3000 until 4000 -> "节目源异常"
                                in 4000 until 6000 -> "解码异常"
                                in 6000 until 7000 -> "DRM 异常"
                                else -> "播放错误"
                            }
                            tvModel?.setErrInfo("${errorType}[${error.errorCode}]\n${error.errorCodeName}")
                        }
                    }
                })

                (activity as MainActivity).ready(TAG)
                Log.i(TAG, "player ready")
            }
        })

        return _binding!!.root
    }

    @OptIn(UnstableApi::class)
    fun play(tvModel: TVModel) {
        this.tvModel = tvModel
        player?.run {
            IgnoreSSLCertificate.ignore()
            val httpDataSource = DefaultHttpDataSource.Factory()
            httpDataSource.setKeepPostFor302Redirects(true)
            httpDataSource.setAllowCrossProtocolRedirects(true)
            httpDataSource.setTransferListener(object : TransferListener {
                override fun onTransferInitializing(
                    source: DataSource,
                    dataSpec: DataSpec,
                    isNetwork: Boolean
                ) {
//                    TODO("Not yet implemented")
                }

                override fun onTransferStart(
                    source: DataSource,
                    dataSpec: DataSpec,
                    isNetwork: Boolean
                ) {
                    Log.d(TAG, "onTransferStart uri ${source.uri}")
//                    TODO("Not yet implemented")
                }

                override fun onBytesTransferred(
                    source: DataSource,
                    dataSpec: DataSpec,
                    isNetwork: Boolean,
                    bytesTransferred: Int
                ) {
//                    TODO("Not yet implemented")
                }

                override fun onTransferEnd(
                    source: DataSource,
                    dataSpec: DataSpec,
                    isNetwork: Boolean
                ) {
//                    TODO("Not yet implemented")
                }
            })

            val dataSource = tvModel.getSource()
            if (dataSource != null) {
                setMediaSource(dataSource)
            } else {
                setMediaItem(tvModel.getMediaItem())
            }

            prepare()
        }
    }

    @OptIn(UnstableApi::class)
    class PlayerMediaCodecSelector : MediaCodecSelector {
        override fun getDecoderInfos(
            mimeType: String,
            requiresSecureDecoder: Boolean,
            requiresTunnelingDecoder: Boolean
        ): MutableList<androidx.media3.exoplayer.mediacodec.MediaCodecInfo> {
            val infos = MediaCodecUtil.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder
            )

            if (mimeType == MimeTypes.VIDEO_H265 && !requiresSecureDecoder && !requiresTunnelingDecoder) {
                if (infos.size > 0) {
                    val infosNew = infos.find { it.name == "c2.android.hevc.decoder" }
                        ?.let { mutableListOf(it) }
                    if (infosNew != null) {
                        return infosNew
                    }
                }
            }
            return infos
        }
    }

    override fun onStart() {
        Log.i(TAG, "onStart")
        super.onStart()
    }

    override fun onResume() {
        Log.i(TAG, "play-onResume")
        super.onResume()
        if (player?.isPlaying == false) {
            Log.i(TAG, "replay")
            player?.prepare()
            player?.play()
        }
    }

    override fun onPause() {
        super.onPause()
        if (player?.isPlaying == true) {
            player?.stop()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    protected fun triggerMetadata(metadata: Metadata) {
        //onMetadataListeners.forEach { it(metadata) }
        Log.d(TAG, "metadata: $metadata")
    }

    private val metadataListener = @UnstableApi object : AnalyticsListener {
        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            metadata = metadata.copy(
                videoMimeType = format.sampleMimeType ?: "",
                videoWidth = format.width,
                videoHeight = format.height,
                videoColor = format.colorInfo?.toLogString() ?: "",
                // TODO 帧率、比特率目前是从tag中获取，有的返回空，后续需要实时计算
                videoFrameRate = format.frameRate,
                videoBitrate = format.bitrate,
            )
            triggerMetadata(metadata)
        }

        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            metadata = metadata.copy(videoDecoder = decoderName)
            triggerMetadata(metadata)
        }

        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            metadata = metadata.copy(
                audioMimeType = format.sampleMimeType ?: "",
                audioChannels = format.channelCount,
                audioSampleRate = format.sampleRate,
            )
            triggerMetadata(metadata)
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            metadata = metadata.copy(audioDecoder = decoderName)
            triggerMetadata(metadata)
        }
    }

    /** 元数据 */
    data class Metadata(
        /** 视频编码 */
        val videoMimeType: String = "",
        /** 视频宽度 */
        val videoWidth: Int = 0,
        /** 视频高度 */
        val videoHeight: Int = 0,
        /** 视频颜色 */
        val videoColor: String = "",
        /** 视频帧率 */
        val videoFrameRate: Float = 0f,
        /** 视频比特率 */
        val videoBitrate: Int = 0,
        /** 视频解码器 */
        val videoDecoder: String = "",

        /** 音频编码 */
        val audioMimeType: String = "",
        /** 音频通道 */
        val audioChannels: Int = 0,
        /** 音频采样率 */
        val audioSampleRate: Int = 0,
        /** 音频解码器 */
        val audioDecoder: String = "",
    )

    companion object {
        private const val TAG = "PlayerFragment"
    }
}