package com.example.myapplication.webrtc

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import com.example.myapplication.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import org.webrtc.*
import org.webrtc.Camera2Enumerator
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer

class SimpleWebRTCManager(
    private val context: Context,
    private val currentUserId: String,
    private val remoteUserId: String
) {
    private val TAG = "WebRTCManager"
    private val db = FirebaseFirestore.getInstance()

    private val callId = if (currentUserId < remoteUserId) "${currentUserId}_${remoteUserId}" else "${remoteUserId}_${currentUserId}"
    private val sessionStartTime = System.currentTimeMillis()

    var onCallEstablished: (() -> Unit)? = null
    var onCallEnded: (() -> Unit)? = null

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null // Видео
    private var videoCapturer: VideoCapturer? = null
    private val rootEglBase: EglBase = EglBase.create()

    private var localSurfaceView: SurfaceViewRenderer? = null
    private var remoteSurfaceView: SurfaceViewRenderer? = null

    private var pendingOfferSdp: String? = null
    private var mediaPlayer: MediaPlayer? = null
    private var signalingListener: ListenerRegistration? = null
    private var candidatesListener: ListenerRegistration? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var savedAudioMode = audioManager.mode
    private var savedIsSpeakerPhoneOn = audioManager.isSpeakerphoneOn
    private var remoteVideoTrack: VideoTrack? = null // <--- ДОБАВИТЬ ЭТО
    // Флаг текущего типа звонка
    private var isVideoCall = false

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:global.stun.twilio.com:3478").createIceServer()
    )

    fun initSurfaceView(view: SurfaceViewRenderer, isLocal: Boolean) {
        try { view.init(rootEglBase.eglBaseContext, null) } catch (e: Exception) {}
        view.setMirror(isLocal)
        view.setEnableHardwareScaler(true)

        // ОЧИСТКА: Убираем этот View из обоих треков, чтобы не было "каши"
        localVideoTrack?.removeSink(view)
        remoteVideoTrack?.removeSink(view)

        // ПРИВЯЗКА:
        if (isLocal) {
            localSurfaceView = view
            localVideoTrack?.addSink(view)
        } else {
            remoteSurfaceView = view
            remoteVideoTrack?.addSink(view)
        }
    }




    // === ИЗМЕНЕНИЕ: Принимаем isVideo ===
    fun initialize(isVideo: Boolean) {
        Log.d(TAG, "🚀 Initialize. Video: $isVideo")
        this.isVideoCall = isVideo
        pendingOfferSdp = null

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions()
        )

        val options = PeerConnectionFactory.Options()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(rootEglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(rootEglBase.eglBaseContext))
            .createPeerConnectionFactory()

        // 1. Аудио (всегда)
        val audioConstraints = MediaConstraints()
        val audioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory?.createAudioTrack("ARDAMSa0", audioSource)

        // 2. Видео (ТОЛЬКО ЕСЛИ НУЖНО)
        if (isVideo) {
            startVideoCapture()
        }

        setupAudioManager()
        createPeerConnection()
        setupFirebaseListeners()
    }

    private fun startVideoCapture() {

        val videoSource = peerConnectionFactory?.createVideoSource(false)
        localVideoTrack = peerConnectionFactory?.createVideoTrack("ARDAMSv0", videoSource)
        localSurfaceView?.let { localVideoTrack?.addSink(it) }

        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames
        var deviceName = deviceNames.find { enumerator.isFrontFacing(it) }
        if (deviceName == null && deviceNames.isNotEmpty()) deviceName = deviceNames[0]

        if (deviceName != null) {
            videoCapturer = enumerator.createCapturer(deviceName, null)
            val textureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase.eglBaseContext)
            val observer = videoSource?.capturerObserver
            if (observer != null) {
                videoCapturer?.initialize(textureHelper, context, observer)
            }

            videoCapturer?.startCapture(640, 480, 30)
        }
    }

    private fun playSound(resId: Int) {
        stopSound()
        try {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = true
            mediaPlayer = MediaPlayer.create(context, resId).apply {
                isLooping = true
                setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build())
                start()
            }
        } catch (e: Exception) { Log.e(TAG, "Sound error: ${e.message}") }
    }

    fun playDialingSound() { playSound(R.raw.dialing) }
    fun playRingtone() { playSound(R.raw.ringtone) }
    fun stopSound() { mediaPlayer?.stop(); mediaPlayer?.release(); mediaPlayer = null }

    private fun setupAudioManager() {
        savedAudioMode = audioManager.mode
        savedIsSpeakerPhoneOn = audioManager.isSpeakerphoneOn
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = false
    }

    fun toggleSpeaker(enable: Boolean) {
        audioManager.isSpeakerphoneOn = enable
    }

    fun toggleMute(mute: Boolean) {
        localAudioTrack?.setEnabled(!mute)
    }

    private fun createPeerConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, object : SimplePeerConnectionObserver() {
            override fun onIceCandidate(candidate: IceCandidate?) { candidate?.let { sendIceCandidate(it) } }
            override fun onAddStream(stream: MediaStream?) {
                Log.d(TAG, "Stream received")
                stopSound()

                if (stream != null && stream.videoTracks.isNotEmpty()) {
                    remoteVideoTrack = stream.videoTracks[0] // <--- СОХРАНЯЕМ
                    remoteSurfaceView?.let { remoteVideoTrack?.addSink(it) }
                }

                onCallEstablished?.invoke()
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                stopSound()
                onCallEstablished?.invoke()
            }
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                if (newState == PeerConnection.IceConnectionState.DISCONNECTED || newState == PeerConnection.IceConnectionState.CLOSED) {
                    onCallEnded?.invoke()
                }
            }
        })

        localAudioTrack?.let { peerConnection?.addTrack(it, listOf("ARDAMS")) }
        localVideoTrack?.let { peerConnection?.addTrack(it, listOf("ARDAMS")) }
    }

    fun startCall() {
        playDialingSound()
        db.collection("calls").document(callId).delete().addOnCompleteListener {
            val constraints = MediaConstraints()
            peerConnection?.createOffer(object : SimpleSdpObserver() {
                override fun onCreateSuccess(desc: SessionDescription?) {
                    desc?.let {
                        peerConnection?.setLocalDescription(SimpleSdpObserver(), it)
                        sendSdp("OFFER", it.description)
                    }
                }
            }, constraints)
        }
    }

    fun acceptCall() {
        stopSound()
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        // Если видео - сразу громкая связь, если аудио - тихо
        audioManager.isSpeakerphoneOn = isVideoCall

        val offerSdp = pendingOfferSdp ?: return
        peerConnection?.setRemoteDescription(SimpleSdpObserver(), SessionDescription(SessionDescription.Type.OFFER, offerSdp))
        val constraints = MediaConstraints()
        peerConnection?.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let {
                    peerConnection?.setLocalDescription(SimpleSdpObserver(), it)
                    sendSdp("ANSWER", it.description)
                }
            }
        }, constraints)
    }

    fun endCall() {
        stopSound()
        sendAction("END_CALL")
        cleanup()
    }
    fun switchCamera() {
        val capturer = videoCapturer as? CameraVideoCapturer
        capturer?.switchCamera(null)
    }

    // ВАЖНО: Добавляем isVideo в базу данных
    private fun sendSdp(type: String, sdp: String) {
        db.collection("calls").document(callId).set(hashMapOf(
            "type" to type,
            "sdp" to sdp,
            "senderId" to currentUserId,
            "isVideo" to isVideoCall, // <--- ОТПРАВЛЯЕМ ТИП
            "timestamp" to System.currentTimeMillis()
        ))
    }

    private fun sendAction(type: String) {
        db.collection("calls").document(callId).set(hashMapOf("type" to type, "senderId" to currentUserId, "timestamp" to System.currentTimeMillis()))
    }

    private fun sendIceCandidate(candidate: IceCandidate) {
        db.collection("calls").document(callId).collection("candidates").add(hashMapOf("sdpMid" to candidate.sdpMid, "sdpMLineIndex" to candidate.sdpMLineIndex, "candidate" to candidate.sdp, "senderId" to currentUserId))
    }

    private fun setupFirebaseListeners() {
        signalingListener = db.collection("calls").document(callId).addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
            val type = snapshot.getString("type")
            val sdp = snapshot.getString("sdp")
            val senderId = snapshot.getString("senderId")
            val timestamp = snapshot.getLong("timestamp") ?: 0L
            if (timestamp < sessionStartTime || senderId == currentUserId) return@addSnapshotListener
            when (type) {
                "OFFER" -> pendingOfferSdp = sdp
                "ANSWER" -> peerConnection?.setRemoteDescription(SimpleSdpObserver(), SessionDescription(SessionDescription.Type.ANSWER, sdp))
                "END_CALL" -> { stopSound(); onCallEnded?.invoke() }
            }
        }
        candidatesListener = db.collection("calls").document(callId).collection("candidates").addSnapshotListener { snapshots, e ->
            if (e != null) return@addSnapshotListener
            for (doc in snapshots!!.documentChanges) {
                if (doc.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                    if (doc.document.getString("senderId") == currentUserId) continue
                    val sdp = doc.document.getString("candidate")
                    if (sdp != null) peerConnection?.addIceCandidate(IceCandidate(doc.document.getString("sdpMid"), doc.document.getLong("sdpMLineIndex")?.toInt() ?: 0, sdp))
                }
            }
        }
    }

    fun cleanup() {
        try {
            stopSound()
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            signalingListener?.remove()
            candidatesListener?.remove()
            audioManager.mode = savedAudioMode
            audioManager.isSpeakerphoneOn = savedIsSpeakerPhoneOn
            peerConnection?.close()
            peerConnection = null
            rootEglBase.release()
        } catch (e: Exception) { e.printStackTrace() }
    }

    open class SimplePeerConnectionObserver : PeerConnection.Observer {
        override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
        override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
        override fun onIceConnectionReceivingChange(p0: Boolean) {}
        override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
        override fun onIceCandidate(p0: IceCandidate?) {}
        override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
        override fun onAddStream(p0: MediaStream?) {}
        override fun onRemoveStream(p0: MediaStream?) {}
        override fun onDataChannel(p0: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
        override fun onTrack(transceiver: RtpTransceiver?) {}
    }
    open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(p0: String?) {}
        override fun onSetFailure(p0: String?) {}
    }
}
