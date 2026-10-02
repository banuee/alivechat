/**
 * Cloudflare Worker AI Reverse Proxy для Ping RP
 * 
 * Бесплатный прокси без ограничений по региону (Беларусь / РФ).
 * Поддерживает Groq и Google Gemini.
 * 
 * ИНСТРУКЦИЯ ПО РАЗВЕРТЫВАНИЮ (занимает 2 минуты):
 * 1. Зарегистрируйтесь на https://cloudflare.com (бесплатно, карта не требуется).
 * 2. Перейдите в Workers & Pages -> Create Application -> Create Worker.
 * 3. Нажмите "Deploy", затем "Edit code".
 * 4. Замените весь код на этот файл и нажмите "Deploy".
 * 5. Скопируйте полученный URL воркера (например: https://ping-proxy.yourname.workers.dev).
 * 6. В приложении Ping RP в настройках укажите:
 *    - Groq Base URL: https://ping-proxy.yourname.workers.dev/groq
 *    - Gemini Base URL: https://ping-proxy.yourname.workers.dev/gemini
 */

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

    // Маршрутизация на Groq
    if (url.pathname.startsWith('/groq')) {
      const targetUrl = new URL(url.pathname.replace(/^\/groq/, '') + url.search, 'https://api.groq.com');
      const newHeaders = new Headers(request.headers);
      newHeaders.set('Host', 'api.groq.com');

      const proxyReq = new Request(targetUrl, {
        method: request.method,
        headers: newHeaders,
        body: request.body,
        redirect: 'follow'
      });
      return fetch(proxyReq);
    }

    // Маршрутизация на Google Gemini
    if (url.pathname.startsWith('/gemini')) {
      const targetUrl = new URL(url.pathname.replace(/^\/gemini/, '') + url.search, 'https://generativelanguage.googleapis.com');
      const newHeaders = new Headers(request.headers);
      newHeaders.set('Host', 'generativelanguage.googleapis.com');

      const proxyReq = new Request(targetUrl, {
        method: request.method,
        headers: newHeaders,
        body: request.body,
        redirect: 'follow'
      });
      return fetch(proxyReq);
    }

    // Главная страница / Healthcheck
    return new Response(JSON.stringify({
      status: "online",
      message: "Ping RP Cloudflare Reverse Proxy is active.",
      endpoints: {
        groq: "/groq/openai/v1/chat/completions",
        gemini: "/gemini/v1beta/models/..."
      }
    }), {
      headers: { "Content-Type": "application/json" }
    });
  }
};
