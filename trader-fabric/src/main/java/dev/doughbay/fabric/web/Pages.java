package dev.doughbay.fabric.web;

/**
 * The two pages the local panel serves, drawn in the look of the screen the
 * mod has in game: the same dark panels, edges, gold titles and button
 * states, taken from UiTheme. Everything is in the page itself; no script,
 * font or picture comes from anywhere but this computer.
 */
final class Pages {
    private Pages() {
    }

    /**
     * UiTheme's colours, by its names. The game's screen is dark and nothing
     * else, so this page is too, whatever the browser prefers.
     */
    private static final String STYLE = """
            :root { --background:#0c121b; --surface:#182230; --inset:#101823; --edge:#2c3b4c; --hover:#243747;
              --selected:#354034; --text:#f0eee7; --secondary:#b6c2cd; --muted:#8799aa; --gold:#f4cd7a;
              --teal:#56dccb; --good:#77deb0; --warn:#f3b576; --bad:#ff858c; color-scheme: dark; }
            * { box-sizing: border-box; }
            body { margin:0; background:var(--background); color:var(--text);
              font: 15px/1.45 "Minecraft", "Monocraft", ui-monospace, "Cascadia Mono", Consolas, "DejaVu Sans Mono", monospace; }
            main { max-width: 1180px; margin: 0 auto; padding: 12px 16px 32px; }
            .panel { background:var(--surface); border:1px solid var(--edge); border-radius:7px;
              box-shadow: 2px 3px 0 rgba(0,0,0,.31); }
            .label { color:var(--muted); font-size:12px; letter-spacing:.08em; text-transform:uppercase; }
            .title { color:var(--gold); font-size:13px; letter-spacing:.08em; text-transform:uppercase;
              padding:8px 10px 6px; border-bottom:1px solid var(--edge); margin:0; font-weight:400; }
            header { display:flex; align-items:center; gap:12px; padding:8px 12px; flex-wrap:wrap; }
            header img { height:44px; width:auto; image-rendering:pixelated; }
            #state { background:var(--inset); border:1px solid var(--edge); border-radius:4px; padding:3px 10px;
              color:var(--gold); letter-spacing:.08em; text-transform:uppercase; font-size:13px; }
            #state.good { color:var(--good); } #state.bad { color:var(--bad); }
            header .gap { flex:1 1 auto; }
            #clock { color:var(--secondary); font-variant-numeric: tabular-nums; }
            .hud { display:grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap:8px; margin-top:10px; }
            .tile { background:var(--inset); border:1px solid var(--edge); border-radius:4px; padding:7px 10px; min-width:0; }
            .tile b { display:block; font-weight:400; font-size:17px; color:var(--text); font-variant-numeric: tabular-nums;
              overflow-wrap:anywhere; }
            .now { margin-top:10px; padding:8px 10px 10px; }
            .now .label { color:var(--warn); }
            #now { color:var(--text); overflow-wrap:anywhere; }
            #capacity { display:grid; grid-template-columns: repeat(auto-fill, 12px); gap:3px; margin-top:8px; }
            #capacity i { width:12px; height:12px; background:var(--inset); border:1px solid var(--edge); }
            #capacity i.used { background:var(--warn); border-color:var(--warn); }
            .work { display:grid; grid-template-columns: minmax(0, 1fr); gap:10px; margin-top:10px; }
            nav { display:flex; gap:6px; overflow-x:auto; padding:8px; -webkit-overflow-scrolling:touch; }
            @media (min-width: 860px) {
              .work { grid-template-columns: 190px minmax(0, 1fr); align-items:start; }
              nav { flex-direction:column; overflow:visible; }
            }
            button, select, input { font: inherit; color:var(--text); background:var(--surface);
              border:1px solid var(--edge); border-radius:4px; padding:9px 12px; min-height:42px; }
            button { cursor:pointer; text-align:center; }
            button:hover:not(:disabled) { background:var(--hover); }
            button:disabled { background:var(--inset); color:var(--muted); cursor:default; }
            button:focus-visible, select:focus-visible, input:focus-visible, .wide:focus-visible {
              outline:none; border-color:var(--gold); box-shadow:0 0 0 1px var(--gold); }
            nav button { text-align:left; white-space:nowrap; flex:0 0 auto; border-left-width:3px; }
            nav button[aria-current="page"] { background:var(--selected); color:var(--gold); border-left-color:var(--gold); }
            button.go { color:var(--good); border-color:var(--good); }
            button.stop { color:var(--bad); border-color:var(--bad); }
            button.pick { color:var(--gold); }
            button.go:disabled, button.stop:disabled { border-color:var(--edge); color:var(--muted); }
            .body { padding:10px; }
            .wide { overflow-x:auto; -webkit-overflow-scrolling:touch; background:var(--inset);
              border:1px solid var(--edge); border-radius:4px; padding:6px; }
            #shot { display:block; width:100%; min-width:720px; height:auto; }
            #notice { min-height:22px; margin:8px 2px 2px; color:var(--secondary); overflow-wrap:anywhere; }
            .row { display:flex; flex-wrap:wrap; gap:6px; margin-top:8px; }
            .row button { flex:1 1 auto; }
            .row select { flex:1 1 100%; max-width:100%; }
            form.out button { min-height:34px; padding:5px 12px; }
            .door { max-width:360px; margin:10vh auto 0; }
            .door .body { padding:14px; }
            .door img { display:block; width:210px; max-width:70%; height:auto; margin:4px auto 14px; image-rendering:pixelated; }
            .door input { width:100%; font-size:24px; letter-spacing:.25em; text-align:center; background:var(--inset); }
            .door button { width:100%; margin-top:10px; }
            .door p { color:var(--secondary); font-size:13px; }
            .door .bad { color:var(--bad); }
            """;

