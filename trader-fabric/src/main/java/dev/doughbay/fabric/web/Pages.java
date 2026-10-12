package dev.doughbay.fabric.web;

/**
 * The two pages the local panel serves. Everything is in the page itself:
 * no script, font or picture comes from anywhere but this computer.
 */
final class Pages {
    private Pages() {
    }

    private static final String STYLE = """
            :root { --bg:#f4f5f7; --card:#ffffff; --ink:#1b1f24; --dim:#5d6672; --line:#d5d9df;
              --go:#1f8a4c; --stop:#c0392b; --pick:#2f6fd0; --plain:#e6e9ee; --plainink:#1b1f24; }
            @media (prefers-color-scheme: dark) {
              :root { --bg:#14171b; --card:#1d2127; --ink:#e8ebef; --dim:#9aa3ae; --line:#333a44;
                --go:#2aa35d; --stop:#d8503f; --pick:#4c8be8; --plain:#2a3038; --plainink:#e8ebef; }
            }
            * { box-sizing: border-box; }
            body { margin:0; background:var(--bg); color:var(--ink);
              font: 16px/1.4 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; }
            main { max-width: 980px; margin: 0 auto; padding: 12px 16px 32px; }
            h1 { font-size: 18px; margin: 4px 0 10px; display:flex; justify-content:space-between; align-items:center; gap:12px; }
            h1 small { font-weight: 400; color: var(--dim); font-size: 14px; }
            .card { background:var(--card); border:1px solid var(--line); border-radius:10px; padding:10px; }
            .wide { overflow-x:auto; -webkit-overflow-scrolling:touch; }
            #shot { display:block; width:100%; min-width:720px; height:auto; border-radius:6px; }
            #notice { min-height: 22px; margin: 10px 2px 6px; color: var(--dim); font-size: 14px; }
            .row { display:flex; flex-wrap:wrap; gap:8px; margin-top:8px; }
            button, select, input { font: inherit; border-radius:8px; border:1px solid var(--line);
              padding: 10px 14px; min-height: 44px; background:var(--plain); color:var(--plainink); }
            select { flex: 1 1 100%; max-width: 100%; }
            button { cursor:pointer; flex: 1 1 auto; }
            button:disabled { opacity:.45; cursor:default; }
            button.go { background:var(--go); border-color:var(--go); color:#fff; }
            button.stop { background:var(--stop); border-color:var(--stop); color:#fff; }
            button.pick { background:var(--pick); border-color:var(--pick); color:#fff; }
            button:focus-visible, select:focus-visible, input:focus-visible { outline: 3px solid var(--pick); outline-offset: 2px; }
            form.out button { flex:none; min-height:36px; padding:6px 12px; font-size:14px; }
            .door { max-width: 340px; margin: 12vh auto 0; }
            .door input { width:100%; font-size: 24px; letter-spacing: .2em; text-align:center; }
            .door button { width:100%; margin-top:10px; }
            .door p { color: var(--dim); font-size: 14px; }
            .bad { color: var(--stop); }
            """;

    static String login(String message) {
        String note = message == null || message.isBlank() ? ""
                : "<p class=\"bad\" role=\"alert\">" + escape(message) + "</p>";
        return "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>GoNuts panel</title><style>" + STYLE + "</style></head><body><main><div class=\"door card\">"
                + "<h1>GoNuts panel</h1>" + note
                + "<form method=\"post\" action=\"/login\">"
                + "<label for=\"code\">PIN</label>"
                + "<input id=\"code\" name=\"code\" type=\"password\" inputmode=\"numeric\" autocomplete=\"off\" "
                + "maxlength=\"12\" autofocus required>"
                + "<button class=\"pick\" type=\"submit\">Open the panel</button></form>"
                + "<p>The PIN is the one set on the GoNuts screen in game, under Automation, Web panel.</p>"
                + "</div></main></body></html>";
    }

    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** The panel: the page's picture, the last reply, and its controls drawn from what the mod says they are. */
    static final String APP = "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
            + "<title>GoNuts panel</title><style>" + STYLE + "</style></head><body><main>"
            + "<h1><span>GoNuts <small id=\"page\"></small></span>"
            + "<form class=\"out\" method=\"post\" action=\"/logout\"><button type=\"submit\">Lock</button></form></h1>"
            + "<div class=\"card wide\" tabindex=\"0\" aria-label=\"The page on view; scroll sideways to see all of it\"><img id=\"shot\" alt=\"The page of the panel on view\" src=\"/panel.png\"></div>"
            + "<div id=\"notice\" role=\"status\"></div>"
            + "<div id=\"controls\"></div>"
            + "</main><script>" + """
            const shot = document.getElementById('shot');
            const controls = document.getElementById('controls');
            let last = '';
            let setting = null;
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
            function draw(state) {
              document.getElementById('page').textContent = state.page || '';
              document.getElementById('notice').textContent = state.notice || '';
              setting = state.setting || null;
              const shape = JSON.stringify(state.components);
              if (shape === last || controls.contains(document.activeElement) && document.activeElement.tagName === 'SELECT') return;
              last = shape;
              controls.replaceChildren();
              for (const row of state.components || []) {
                const div = document.createElement('div');
                div.className = 'row';
                for (const c of row.components || []) {
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
                document.getElementById('notice').textContent = 'The game is not answering. Is it still running?';
              }
            }
            refresh();
            setInterval(refresh, 5000);
            """ + "</script></body></html>";
}
