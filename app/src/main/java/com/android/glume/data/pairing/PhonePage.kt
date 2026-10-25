package com.android.glume.data.pairing

/** The small sign-in page the phone opens from the TV's QR code. Self-contained HTML, no scripts. */
object PhonePage {

    fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

    private fun page(body: String) = """
        <!doctype html><html lang="en"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Sign in to Immich TV</title>
        <style>
          :root{color-scheme:dark}
          body{margin:0;background:#0A0C11;color:#F3F5F9;font:16px/1.45 system-ui,-apple-system,sans-serif}
          main{max-width:420px;margin:0 auto;padding:32px 20px}
          .brand{display:flex;align-items:center;gap:12px;font-weight:700;font-size:20px}
          .logo{width:40px;height:40px;border-radius:12px;background:#4553D6;display:grid;place-items:center}
          h1{font-size:26px;margin:28px 0 6px}
          p{color:#AEB5C4;margin:0 0 18px}
          .code{display:inline-block;margin:4px 0 22px;padding:8px 14px;border-radius:10px;background:#161A23;letter-spacing:.14em;font-weight:700;font-size:20px}
          label{display:block;font-size:14px;color:#AEB5C4;margin:14px 0 6px}
          input{box-sizing:border-box;width:100%;padding:14px;border-radius:12px;border:2px solid rgba(255,255,255,.12);background:#1E2330;color:#F3F5F9;font-size:16px}
          input:focus{outline:none;border-color:#7C8CFF}
          button{margin-top:22px;width:100%;padding:15px;border:0;border-radius:999px;background:#4553D6;color:#fff;font-size:17px;font-weight:700}
          .err{background:#3A1F1C;border:2px solid rgba(255,138,122,.5);color:#FFD9D3;padding:12px 14px;border-radius:12px;margin-bottom:8px}
          .note{font-size:13px;color:#858DA0;margin-top:18px}
          .ok{font-size:44px}
        </style></head><body><main>
        <div class="brand"><span class="logo">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM14.3 3.3 8.5 13.5M20.6 9.5H9M17.7 19 12 9M9.7 20.7l5.8-10.2M3.4 14.5H15M6.3 5 12 15"/></svg>
        </span>Immich TV</div>
        $body
        </main></body></html>
    """.trimIndent()

    fun form(code: String, defaultServer: String = "", email: String = "", error: String? = null) = page(
        """
        <h1>Sign in to your TV</h1>
        <p>Check that this code matches the one on your TV:</p>
        <div class="code">${escape(code)}</div>
        ${if (error != null) "<div class=\"err\">${escape(error)}</div>" else ""}
        <form method="post" autocomplete="on">
          <label for="server">Server address</label>
          <input id="server" name="server" type="url" inputmode="url" placeholder="https://photos.example.com" value="${escape(defaultServer)}" required autocapitalize="off" autocorrect="off">
          <label for="email">Email</label>
          <input id="email" name="email" type="email" autocomplete="username" value="${escape(email)}" required autocapitalize="off">
          <label for="password">Password</label>
          <input id="password" name="password" type="password" autocomplete="current-password" required>
          <button type="submit">Sign in on TV</button>
        </form>
        <p class="note">Your password goes straight to your Immich server and is never stored. The TV keeps only a limited, read-only access key (it can also favorite and add to albums), which you can revoke in Immich under Account Settings › API Keys.</p>
        """,
    )

    fun success(name: String) = page(
        """
        <h1><span class="ok">✓</span><br>You're signed in</h1>
        <p>Welcome, ${escape(name)}. You can put your phone away and continue on the TV.</p>
        """,
    )

    fun expired() = page(
        """
        <h1>This link has expired</h1>
        <p>Open the sign-in screen on the TV again and scan the new code.</p>
        """,
    )
}
