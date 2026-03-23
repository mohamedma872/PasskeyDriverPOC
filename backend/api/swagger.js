/**
 * GET /api/swagger
 * Serves the Swagger UI HTML page.
 * Opens in browser → interactive API docs loaded from /api/docs.
 */
export default function handler(req, res) {
  if (req.method !== "GET") return res.status(405).end();

  res.setHeader("Content-Type", "text/html");
  res.send(`<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>WasteHero Fleet API</title>
  <link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5.17.14/swagger-ui.css" />
  <style>
    /* ── Base ── */
    *, *::before, *::after { box-sizing: border-box; }
    body { margin: 0; background: #0f1624; font-family: 'Inter', system-ui, sans-serif; }

    /* ── Top bar ── */
    .swagger-ui .topbar {
      background: linear-gradient(135deg, #0f1624 0%, #1a2535 100%);
      border-bottom: 1px solid #252f42;
      padding: 12px 24px;
    }
    .swagger-ui .topbar .download-url-wrapper { display: none; }
    .swagger-ui .topbar-wrapper .link { display: flex; align-items: center; gap: 10px; }
    .swagger-ui .topbar-wrapper .link::before {
      content: "🚛  WasteHero Fleet API";
      color: #00c9d7;
      font-size: 17px;
      font-weight: 700;
      letter-spacing: 0.5px;
    }
    .swagger-ui .topbar-wrapper img { display: none; }

    /* ── Main wrapper ── */
    .swagger-ui { background: #0f1624; color: #c9d4e8; }
    .swagger-ui .wrapper { padding: 0 24px; max-width: 1100px; }

    /* ── Info block ── */
    .swagger-ui .info { margin: 32px 0 24px; }
    .swagger-ui .info .title {
      color: #00c9d7;
      font-size: 28px;
      font-weight: 800;
      letter-spacing: -0.5px;
    }
    .swagger-ui .info .title small.version-stamp {
      background: #00c9d7;
      color: #0f1624;
      font-size: 11px;
      font-weight: 700;
      padding: 3px 8px;
      border-radius: 20px;
      vertical-align: middle;
      margin-left: 10px;
    }
    .swagger-ui .info p,
    .swagger-ui .info li { color: #8a96aa; font-size: 14px; line-height: 1.6; }
    .swagger-ui .info a { color: #00c9d7; }

    /* ── Servers block ── */
    .swagger-ui .scheme-container {
      background: #1a2535;
      border: 1px solid #252f42;
      border-radius: 10px;
      padding: 16px 20px;
      margin-bottom: 24px;
      box-shadow: none;
    }
    .swagger-ui .schemes > label, .swagger-ui .servers > label {
      color: #8a96aa;
      font-size: 12px;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 1px;
    }
    .swagger-ui .servers select,
    .swagger-ui select {
      background: #252f42;
      border: 1px solid #2d3748;
      color: #c9d4e8;
      border-radius: 6px;
      padding: 6px 10px;
    }

    /* ── Authorize button ── */
    .swagger-ui .btn.authorize {
      background: transparent;
      border: 1.5px solid #00c9d7;
      color: #00c9d7;
      border-radius: 8px;
      font-weight: 600;
      font-size: 13px;
      padding: 7px 18px;
      transition: all 0.2s;
    }
    .swagger-ui .btn.authorize:hover { background: #00c9d7; color: #0f1624; }
    .swagger-ui .btn.authorize svg { fill: #00c9d7; }
    .swagger-ui .btn.authorize:hover svg { fill: #0f1624; }

    /* ── Tags (section headers) ── */
    .swagger-ui .opblock-tag {
      color: #c9d4e8;
      border-bottom: 1px solid #252f42;
      font-size: 17px;
      font-weight: 700;
      padding: 12px 0;
    }
    .swagger-ui .opblock-tag:hover { background: transparent; }
    .swagger-ui .opblock-tag small { color: #8a96aa; font-size: 13px; font-weight: 400; }

    /* ── Operation blocks ── */
    .swagger-ui .opblock {
      background: #1a2535;
      border: 1px solid #252f42;
      border-radius: 10px;
      margin-bottom: 10px;
      box-shadow: none;
    }
    .swagger-ui .opblock .opblock-summary {
      border-radius: 10px;
      padding: 12px 16px;
    }
    .swagger-ui .opblock .opblock-summary-description {
      color: #c9d4e8;
      font-size: 14px;
      font-weight: 500;
    }
    .swagger-ui .opblock .opblock-summary-path {
      color: #e2e8f4;
      font-size: 14px;
      font-family: 'JetBrains Mono', 'Fira Code', monospace;
    }
    .swagger-ui .opblock .opblock-summary-path__deprecated { color: #8a96aa; }

    /* POST */
    .swagger-ui .opblock.opblock-post {
      border-color: #00c9d7;
      background: #111d2e;
    }
    .swagger-ui .opblock.opblock-post .opblock-summary { background: rgba(0,201,215,0.06); }
    .swagger-ui .opblock.opblock-post .opblock-summary-method {
      background: #00c9d7;
      color: #0f1624;
      font-weight: 800;
      border-radius: 6px;
      min-width: 60px;
      text-align: center;
    }

    /* GET */
    .swagger-ui .opblock.opblock-get { border-color: #4299e1; background: #111d2e; }
    .swagger-ui .opblock.opblock-get .opblock-summary { background: rgba(66,153,225,0.06); }
    .swagger-ui .opblock.opblock-get .opblock-summary-method {
      background: #4299e1; color: #0f1624; font-weight: 800; border-radius: 6px;
    }

    /* ── Expanded section body ── */
    .swagger-ui .opblock-body { background: #0f1624; border-top: 1px solid #252f42; }
    .swagger-ui .opblock-description-wrapper p,
    .swagger-ui .opblock-external-docs-wrapper p,
    .swagger-ui .opblock-title_normal p { color: #8a96aa; font-size: 13px; }
    .swagger-ui .tab li { color: #8a96aa; }
    .swagger-ui .tab li.active { color: #00c9d7; border-bottom: 2px solid #00c9d7; }

    /* ── Parameters / body ── */
    .swagger-ui table thead tr th { color: #8a96aa; border-bottom: 1px solid #252f42; font-size: 12px; }
    .swagger-ui table tbody tr td { border-bottom: 1px solid #1a2535; color: #c9d4e8; }
    .swagger-ui .parameter__name { color: #c9d4e8; font-weight: 600; }
    .swagger-ui .parameter__type { color: #00c9d7; font-size: 12px; }
    .swagger-ui .parameter__in { color: #8a96aa; font-size: 11px; }

    /* ── Code / schema boxes ── */
    .swagger-ui .model-box, .swagger-ui section.models { background: #1a2535; border: 1px solid #252f42; border-radius: 8px; }
    .swagger-ui .model { color: #c9d4e8; }
    .swagger-ui .prop-type { color: #00c9d7; }
    .swagger-ui .prop-format { color: #8a96aa; }
    .swagger-ui pre.microlight, .swagger-ui .highlight-code pre {
      background: #0a1120 !important;
      border-radius: 8px;
      border: 1px solid #252f42;
      color: #a0f0f7;
      font-family: 'JetBrains Mono', 'Fira Code', monospace;
      font-size: 13px;
    }

    /* ── Inputs & textareas ── */
    .swagger-ui input[type=text], .swagger-ui textarea {
      background: #0f1624;
      border: 1px solid #252f42;
      color: #c9d4e8;
      border-radius: 6px;
      padding: 8px 12px;
      font-size: 13px;
    }
    .swagger-ui input[type=text]:focus, .swagger-ui textarea:focus {
      border-color: #00c9d7;
      outline: none;
      box-shadow: 0 0 0 2px rgba(0,201,215,0.15);
    }

    /* ── Buttons ── */
    .swagger-ui .btn {
      border-radius: 7px;
      font-weight: 600;
      font-size: 13px;
      transition: all 0.2s;
    }
    .swagger-ui .btn.execute {
      background: #00c9d7;
      border: none;
      color: #0f1624;
      padding: 8px 20px;
    }
    .swagger-ui .btn.execute:hover { background: #00aab6; }
    .swagger-ui .btn.cancel {
      background: transparent;
      border: 1px solid #4a5568;
      color: #8a96aa;
    }
    .swagger-ui .btn.cancel:hover { border-color: #8a96aa; color: #c9d4e8; }
    .swagger-ui .btn.try-out__btn {
      background: transparent;
      border: 1px solid #252f42;
      color: #8a96aa;
    }
    .swagger-ui .btn.try-out__btn:hover { border-color: #00c9d7; color: #00c9d7; }
    .swagger-ui .btn.try-out__btn.cancel { border-color: #ff6b6b; color: #ff6b6b; }

    /* ── Response codes ── */
    .swagger-ui .response-col_status { color: #c9d4e8; font-weight: 700; }
    .swagger-ui table.responses-table td { border-bottom: 1px solid #1a2535; }
    .swagger-ui .response-col_description { color: #8a96aa; }
    .swagger-ui .opblock-body .opblock-section-header {
      background: #141e30;
      border-bottom: 1px solid #252f42;
    }
    .swagger-ui .opblock-body .opblock-section-header label { color: #8a96aa; font-size: 12px; font-weight: 700; text-transform: uppercase; letter-spacing: 1px; }

    /* ── Models section ── */
    .swagger-ui section.models { margin-top: 32px; }
    .swagger-ui section.models h4 { color: #c9d4e8; font-weight: 700; }
    .swagger-ui .model-title { color: #00c9d7; }
    .swagger-ui .model-toggle { color: #8a96aa; }

    /* ── Auth modal ── */
    .swagger-ui .dialog-ux .modal-ux {
      background: #1a2535;
      border: 1px solid #252f42;
      border-radius: 14px;
      color: #c9d4e8;
    }
    .swagger-ui .dialog-ux .modal-ux-header {
      background: #141e30;
      border-bottom: 1px solid #252f42;
      border-radius: 14px 14px 0 0;
    }
    .swagger-ui .dialog-ux .modal-ux-header h3 { color: #c9d4e8; }
    .swagger-ui .dialog-ux .modal-ux-content p { color: #8a96aa; }
    .swagger-ui .dialog-ux .modal-ux-content label { color: #c9d4e8; font-weight: 600; }
    .swagger-ui .auth-btn-wrapper .btn-done { background: #00c9d7; color: #0f1624; border: none; }
    .swagger-ui .auth-btn-wrapper .btn-done:hover { background: #00aab6; }

    /* ── Scrollbar ── */
    ::-webkit-scrollbar { width: 6px; height: 6px; }
    ::-webkit-scrollbar-track { background: #0f1624; }
    ::-webkit-scrollbar-thumb { background: #252f42; border-radius: 3px; }
    ::-webkit-scrollbar-thumb:hover { background: #2d3748; }
  </style>
</head>
<body>
  <div id="swagger-ui"></div>
  <script src="https://unpkg.com/swagger-ui-dist@5.17.14/swagger-ui-bundle.js"></script>
  <script>
    SwaggerUIBundle({
      url: "/api/docs",
      dom_id: "#swagger-ui",
      presets: [SwaggerUIBundle.presets.apis, SwaggerUIBundle.SwaggerUIStandalonePreset],
      layout: "BaseLayout",
      deepLinking: true,
      persistAuthorization: true,
      defaultModelsExpandDepth: 1,
      defaultModelExpandDepth: 1,
    });
  </script>
</body>
</html>`);
}
