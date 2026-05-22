/* ─────────────────────────────────────────────────────────────────
   DETRAN-DF — Consulta de Patrimônio
   Scanner: html5-qrcode (robusto, funciona via CDN sem configuração)
───────────────────────────────────────────────────────────────── */

const DB = {
  "040608":  {num:"040608",  desc:"Monitor LCD 19pol Samsung",      sf:"Bom Estado",    status:"Ativo",                  unidade:"Coordenadoria de TI",         end:"Bloco B, Sala 112 - Ed. Sede", obs:null},
  "0012345": {num:"0012345", desc:"Computador Dell Optiplex 7090",  sf:"Bom Estado",    status:"Ativo",                  unidade:"Secretaria de Administracao", end:"Bloco A, Sala 204 - Ed. Sede", obs:null},
  "0067890": {num:"0067890", desc:"Mesa de Reuniao 10 Lugares",     sf:"Estado Regular",status:"Em Tramitacao",          unidade:"Secretaria de Planejamento",  end:"Bloco C, Sala 310 - Ed. Anexo I",
    obs:{tipo:"tramitacao",texto:"Bem em processo de tramitacao para nova unidade.",destino:"Secretaria de Educacao",solicitante:"Joao Oliveira",data:"02/05/2026",protocolo:"PROT-2026/00342"}},
  "0034567": {num:"0034567", desc:"Ar-condicionado Split 12000 BTU",sf:"Mau Estado",    status:"Aguardando Autorizacao", unidade:"Coordenadoria de TI",         end:"Bloco B, Sala 112 - Ed. Sede",
    obs:{tipo:"aguardando",texto:"Aguardando autorizacao para descarte por obsolescencia.",destino:"Almoxarifado Central (Descarte)",solicitante:"Maria Santos",data:"15/04/2026",protocolo:"PROT-2026/00215"}},
  "0099001": {num:"0099001", desc:"Cadeira Presidente Ergonomica",  sf:"Bom Estado",    status:"Ativo",                  unidade:"Gabinete do Secretario",      end:"Bloco A, Sala 001 - Ed. Sede", obs:null},
  "0011111": {num:"0011111", desc:"Projetor Epson PowerLite X41",   sf:"Bom Estado",    status:"Baixado",                unidade:"Sala de Treinamento",         end:"Bloco D, Sala 50 - Ed. Anexo II",
    obs:{tipo:"baixado",texto:"Bem baixado por doacao ao Hospital Municipal.",destino:"Hospital Municipal Sao Jose",solicitante:"Carlos Ferreira",data:"10/03/2026",protocolo:"PROT-2026/00098"}}
};

/* ── estado global ───────────────────────────────────────────── */
var scannerAtivo = false;
var codDetectado = null;
var html5Scanner = null;

/* ── DEBUG ───────────────────────────────────────────────────── */
function debug(msg) {
  var el = document.getElementById('debug-box');
  if (!el) return;
  el.style.display = 'block';
  el.textContent = msg;
}

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
      // DEBUG: mostra exatamente o que foi lido antes de qualquer processamento
      debug('RAW lido: [' + cod + ']  |  scannerAtivo: ' + scannerAtivo);
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
    var msg = '';
    var s = String(err).toLowerCase();
    if (s.indexOf('permission') !== -1 || s.indexOf('notallowed') !== -1) {
      msg = 'Permissão de câmera negada. Toque no ícone de cadeado na barra do navegador e permita o acesso à câmera.';
    } else if (s.indexOf('notfound') !== -1 || s.indexOf('devicenotfound') !== -1) {
      msg = 'Nenhuma câmera encontrada neste dispositivo.';
    } else if (s.indexOf('notreadable') !== -1 || s.indexOf('inuse') !== -1) {
      msg = 'A câmera está sendo usada por outro aplicativo. Feche-o e tente novamente.';
    } else {
      msg = 'Não foi possível acessar a câmera. Verifique se a página está em HTTPS e se a permissão foi concedida. Detalhe: ' + err;
    }
    mostrarErro(msg);
  });
}

