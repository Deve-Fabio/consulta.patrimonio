/* ─────────────────────────────────────────────────────────────────
   DETRAN-DF — Consulta de Patrimônio
   app.js — busca via API Tomcat 9 + PostgreSQL
───────────────────────────────────────────────────────────────── */


const API_BASE = '/consulta-patrimonio/api/patrimonio';

/* ── estado global ───────────────────────────────────────────── */
var scannerAtivo = false;
var codDetectado = null;
var html5Scanner = null;

/* ── SCANNER ─────────────────────────────────────────────────── */
function toggleScanner() {
  scannerAtivo ? pararScanner() : iniciarScanner();
}

function iniciarScanner() {
  escondeResultados();
  document.getElementById('scanner-container').classList.add('active');
  document.getElementById('scanner-status').classList.add('visible');
  document.getElementById('btn-live-icon').textContent = '\u23F9\uFE0F';
  document.getElementById('btn-live-text').textContent = 'Parar Scanner';
  document.getElementById('btn-live').classList.add('scanning');
  setStatus('Iniciando câmera...', false);

  html5Scanner = new Html5Qrcode('scanner-viewport');

  var config = {
    fps: 15,
    qrbox: { width: 280, height: 100 },
    aspectRatio: 2.0,
    formatsToSupport: [
      Html5QrcodeSupportedFormats.CODE_128,
      Html5QrcodeSupportedFormats.CODE_39,
      Html5QrcodeSupportedFormats.EAN_13,
      Html5QrcodeSupportedFormats.EAN_8
    ]
  };

  scannerAtivo = true;

  html5Scanner.start(
    { facingMode: 'environment' },
    config,
    function(cod) {
      if (!scannerAtivo) return;
      flashTela();
      codDetectado = cod;
      pararScanner();
      buscar(cod);
    },
    function(erroFrame) { /* frame sem leitura — normal */ }
  ).then(function() {
    setStatus('Câmera ativa — aponte para a etiqueta', true);
  }).catch(function(err) {
    scannerAtivo = false;
    pararScanner();
    var s = String(err).toLowerCase();
    var msg;
    if (s.indexOf('permission') !== -1 || s.indexOf('notallowed') !== -1)
      msg = 'Permissão de câmera negada. Toque no cadeado na barra do navegador e permita o acesso.';
    else if (s.indexOf('notfound') !== -1 || s.indexOf('devicenotfound') !== -1)
      msg = 'Nenhuma câmera encontrada neste dispositivo.';
    else if (s.indexOf('notreadable') !== -1 || s.indexOf('inuse') !== -1)
      msg = 'A câmera está sendo usada por outro aplicativo. Feche-o e tente novamente.';
    else
      msg = 'Não foi possível acessar a câmera. Verifique se a página está em HTTPS. Detalhe: ' + err;
    mostrarErro(msg);
  });
}

function pararScanner() {
  if (html5Scanner) {
    html5Scanner.stop().then(function() {
      html5Scanner.clear();
      html5Scanner = null;
    }).catch(function() { html5Scanner = null; });
  }
  scannerAtivo = false;
  document.getElementById('scanner-container').classList.remove('active');
  document.getElementById('scanner-status').classList.remove('visible');
  document.getElementById('btn-live-icon').textContent = '\uD83D\uDCF7';
  document.getElementById('btn-live-text').textContent = 'Iniciar Scanner';
  document.getElementById('btn-live').classList.remove('scanning');
}

function setStatus(msg, ativo) {
  document.getElementById('status-msg').textContent = msg;
  document.getElementById('status-dot').className = 'status-dot' + (ativo ? ' on' : '');
}

/* ── BUSCA via API ───────────────────────────────────────────── */
function buscar(cod) {
  var raw = cod || document.getElementById('inp-m').value.trim();
  var num = raw.replace(/\D/g, '');
  if (!num) return;

  if (scannerAtivo) pararScanner();
  limparRes();
  document.getElementById('loading').classList.add('visible');

  fetch(API_BASE + '/' + num)
    .then(function(response) {
      if (response.status === 404) return { encontrado: false };
      if (!response.ok) throw new Error('Erro HTTP ' + response.status);
      return response.json();
    })
    .then(function(item) {
      document.getElementById('loading').classList.remove('visible');
      var dados = (item && item.encontrado) ? item : null;
      render(dados, num);
      document.getElementById('btn-clear').style.display = 'flex';
      document.getElementById('result-sec').scrollIntoView({ behavior: 'smooth', block: 'start' });
    })
    .catch(function(err) {
      document.getElementById('loading').classList.remove('visible');
      mostrarErro('Não foi possível consultar o sistema. Verifique a conexão e tente novamente.');
    });
}

