// WebRTC Client for live streaming
let peerConnection = null;
let currentStreamDevice = null;
let webrtcSocket = null;
let pendingIceCandidates = [];

function initWebRTCSocket() {
  if (webrtcSocket) return;
  webrtcSocket = io();

  webrtcSocket.on('webrtc-answer', async (data) => {
    try {
      if (peerConnection && data.signal) {
        await peerConnection.setRemoteDescription(
          new RTCSessionDescription({ type: 'answer', sdp: data.signal.sdp })
        );
        document.getElementById('liveStatus').textContent = '🔴 LIVE';
      }
    } catch (e) {
      console.error('Answer error:', e);
    }
  });

  webrtcSocket.on('webrtc-ice', async (data) => {
    try {
      if (!data.signal) return;
      const candidate = new RTCIceCandidate({
        sdpMid: data.signal.sdpMid,
        sdpMLineIndex: data.signal.sdpMLineIndex,
        candidate: data.signal.candidate
      });
      if (peerConnection && peerConnection.remoteDescription) {
        await peerConnection.addIceCandidate(candidate);
      } else {
        pendingIceCandidates.push(candidate);
      }
    } catch (e) {
      console.error('ICE error:', e);
    }
  });
}

function startLiveStream(deviceId, type = 'camera') {
  if (!deviceId) { alert('Device select karein'); return; }
  stopLiveStream(true);
  currentStreamDevice = deviceId;
  pendingIceCandidates = [];

  if (!webrtcSocket) initWebRTCSocket();

  peerConnection = new RTCPeerConnection({
    iceServers: [
      { urls: 'stun:stun.l.google.com:19302' },
      { urls: 'stun:stun1.l.google.com:19302' }
    ]
  });

  peerConnection.ontrack = (event) => {
    const video = document.getElementById('liveVideo');
    const audio = document.getElementById('liveAudio');
    if (event.track.kind === 'video' && video) {
      video.srcObject = event.streams[0];
      video.play().catch(() => {});
    }
    if (event.track.kind === 'audio' && audio) {
      audio.srcObject = event.streams[0];
      audio.play().catch(() => {});
    }
  };

  peerConnection.onicecandidate = (event) => {
    if (event.candidate && webrtcSocket) {
      webrtcSocket.emit('webrtc-ice', {
        target: deviceId,
        signal: {
          sdpMid: event.candidate.sdpMid,
          sdpMLineIndex: event.candidate.sdpMLineIndex,
          candidate: event.candidate.candidate
        }
      });
    }
  };

  peerConnection.oniceconnectionstatechange = () => {
    const status = document.getElementById('liveStatus');
    if (status) status.textContent = 'ICE: ' + peerConnection.iceConnectionState;
  };

  peerConnection.onconnectionstatechange = () => {
    const status = document.getElementById('liveStatus');
    if (status && peerConnection.connectionState === 'connected') {
      status.textContent = '🔴 LIVE';
    }
  };

  peerConnection.createOffer({
    offerToReceiveVideo: type !== 'audio',
    offerToReceiveAudio: true
  })
  .then(offer => peerConnection.setLocalDescription(offer))
  .then(() => {
    webrtcSocket.emit('webrtc-offer', {
      target: deviceId,
      signal: peerConnection.localDescription
    });

    // Also trigger device to start WebRTC
    fetch('/api/admin/command', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        deviceId,
        command: type === 'audio' ? 'start_audio_stream' : 'start_webrtc'
      })
    });

    const status = document.getElementById('liveStatus');
    if (status) status.textContent = 'Connecting...';
  })
  .catch(e => console.error('Offer error:', e));
}

function stopLiveStream(silent = false) {
  if (peerConnection) {
    try { peerConnection.close(); } catch (e) {}
    peerConnection = null;
  }
  const v = document.getElementById('liveVideo');
  const a = document.getElementById('liveAudio');
  if (v) v.srcObject = null;
  if (a) a.srcObject = null;

  if (currentStreamDevice && !silent) {
    fetch('/api/admin/command', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        deviceId: currentStreamDevice,
        command: 'stop_webrtc'
      })
    });
  }
  if (!silent) currentStreamDevice = null;

  const status = document.getElementById('liveStatus');
  if (status && !silent) status.textContent = 'Not streaming';
}

function switchCamera() {
  if (!currentStreamDevice) { alert('Pehle stream start karein'); return; }
  fetch('/api/admin/command', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      deviceId: currentStreamDevice,
      command: 'switch_camera'
    })
  }).then(() => alert('🔄 Camera switched'));
}