package com.lizongying.mytv0

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import com.lizongying.mytv0.databinding.PlayerBinding
import com.lizongying.mytv0.models.TVModel

class PlayerFragment : Fragment(), SurfaceHolder.Callback {
    private var _binding: PlayerBinding? = null
    private val binding get() = _binding!!

    private var ijkUtil: IjkUtil? = null

    private var videoUrl = ""
    private var tvModel: TVModel? = null
    private val aspectRatio = 16f / 9f

    private lateinit var surfaceView: SurfaceView

    private lateinit var mainActivity: MainActivity

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        mainActivity = activity as MainActivity
        super.onActivityCreated(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = PlayerBinding.inflate(inflater, container, false)
        surfaceView = _binding!!.surfaceView
        surfaceView.holder.addCallback(this)

        ijkUtil = IjkUtil.getInstance();
        ijkUtil?.setOnErrorListener(TAG) { what, extra ->
            Log.e(TAG, "PlaybackException what=" + what + " extra=" + extra)
            val err = "播放错误"
            tvModel?.setErrInfo(err)
            tvModel?.setReady()
            //tvModel?.changed("retry")
        }

        ijkUtil?.setOnPreparedListener(TAG) {
            tvModel?.setErrInfo("")
        }

        return _binding!!.root
    }

    @OptIn(UnstableApi::class)
    fun play(tvModel: TVModel) {
        videoUrl = tvModel.videoUrl.value ?: return
        this.tvModel = tvModel
        Log.i(TAG, "play ${tvModel.tv.title} $videoUrl")

        ijkUtil?.reset()
        ijkUtil?.setDisplay(surfaceView.holder)
        ijkUtil?.setDataSource(videoUrl)
        ijkUtil?.prepareAsync()
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
        if (ijkUtil?.isPlaying == false) {
            Log.i(TAG, "replay")
            ijkUtil?.start()
        }
    }

    override fun onPause() {
        super.onPause()
        ijkUtil?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        ijkUtil?.release()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        ijkUtil = null;
    }

    companion object {
        private const val TAG = "PlayerFragment"
    }

    override fun surfaceCreated(holder: SurfaceHolder) {

        ijkUtil?.setDisplay(holder)
        (activity as MainActivity).ready(TAG)
        Log.i(TAG, "IjkMediaPlayer ready")
    }

    private fun updateVideoLayout(videoWidth: Int, videoHeight: Int) {
        val surfaceWidth = surfaceView.width
        val surfaceHeight = surfaceView.height

        val aspectRatio = videoWidth.toFloat() / videoHeight.toFloat()
        var newWidth = surfaceWidth
        var newHeight = surfaceHeight

        if (surfaceWidth.toFloat() / surfaceHeight.toFloat() > aspectRatio) {
            newWidth = (surfaceHeight.toFloat() * aspectRatio).toInt()
        } else {
            newHeight = (surfaceWidth.toFloat() / aspectRatio).toInt()
        }

        val layoutParams = surfaceView.layoutParams
        layoutParams.width = newWidth
        layoutParams.height = newHeight
        surfaceView.layoutParams = layoutParams
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.i(TAG, "Surface changed: $width x $height")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        ijkUtil?.setDisplay(null)
    }
}