/* ── UTILITÁRIOS ─────────────────────────────────────────────── */
function limpar() {
  limparRes();
  pararScanner();
  escondeResultados();
  document.getElementById('inp-m').value = '';
  document.getElementById('btn-clear').style.display = 'none';
  codDetectado = null;
}
function limparRes() {
  var s = document.getElementById('result-sec');
  s.innerHTML = '';
  s.classList.remove('visible');
}
function escondeResultados() {
  document.getElementById('err-box').classList.remove('show');
}
function mostrarErro(msg) {
  document.getElementById('err-msg').textContent = msg;
  document.getElementById('err-box').classList.add('show');
}
function flashTela() {
  var f = document.getElementById('flash');
  f.classList.add('on');
  setTimeout(function() { f.classList.remove('on'); }, 200);
}

/* ── RENDER ──────────────────────────────────────────────────── */
function badgeCls(s) {
  if (!s) return 'b-aguard';
  var u = s.toUpperCase();
  if (u === 'ATIVO')                return 'b-ativo';
  if (u.indexOf('TRAMITA') !== -1)  return 'b-tram';
  if (u.indexOf('BAIXAD') !== -1)   return 'b-baixado';
  return 'b-aguard';
}

function render(item, q) {
  var s = document.getElementById('result-sec');
  s.classList.add('visible');
  if (!item) {
    s.innerHTML = '<div class="not-found">'
      + '<div class="nf-ico">&#128269;</div>'
      + '<div class="nf-tit">Patrimônio não encontrado</div>'
      + '<div class="nf-sub">O código <strong>' + q + '</strong> não foi localizado no sistema.<br>Verifique o número e tente novamente.</div>'
      + '</div>';
    return;
  }

  var sf = item.sf || '';
  var sfU = sf.toUpperCase();
  var sfIco = sfU.indexOf('BOM') !== -1 ? '&#9989;'
            : sfU.indexOf('REGULAR') !== -1 ? '&#9888;&#65039;'
            : '&#10060;';
  var sfCls = sfU.indexOf('BOM') !== -1 ? 'i-green'
            : sfU.indexOf('REGULAR') !== -1 ? 'i-amber'
            : 'i-red';

  var obs = '';
  if (item.obs) {
    obs = '<div class="obs-card">'
      + '<div class="obs-emoji">&#9203;</div>'
      + '<div style="flex:1">'
      + '<div class="obs-title">Observação</div>'
      + '<div class="obs-text">' + item.obs.texto + '</div>'
      + '</div></div>';
  }

  s.innerHTML =
    '<div class="res-card">'
    + '<div class="res-header">'
    + '<div><div class="pat-lbl">Nº Patrimônio</div><div class="pat-num">' + item.num + '</div></div>'
    + '<span class="badge ' + badgeCls(item.status) + '">' + (item.status || '') + '</span>'
    + '</div>'
    + '<div class="info-row"><div class="i-icon i-purple">&#128230;</div><div><div class="i-lbl">Descrição do Bem</div><div class="i-val">'        + (item.desc    || '') + '</div></div></div>'
    + '<div class="info-row"><div class="i-icon ' + sfCls + '">' + sfIco + '</div><div><div class="i-lbl">Situação Física</div><div class="i-val">' + sf               + '</div></div></div>'
    + '<div class="info-row"><div class="i-icon i-teal">&#127970;</div><div><div class="i-lbl">Unidade Responsável</div><div class="i-val">'        + (item.unidade || '') + '</div></div></div>'
    + '<div class="info-row"><div class="i-icon i-blue">&#128205;</div><div><div class="i-lbl">Localização / Endereço</div><div class="i-val">'     + (item.end     || '') + '</div></div></div>'
    + '</div>'
    + obs;
}