function pararScanner() {
  if (html5Scanner) {
    html5Scanner.stop().then(function() {
      html5Scanner.clear();
      html5Scanner = null;
    }).catch(function() {
      html5Scanner = null;
    });
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

/* ── BUSCA ───────────────────────────────────────────────────── */
function buscar(cod) {
  var raw = cod || document.getElementById('inp-m').value.trim();
  var num = raw.replace(/\D/g, '');
  debug('buscar() chamado | raw: [' + raw + '] | num: [' + num + ']');
  if (!num) return;
  pararScanner();
  limparRes();
  document.getElementById('loading').classList.add('visible');
  setTimeout(function() {
    document.getElementById('loading').classList.remove('visible');
    var item = DB[num] || DB[num.padStart(6,'0')] || DB[num.padStart(7,'0')];
    debug('buscar() resultado | num: [' + num + '] | encontrou: ' + (item ? item.desc : 'NÃO ENCONTRADO'));
    render(item, num);
    document.getElementById('btn-clear').style.display = 'flex';
    document.getElementById('result-sec').scrollIntoView({ behavior: 'smooth', block: 'start' });
  }, 500);
}

/* ── UTILITÁRIOS ─────────────────────────────────────────────── */
function limpar() {
  limparRes();
  pararScanner();
  escondeResultados();
  document.getElementById('inp-m').value = '';
  document.getElementById('btn-clear').style.display = 'none';
  document.getElementById('debug-box').style.display = 'none';
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
  if (s === 'Ativo')               return 'b-ativo';
  if (s === 'Em Tramitacao')       return 'b-tram';
  if (s === 'Baixado')             return 'b-baixado';
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
  var sfIco = item.sf === 'Bom Estado' ? '&#9989;' : item.sf === 'Estado Regular' ? '&#9888;&#65039;' : '&#10060;';
  var sfCls = item.sf === 'Bom Estado' ? 'i-green' : item.sf === 'Estado Regular' ? 'i-amber' : 'i-red';
  var obs = '';
  if (item.obs) {
    var o  = item.obs;
    var oi = o.tipo === 'tramitacao' ? '&#128260;' : o.tipo === 'aguardando' ? '&#9203;' : '&#128228;';
    obs = '<div class="obs-card">'
      + '<div class="obs-emoji">' + oi + '</div>'
      + '<div style="flex:1">'
      + '<div class="obs-title">Observação</div>'
      + '<div class="obs-text">'  + o.texto + '</div>'
      + '<div class="obs-grid">'
      + '<div><div class="og-lbl">Destino</div><div class="og-val">'     + o.destino     + '</div></div>'
      + '<div><div class="og-lbl">Solicitante</div><div class="og-val">' + o.solicitante + '</div></div>'
      + '<div><div class="og-lbl">Data</div><div class="og-val">'        + o.data        + '</div></div>'
      + '<div><div class="og-lbl">Protocolo</div><div class="og-val">'   + o.protocolo   + '</div></div>'
      + '</div></div></div>';
  }
  s.innerHTML =
    '<div class="res-card">'
    + '<div class="res-header">'
    + '<div><div class="pat-lbl">Nº Patrimônio</div><div class="pat-num">' + item.num + '</div></div>'
    + '<span class="badge ' + badgeCls(item.status) + '">' + item.status + '</span>'
    + '</div>'
    + '<div class="info-row"><div class="i-icon i-purple">&#128230;</div><div><div class="i-lbl">Descrição do Bem</div><div class="i-val">'        + item.desc    + '</div></div></div>'
    + '<div class="info-row"><div class="i-icon ' + sfCls + '">' + sfIco + '</div><div><div class="i-lbl">Situação Física</div><div class="i-val">' + item.sf      + '</div></div></div>'
    + '<div class="info-row"><div class="i-icon i-teal">&#127970;</div><div><div class="i-lbl">Unidade Responsável</div><div class="i-val">'        + item.unidade + '</div></div></div>'
    + '<div class="info-row"><div class="i-icon i-blue">&#128205;</div><div><div class="i-lbl">Localização / Endereço</div><div class="i-val">'     + item.end     + '</div></div></div>'
    + '</div>'
    + obs;
}