    private static final String HEAD = "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
            + "<title>GoNuts panel</title><style>" + STYLE + "</style></head><body><main>";

    static String login(String message) {
        String note = message == null || message.isBlank() ? ""
                : "<p class=\"bad\" role=\"alert\">" + escape(message) + "</p>";
        return HEAD + "<div class=\"door panel\"><h1 class=\"title\">Web panel</h1><div class=\"body\">"
                + "<img src=\"/logo.png\" alt=\"GoNuts\">" + note
                + "<form method=\"post\" action=\"/login\">"
                + "<label class=\"label\" for=\"code\">PIN</label>"
                + "<input id=\"code\" name=\"code\" type=\"password\" inputmode=\"numeric\" autocomplete=\"off\" "
                + "maxlength=\"12\" autofocus required>"
                + "<button class=\"pick\" type=\"submit\">Open the panel</button></form>"
                + "<p>The PIN is the one set on the GoNuts screen in game, under Autopilot, Web panel.</p>"
                + "</div></div></main></body></html>";
    }

    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /**
     * The panel. The header and the tiles under it are the HUD's; the pages
     * run down the side as they do on the screen in game; the page on view is
     * the picture the mod draws, with its controls underneath.
     */
    static final String APP = HEAD
            + "<header class=\"panel\"><img src=\"/logo.png\" alt=\"GoNuts\">"
            + "<span id=\"state\">...</span><span class=\"gap\"></span><span id=\"clock\"></span>"
            + "<form class=\"out\" method=\"post\" action=\"/logout\"><button type=\"submit\">Lock</button></form></header>"
            + "<section class=\"hud\" aria-label=\"The trader now\">"
            + "<div class=\"tile\"><span class=\"label\">Auction slots</span><b id=\"slots\">-</b></div>"
            + "<div class=\"tile\"><span class=\"label\">Bank</span><b id=\"bank\">-</b></div>"
            + "<div class=\"tile\"><span class=\"label\">Trades</span><b id=\"trades\">-</b></div>"
            + "<div class=\"tile\"><span class=\"label\">Account</span><b id=\"who\">-</b></div></section>"
            + "<section class=\"panel now\"><span class=\"label\">Now</span><div id=\"now\" role=\"status\"></div>"
            + "<div id=\"capacity\" aria-hidden=\"true\"></div></section>"
            + "<div class=\"work\"><nav class=\"panel\" id=\"pages\" aria-label=\"Pages\"></nav>"
            + "<section class=\"panel\"><h1 class=\"title\" id=\"page\">&nbsp;</h1><div class=\"body\">"
            + "<div class=\"wide\" tabindex=\"0\" aria-label=\"The page on view; scroll sideways to see all of it\">"
            + "<img id=\"shot\" alt=\"The page of the panel on view\" src=\"/panel.png\"></div>"
            + "<div id=\"notice\" role=\"status\"></div>"
            + "<div id=\"controls\"></div></div></section></div>"
            + "</main><script>" + """
            const shot = document.getElementById('shot');
            const controls = document.getElementById('controls');
            const pages = document.getElementById('pages');
            let last = '';
            let lastPages = '';
            let lastCapacity = '';
            let setting = null;
            function put(id, text) { document.getElementById(id).textContent = text; }
            function picture() { shot.src = '/panel.png?t=' + Date.now(); }
            async function act(id, value, text) {
              const body = new URLSearchParams();
              body.set('id', id);
              if (value !== undefined && value !== null) body.set('value', value);
              if (text !== undefined && text !== null) body.set('text', text);
              await fetch('/act', { method: 'POST', headers: { 'X-GoNuts': '1',
                'Content-Type': 'application/x-www-form-urlencoded' }, body });
              setTimeout(refresh, 350);
              setTimeout(refresh, 1500);
            }
            function press(id) {
              if (id === 'ctl:search') {
                const item = prompt('Item to search for, for example golden apple');
                if (item) act(id, null, item);
                return;
              }
              if (id === 'tune:set') {
                const text = prompt(setting ? setting.label + '\\n' + setting.description : 'New value',
                  setting ? setting.value : '');
                if (text !== null && text !== '') act(id, null, text);
                return;
              }
              act(id, '');
            }
            function hud(h) {
              if (!h) return;
              const state = document.getElementById('state');
              state.textContent = h.state || '...';
              const s = (h.state || '').toUpperCase();
              state.className = s === 'STOPPED' ? 'bad' : s === 'PAUSED' ? '' : 'good';
              put('slots', h.listed + ' / ' + h.slots);
              put('bank', h.bank || '-');
              put('trades', String(h.trades));
              put('who', h.who || '-');
              put('now', h.now || '');
              put('clock', h.clock || '');
              const shape = h.listed + '/' + h.slots;
              if (shape === lastCapacity) return;
              lastCapacity = shape;
              const grid = document.getElementById('capacity');
              grid.replaceChildren();
              for (let i = 0; i < Math.min(180, h.slots); i++) {
                const cell = document.createElement('i');
                if (i < h.listed) cell.className = 'used';
                grid.appendChild(cell);
              }
            }
            function tabs(pick) {
              const shape = JSON.stringify(pick ? pick.options : []);
              if (shape === lastPages) return;
              lastPages = shape;
              pages.replaceChildren();
              for (const o of (pick ? pick.options : []) || []) {
                const b = document.createElement('button');
                b.type = 'button';
                b.textContent = o.label;
                if (o.default) b.setAttribute('aria-current', 'page');
                b.addEventListener('click', () => act('page:pick', o.value));
                pages.appendChild(b);
              }
            }
            function draw(state) {
              put('page', state.page || '');
              put('notice', state.notice || '');
              hud(state.hud);
              setting = state.setting || null;
              let pick = null;
              const rows = [];
              for (const row of state.components || []) {
                const rest = [];
                for (const c of row.components || []) {
                  if (c.type === 3 && c.custom_id === 'page:pick') pick = c; else rest.push(c);
                }
                if (rest.length) rows.push(rest);
              }
              tabs(pick);
              const shape = JSON.stringify(rows);
              if (shape === last || controls.contains(document.activeElement) && document.activeElement.tagName === 'SELECT') return;
              last = shape;
              controls.replaceChildren();
              for (const row of rows) {
                const div = document.createElement('div');
                div.className = 'row';
                for (const c of row) {
                  if (c.type === 2) {
                    const b = document.createElement('button');
                    b.type = 'button';
                    b.textContent = c.label;
                    b.className = c.style === 3 ? 'go' : c.style === 4 ? 'stop' : c.style === 1 ? 'pick' : '';
                    b.disabled = !!c.disabled;
                    b.addEventListener('click', () => press(c.custom_id));
                    div.appendChild(b);
                  } else if (c.type === 3) {
                    const s = document.createElement('select');
                    s.setAttribute('aria-label', c.placeholder || 'Choose');
                    const first = document.createElement('option');
                    first.textContent = c.placeholder || 'Choose';
                    first.value = '';
                    first.disabled = true;
                    first.selected = true;
                    s.appendChild(first);
                    for (const o of c.options || []) {
                      const opt = document.createElement('option');
                      opt.textContent = o.label;
                      opt.value = o.value;
                      s.appendChild(opt);
                    }
                    s.addEventListener('change', () => { const v = s.value; s.blur(); act(c.custom_id, v); });
                    div.appendChild(s);
                  }
                }
                controls.appendChild(div);
              }
            }
            async function refresh() {
              try {
                const r = await fetch('/state', { cache: 'no-store' });
                if (r.status === 401) { location.reload(); return; }
                draw(await r.json());
                picture();
              } catch (e) {
                put('notice', 'The game is not answering. Is it still running?');
              }
            }
            refresh();
            setInterval(refresh, 5000);
            """ + "</script></body></html>";
}
